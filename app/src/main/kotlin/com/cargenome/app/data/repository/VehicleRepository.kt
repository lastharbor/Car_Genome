package com.cargenome.app.data.repository

import androidx.room.withTransaction
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.dao.VehicleSummary
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.VehicleEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.domain.service.MaintenanceAlarmScheduler
import kotlinx.coroutines.flow.Flow

@Singleton
class VehicleRepository @Inject constructor(
    private val database: CarGenomeDatabase,
    private val vehicleDao: VehicleDao,
    private val odometerDao: OdometerReadingDao,
    private val attachments: AttachmentRepository,
    private val attachmentManager: AttachmentManager? = null,
    private val maintenanceEventDao: MaintenanceEventDao? = null,
    private val alarmScheduler: MaintenanceAlarmScheduler? = null,
) {
    fun observeActive(): Flow<List<VehicleEntity>> = vehicleDao.observeActive()

    fun observeSummaries(): Flow<List<VehicleSummary>> = vehicleDao.observeSummaries()

    fun observeAll(): Flow<List<VehicleEntity>> = vehicleDao.observeAll()

    fun observe(id: Long): Flow<VehicleEntity?> = vehicleDao.observeById(id)

    fun observeSelectedVehicle(
        settingsRepo: com.cargenome.app.data.settings.AppSettingsRepository,
        explicitVehicleId: Long? = null,
    ): Flow<Pair<VehicleEntity?, List<VehicleEntity>>> =
        kotlinx.coroutines.flow.combine(observeActive(), settingsRepo.settings) { activeVehicles, settings ->
            val targetId = explicitVehicleId ?: settings.selectedVehicleId
            val selected = when {
                targetId != null ->
                    activeVehicles.find { it.id == targetId } ?: activeVehicles.firstOrNull()
                else -> activeVehicles.firstOrNull()
            }
            selected to activeVehicles
        }

    fun observeActiveCount(): Flow<Int> = vehicleDao.observeActiveCount()

    suspend fun find(id: Long): VehicleEntity? = vehicleDao.findById(id)

    suspend fun findByVin(vin: String): VehicleEntity? = vehicleDao.findByVin(vin)

    /**
     * Adds a car and seeds the odometer log with the mileage it was bought at,
     * so the very first fill-up already has something to measure against.
     */
    suspend fun add(vehicle: VehicleEntity, now: Instant = Instant.now()): Long =
        database.withTransaction {
            val id = vehicleDao.insert(vehicle.copy(createdAt = now))
            if (vehicle.initialOdometerKm > 0.0) {
                odometerDao.insert(
                    OdometerReadingEntity(
                        vehicleId = id,
                        recordedAt = vehicle.purchasedOn
                            ?.atStartOfDay(ZoneId.systemDefault())
                            ?.toInstant()
                            ?: now,
                        odometerKm = vehicle.initialOdometerKm,
                        source = OdometerSource.Manual,
                    ),
                )
            }
            id
        }

    suspend fun update(vehicle: VehicleEntity) = vehicleDao.update(vehicle)

    suspend fun setArchived(id: Long, archived: Boolean) = vehicleDao.setArchived(id, archived)

    /**
     * Removes the car and everything hanging off it. Child rows go by cascade;
     * attachments are cleared first, while the records they point at still
     * exist to be found.
     */
    suspend fun delete(id: Long) = database.withTransaction {
        val events = maintenanceEventDao?.listForVehicle(id).orEmpty()
        events.forEach { alarmScheduler?.cancelAlarm(it.id) }
        val vehicle = vehicleDao.findById(id)
        vehicle?.photoUri?.let { attachmentManager?.deleteAttachmentFile(it) }
        vehicle?.insurancePdfUri?.let { attachmentManager?.deleteAttachmentFile(it) }
        attachments.deleteForVehicle(id)
        vehicleDao.deleteById(id)
    }
}
