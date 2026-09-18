package com.cargenome.app.data.export

import androidx.room.withTransaction
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.domain.service.MaintenanceAlarmScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VehicleBackupDto(
    val id: Long,
    val vin: String? = null,
    val make: String,
    val model: String,
    val modelYear: Int? = null,
    val trim: String? = null,
    val engine: String? = null,
    val fuelType: String,
    val plateNumber: String? = null,
    val nickname: String? = null,
    val distanceUnit: String,
    val volumeUnit: String,
    val currencyCode: String,
    val purchasedOn: String? = null,
    val initialOdometerKm: Double = 0.0,
    val insuranceProvider: String? = null,
    val insurancePolicyNumber: String? = null,
    val insuranceExpiresOn: String? = null,
    val createdAt: String,
    val isArchived: Boolean = false,
)

@Serializable
data class FuelRecordBackupDto(
    val id: Long,
    val vehicleId: Long,
    val filledAt: String,
    val odometerKm: Double,
    val volumeLitres: Double,
    val totalCostMinor: Long,
    val station: String? = null,
    val isFullTank: Boolean,
    val missedPreviousFillUp: Boolean,
    val notes: String? = null,
)

@Serializable
data class ServiceRecordBackupDto(
    val id: Long,
    val vehicleId: Long,
    val performedAt: String,
    val odometerKm: Double? = null,
    val category: String,
    val title: String,
    val labourCostMinor: Long,
    val partsCostMinor: Long,
    val shop: String? = null,
    val notes: String? = null,
    val scheduleId: Long? = null,
)

@Serializable
data class MaintenanceScheduleBackupDto(
    val id: Long,
    val vehicleId: Long,
    val title: String,
    val category: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val lastPerformedAt: String? = null,
    val lastPerformedOdometerKm: Double? = null,
    val warnBeforeKm: Double,
    val warnBeforeDays: Int,
    val isEnabled: Boolean,
    val notes: String? = null,
)

@Serializable
data class ExpenseBackupDto(
    val id: Long,
    val vehicleId: Long,
    val incurredAt: String,
    val category: String,
    val title: String,
    val amountMinor: Long,
    val odometerKm: Double? = null,
    val notes: String? = null,
)

@Serializable
data class OdometerReadingBackupDto(
    val id: Long,
    val vehicleId: Long,
    val recordedAt: String,
    val odometerKm: Double,
    val source: String,
    val sourceRecordId: Long? = null,
    val note: String? = null,
)

@Serializable
data class MaintenanceEventBackupDto(
    val id: Long,
    val vehicleId: Long,
    val scheduleId: Long? = null,
    val title: String,
    val category: String,
    val scheduledDate: String,
    val scheduledTimeMinutes: Int? = null,
    val targetOdometerKm: Double? = null,
    val estimatedCostMinor: Long? = null,
    val shop: String? = null,
    val notes: String? = null,
    val remindAdvanceDays: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: String? = null,
    val serviceRecordId: Long? = null,
    val createdAt: String,
)

@Serializable
data class CarGenomeBackup(
    val version: Int = 1,
    val exportedAt: String,
    val vehicles: List<VehicleBackupDto>,
    val fuelRecords: List<FuelRecordBackupDto>,
    val serviceRecords: List<ServiceRecordBackupDto>,
    val maintenanceSchedules: List<MaintenanceScheduleBackupDto>,
    val expenses: List<ExpenseBackupDto>,
    val odometerReadings: List<OdometerReadingBackupDto>,
    val maintenanceEvents: List<MaintenanceEventBackupDto> = emptyList(),
)

