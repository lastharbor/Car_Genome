package com.cargenome.app.data.repository

import androidx.room.withTransaction
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.db.dao.MaintenanceScheduleDao
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.dao.ServiceRecordDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.domain.service.MaintenanceAlarmScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Work done on the car, the scheduled calendar events, and the recurring plan.
 *
 * Booking a job against a plan item is what resets that item's interval, so
 * all are handled here rather than in separate repositories that would have to
 * agree with each other.
 */
@Singleton
class ServiceRepository @Inject constructor(
    private val database: CarGenomeDatabase,
    private val serviceDao: ServiceRecordDao,
    private val scheduleDao: MaintenanceScheduleDao,
    private val odometerDao: OdometerReadingDao,
    private val attachments: AttachmentRepository,
    private val eventDao: MaintenanceEventDao = database.maintenanceEventDao(),
    private val vehicleDao: VehicleDao = database.vehicleDao(),
    private val alarmScheduler: MaintenanceAlarmScheduler? = null,
) {
    fun observeRecords(vehicleId: Long): Flow<List<ServiceRecordEntity>> =
        serviceDao.observeForVehicle(vehicleId)

    fun observeSchedules(vehicleId: Long): Flow<List<MaintenanceScheduleEntity>> =
        scheduleDao.observeForVehicle(vehicleId)

    fun observeSpendBetween(vehicleId: Long, fromEpochMilli: Long, toEpochMilli: Long): Flow<Long> =
        serviceDao.observeSpendBetween(vehicleId, fromEpochMilli, toEpochMilli)

    suspend fun findRecord(id: Long): ServiceRecordEntity? = serviceDao.findById(id)

    suspend fun findSchedule(id: Long): MaintenanceScheduleEntity? = scheduleDao.findById(id)

    suspend fun listEnabledSchedules(): List<MaintenanceScheduleEntity> = scheduleDao.listEnabled()

    suspend fun addRecord(record: ServiceRecordEntity): Long = database.withTransaction {
        val id = serviceDao.insert(record)
        record.odometerKm?.let { km ->
            odometerDao.insert(
                OdometerReadingEntity(
                    vehicleId = record.vehicleId,
                    recordedAt = record.performedAt,
                    odometerKm = km,
                    source = OdometerSource.ServiceRecord,
                    sourceRecordId = id,
                ),
            )
        }
        record.scheduleId?.let { scheduleId ->
            refreshScheduleFromHistory(scheduleId)
        }
        id
    }

    suspend fun updateRecord(record: ServiceRecordEntity) = database.withTransaction {
        val oldScheduleId = serviceDao.findById(record.id)?.scheduleId
        serviceDao.update(record)
        odometerDao.deleteBySource(OdometerSource.ServiceRecord, record.id)
        record.odometerKm?.let { km ->
            odometerDao.insert(
                OdometerReadingEntity(
                    vehicleId = record.vehicleId,
                    recordedAt = record.performedAt,
                    odometerKm = km,
                    source = OdometerSource.ServiceRecord,
                    sourceRecordId = record.id,
                ),
            )
        }
        if (oldScheduleId != null && oldScheduleId != record.scheduleId) {
            refreshScheduleFromHistory(oldScheduleId)
        }
        record.scheduleId?.let { refreshScheduleFromHistory(it) }
    }

    suspend fun deleteRecord(id: Long) = database.withTransaction {
        val scheduleId = serviceDao.findById(id)?.scheduleId
        attachments.deleteForOwner(AttachmentOwner.ServiceRecord, id)
        odometerDao.deleteBySource(OdometerSource.ServiceRecord, id)
        serviceDao.deleteById(id)
        scheduleId?.let { refreshScheduleFromHistory(it) }
    }

    suspend fun addSchedule(schedule: MaintenanceScheduleEntity): Long = scheduleDao.insert(schedule)

    suspend fun addSchedules(schedules: List<MaintenanceScheduleEntity>): List<Long> =
        scheduleDao.insertAll(schedules)

    suspend fun updateSchedule(schedule: MaintenanceScheduleEntity) = scheduleDao.update(schedule)

    suspend fun deleteSchedule(schedule: MaintenanceScheduleEntity) = scheduleDao.delete(schedule)

    /**
     * Maintenance calendar events
     */
    fun observeEvents(vehicleId: Long): Flow<List<MaintenanceEventEntity>> =
        eventDao.observeForVehicle(vehicleId)

    fun observeUpcomingEvents(vehicleId: Long, fromEpochDay: Long): Flow<List<MaintenanceEventEntity>> =
        eventDao.observeUpcoming(vehicleId, fromEpochDay)

    suspend fun findEvent(id: Long): MaintenanceEventEntity? = eventDao.findById(id)

    suspend fun addEvent(event: MaintenanceEventEntity): Long {
        val id = eventDao.insert(event)
        val inserted = event.copy(id = id)
        scheduleEventAlarm(inserted)
        return id
    }

    suspend fun updateEvent(event: MaintenanceEventEntity) {
        eventDao.update(event)
        scheduleEventAlarm(event)
    }

    suspend fun deleteEvent(id: Long) {
        alarmScheduler?.cancelAlarm(id)
        eventDao.deleteById(id)
    }

    suspend fun completeEvent(
        eventId: Long,
        createServiceRecord: Boolean,
        actualOdometerKm: Double?,
        labourCostMinor: Long,
        partsCostMinor: Long,
        shop: String?,
        notes: String?,
    ): Long? = database.withTransaction {
        alarmScheduler?.cancelAlarm(eventId)
        val event = eventDao.findById(eventId) ?: return@withTransaction null
        val now = Instant.now()
        val today = LocalDate.now()
        val perfDate = if (event.scheduledDate.isAfter(today)) today else event.scheduledDate

        var createdRecordId: Long? = null
        if (createServiceRecord) {
            val record = ServiceRecordEntity(
                vehicleId = event.vehicleId,
                performedAt = perfDate.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                odometerKm = actualOdometerKm ?: event.targetOdometerKm,
                category = event.category,
                title = event.title,
                labourCostMinor = labourCostMinor.coerceAtLeast(0L),
                partsCostMinor = partsCostMinor.coerceAtLeast(0L),
                shop = shop?.takeIf { it.isNotBlank() } ?: event.shop,
                notes = notes?.takeIf { it.isNotBlank() } ?: event.notes,
                scheduleId = event.scheduleId,
            )
            createdRecordId = addRecord(record)
        }

        eventDao.markCompleted(
            id = eventId,
            completedAt = now.toEpochMilli(),
            serviceRecordId = createdRecordId,
        )

        createdRecordId
    }

    private suspend fun scheduleEventAlarm(event: MaintenanceEventEntity) {
        if (event.isCompleted || event.remindAdvanceDays < 0) {
            alarmScheduler?.cancelAlarm(event.id)
            return
        }
        val vehicle = vehicleDao.findById(event.vehicleId)
        val vehicleName = vehicle?.let { v ->
            v.nickname?.takeIf { it.isNotBlank() }
                ?: listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" ")
        }.orEmpty()
        alarmScheduler?.scheduleEventAlarm(event, vehicleName)
    }

    /**
     * Re-reads the last job booked against a plan item. Editing or deleting a
     * record can move the "last performed" mark backwards, and only the history
     * knows where it should now sit.
     */
    private suspend fun refreshScheduleFromHistory(scheduleId: Long) {
        val latest = serviceDao.findLatestForSchedule(scheduleId)
        scheduleDao.markPerformed(
            id = scheduleId,
            performedAt = latest?.performedAt?.toEpochMilli(),
            odometerKm = latest?.odometerKm,
        )
    }
}