@Singleton
class DataBackupManager @Inject constructor(
    private val db: CarGenomeDatabase,
    private val attachmentManager: AttachmentManager? = null,
    private val alarmScheduler: MaintenanceAlarmScheduler? = null,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportJson(): String {
        val vehicles = db.vehicleDao().listAll().map { it.toDto() }
        val fuels = db.fuelRecordDao().listAll().map { it.toDto() }
        val services = db.serviceRecordDao().listAll().map { it.toDto() }
        val schedules = db.maintenanceScheduleDao().listAll().map { it.toDto() }
        val expenses = db.expenseDao().listAll().map { it.toDto() }
        val readings = db.odometerReadingDao().listAll().map { it.toDto() }
        val events = db.maintenanceEventDao().listAll().map { it.toDto() }

        val backup = CarGenomeBackup(
            version = 1,
            exportedAt = Instant.now().toString(),
            vehicles = vehicles,
            fuelRecords = fuels,
            serviceRecords = services,
            maintenanceSchedules = schedules,
            expenses = expenses,
            odometerReadings = readings,
            maintenanceEvents = events,
        )

        return json.encodeToString(backup)
    }

    suspend fun importJson(jsonContent: String) = db.withTransaction {
        val backup = json.decodeFromString<CarGenomeBackup>(jsonContent)

        // Insert vehicles first (so foreign keys are valid)
        db.vehicleDao().insertAll(backup.vehicles.map { it.toEntity() })

        // Insert schedules before service records (since service records reference scheduleId)
        db.maintenanceScheduleDao().insertAll(backup.maintenanceSchedules.map { it.toEntity() })

        db.fuelRecordDao().insertAll(backup.fuelRecords.map { it.toEntity() })
        db.serviceRecordDao().insertAll(backup.serviceRecords.map { it.toEntity() })
        db.expenseDao().insertAll(backup.expenses.map { it.toEntity() })
        db.odometerReadingDao().insertAll(backup.odometerReadings.map { it.toEntity() })

        val importedEvents = backup.maintenanceEvents.map { it.toEntity() }
        db.maintenanceEventDao().insertAll(importedEvents)
        for (event in importedEvents) {
            if (!event.isCompleted && event.remindAdvanceDays >= 0) {
                val vehicle = db.vehicleDao().findById(event.vehicleId)
                val vehicleName = vehicle?.let { v ->
                    v.nickname?.takeIf { it.isNotBlank() }
                        ?: listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" ")
                }.orEmpty()
                alarmScheduler?.scheduleEventAlarm(event, vehicleName)
            }
        }
    }

    suspend fun clearAllData() = db.withTransaction {
        val events = db.maintenanceEventDao().listAll()
        for (e in events) {
            alarmScheduler?.cancelAlarm(e.id)
        }
        val vehicles = db.vehicleDao().listAll()
        for (v in vehicles) {
            db.vehicleDao().deleteById(v.id)
        }
        db.attachmentDao().deleteAll()
        attachmentManager?.deleteAllAttachments()
    }

    suspend fun exportFuelCsv(vehicleId: Long): String {
        val records = db.fuelRecordDao().listChronological(vehicleId)
        val sb = StringBuilder()
        sb.append("id,date,odometer_km,volume_litres,total_cost_minor,station,full_tank,missed_previous,notes\n")
        for (r in records) {
            sb.append(r.id).append(',')
            sb.append(r.filledAt).append(',')
            sb.append(r.odometerKm).append(',')
            sb.append(r.volumeLitres).append(',')
            sb.append(r.totalCostMinor).append(',')
            sb.append(csvEscape(r.station.orEmpty())).append(',')
            sb.append(r.isFullTank).append(',')
            sb.append(r.missedPreviousFillUp).append(',')
            sb.append(csvEscape(r.notes.orEmpty())).append('\n')
        }
        return sb.toString()
    }

    suspend fun exportServiceCsv(vehicleId: Long): String {
        val list = db.serviceRecordDao().listChronological(vehicleId)
        val sb = StringBuilder()
        sb.append("id,date,odometer_km,category,title,labour_minor,parts_minor,total_minor,shop,notes\n")
        for (r in list) {
            sb.append(r.id).append(',')
            sb.append(r.performedAt).append(',')
            sb.append(r.odometerKm ?: "").append(',')
            sb.append(r.category.name).append(',')
            sb.append(csvEscape(r.title)).append(',')
            sb.append(r.labourCostMinor).append(',')
            sb.append(r.partsCostMinor).append(',')
            sb.append(r.labourCostMinor + r.partsCostMinor).append(',')
            sb.append(csvEscape(r.shop.orEmpty())).append(',')
            sb.append(csvEscape(r.notes.orEmpty())).append('\n')
        }
        return sb.toString()
    }

    suspend fun exportExpensesCsv(vehicleId: Long): String {
        val list = db.expenseDao().listChronological(vehicleId)
        val sb = StringBuilder()
        sb.append("id,date,odometer_km,category,title,amount_minor,notes\n")
        for (e in list) {
            sb.append(e.id).append(',')
            sb.append(e.incurredAt).append(',')
            sb.append(e.odometerKm ?: "").append(',')
            sb.append(e.category.name).append(',')
            sb.append(csvEscape(e.title)).append(',')
            sb.append(e.amountMinor).append(',')
            sb.append(csvEscape(e.notes.orEmpty())).append('\n')
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun VehicleEntity.toDto() = VehicleBackupDto(
        id = id,
        vin = vin,
        make = make,
        model = model,
        modelYear = modelYear,
        trim = trim,
        engine = engine,
        fuelType = fuelType.name,
        plateNumber = plateNumber,
        nickname = nickname,
        distanceUnit = distanceUnit.name,
        volumeUnit = volumeUnit.name,
        currencyCode = currencyCode,
        purchasedOn = purchasedOn?.toString(),
        initialOdometerKm = initialOdometerKm,
        insuranceProvider = insuranceProvider,
        insurancePolicyNumber = insurancePolicyNumber,
        insuranceExpiresOn = insuranceExpiresOn?.toString(),
        createdAt = createdAt.toString(),
        isArchived = isArchived,
    )

    private fun VehicleBackupDto.toEntity() = VehicleEntity(
        id = id,
        vin = vin,
        make = make,
        model = model,
        modelYear = modelYear,
        trim = trim,
        engine = engine,
        fuelType = runCatching { FuelType.valueOf(fuelType) }.getOrDefault(FuelType.Petrol),
        plateNumber = plateNumber,
        nickname = nickname,
        distanceUnit = runCatching { DistanceUnit.valueOf(distanceUnit) }.getOrDefault(DistanceUnit.Kilometres),
        volumeUnit = runCatching { VolumeUnit.valueOf(volumeUnit) }.getOrDefault(VolumeUnit.Litres),
        currencyCode = currencyCode,
        purchasedOn = purchasedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        initialOdometerKm = initialOdometerKm,
        insuranceProvider = insuranceProvider,
        insurancePolicyNumber = insurancePolicyNumber,
        insuranceExpiresOn = insuranceExpiresOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.EPOCH),
        isArchived = isArchived,
    )

    private fun FuelRecordEntity.toDto() = FuelRecordBackupDto(
        id = id,
        vehicleId = vehicleId,
        filledAt = filledAt.toString(),
        odometerKm = odometerKm,
        volumeLitres = volumeLitres,
        totalCostMinor = totalCostMinor,
        station = station,
        isFullTank = isFullTank,
        missedPreviousFillUp = missedPreviousFillUp,
        notes = notes,
    )

    private fun FuelRecordBackupDto.toEntity() = FuelRecordEntity(
        id = id,
        vehicleId = vehicleId,
        filledAt = runCatching { Instant.parse(filledAt) }.getOrDefault(Instant.now()),
        odometerKm = odometerKm,
        volumeLitres = volumeLitres,
        totalCostMinor = totalCostMinor,
        station = station,
        isFullTank = isFullTank,
        missedPreviousFillUp = missedPreviousFillUp,
        notes = notes,
    )

    private fun ServiceRecordEntity.toDto() = ServiceRecordBackupDto(
        id = id,
        vehicleId = vehicleId,
        performedAt = performedAt.toString(),
        odometerKm = odometerKm,
        category = category.name,
        title = title,
        labourCostMinor = labourCostMinor,
        partsCostMinor = partsCostMinor,
        shop = shop,
        notes = notes,
        scheduleId = scheduleId,
    )

    private fun ServiceRecordBackupDto.toEntity() = ServiceRecordEntity(
        id = id,
        vehicleId = vehicleId,
        performedAt = runCatching { Instant.parse(performedAt) }.getOrDefault(Instant.now()),
        odometerKm = odometerKm,
        category = runCatching { ServiceCategory.valueOf(category) }.getOrDefault(ServiceCategory.RoutineService),
        title = title,
        labourCostMinor = labourCostMinor,
        partsCostMinor = partsCostMinor,
        shop = shop,
        notes = notes,
        scheduleId = scheduleId,
    )

    private fun MaintenanceScheduleEntity.toDto() = MaintenanceScheduleBackupDto(
        id = id,
        vehicleId = vehicleId,
        title = title,
        category = category.name,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastPerformedAt = lastPerformedAt?.toString(),
        lastPerformedOdometerKm = lastPerformedOdometerKm,
        warnBeforeKm = warnBeforeKm,
        warnBeforeDays = warnBeforeDays,
        isEnabled = isEnabled,
        notes = notes,
    )

    private fun MaintenanceScheduleBackupDto.toEntity() = MaintenanceScheduleEntity(
        id = id,
        vehicleId = vehicleId,
        title = title,
        category = runCatching { ServiceCategory.valueOf(category) }.getOrDefault(ServiceCategory.RoutineService),
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastPerformedAt = lastPerformedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        lastPerformedOdometerKm = lastPerformedOdometerKm,
        warnBeforeKm = warnBeforeKm,
        warnBeforeDays = warnBeforeDays,
        isEnabled = isEnabled,
        notes = notes,
    )

    private fun ExpenseEntity.toDto() = ExpenseBackupDto(
        id = id,
        vehicleId = vehicleId,
        incurredAt = incurredAt.toString(),
        category = category.name,
        title = title,
        amountMinor = amountMinor,
        odometerKm = odometerKm,
        notes = notes,
    )

    private fun ExpenseBackupDto.toEntity() = ExpenseEntity(
        id = id,
        vehicleId = vehicleId,
        incurredAt = runCatching { Instant.parse(incurredAt) }.getOrDefault(Instant.now()),
        category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.Other),
        title = title,
        amountMinor = amountMinor,
        odometerKm = odometerKm,
        notes = notes,
    )

    private fun OdometerReadingEntity.toDto() = OdometerReadingBackupDto(
        id = id,
        vehicleId = vehicleId,
        recordedAt = recordedAt.toString(),
        odometerKm = odometerKm,
        source = source.name,
        sourceRecordId = sourceRecordId,
        note = note,
    )

    private fun OdometerReadingBackupDto.toEntity() = OdometerReadingEntity(
        id = id,
        vehicleId = vehicleId,
        recordedAt = runCatching { Instant.parse(recordedAt) }.getOrDefault(Instant.now()),
        odometerKm = odometerKm,
        source = runCatching { OdometerSource.valueOf(source) }.getOrDefault(OdometerSource.Manual),
        sourceRecordId = sourceRecordId,
        note = note,
    )

    private fun MaintenanceEventEntity.toDto() = MaintenanceEventBackupDto(
        id = id,
        vehicleId = vehicleId,
        scheduleId = scheduleId,
        title = title,
        category = category.name,
        scheduledDate = scheduledDate.toString(),
        scheduledTimeMinutes = scheduledTimeMinutes,
        targetOdometerKm = targetOdometerKm,
        estimatedCostMinor = estimatedCostMinor,
        shop = shop,
        notes = notes,
        remindAdvanceDays = remindAdvanceDays,
        isCompleted = isCompleted,
        completedAt = completedAt?.toString(),
        serviceRecordId = serviceRecordId,
        createdAt = createdAt.toString(),
    )

    private fun MaintenanceEventBackupDto.toEntity() = MaintenanceEventEntity(
        id = id,
        vehicleId = vehicleId,
        scheduleId = scheduleId,
        title = title,
        category = runCatching { ServiceCategory.valueOf(category) }.getOrDefault(ServiceCategory.RoutineService),
        scheduledDate = runCatching { LocalDate.parse(scheduledDate) }.getOrDefault(LocalDate.now()),
        scheduledTimeMinutes = scheduledTimeMinutes,
        targetOdometerKm = targetOdometerKm,
        estimatedCostMinor = estimatedCostMinor,
        shop = shop,
        notes = notes,
        remindAdvanceDays = remindAdvanceDays,
        isCompleted = isCompleted,
        completedAt = completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        serviceRecordId = serviceRecordId,
        createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.now()),
    )
}
