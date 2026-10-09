package com.cargenome.app.data.export

import android.util.Log
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
import kotlinx.coroutines.CancellationException
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
data class LoyaltyCardBackupDto(
    val id: Long,
    val title: String,
    val cardNumber: String,
    val barcodeType: String,
    val barcodeRawValue: String,
    val category: String,
    val colorHex: Long,
    val note: String? = null,
    val vehicleId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
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
    val loyaltyCards: List<LoyaltyCardBackupDto> = emptyList(),
)

@Singleton
class DataBackupManager @Inject constructor(
    private val db: CarGenomeDatabase,
    private val attachmentManager: AttachmentManager? = null,
    private val alarmScheduler: MaintenanceAlarmScheduler? = null,
) {
    companion object {
        const val BACKUP_VERSION = 1
        private const val TAG = "DataBackupManager"
    }

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
        val loyaltyCards = db.loyaltyCardDao().listAll().map { it.toDto() }

        val backup = CarGenomeBackup(
            version = BACKUP_VERSION,
            exportedAt = Instant.now().toString(),
            vehicles = vehicles,
            fuelRecords = fuels,
            serviceRecords = services,
            maintenanceSchedules = schedules,
            expenses = expenses,
            odometerReadings = readings,
            maintenanceEvents = events,
            loyaltyCards = loyaltyCards,
        )

        return json.encodeToString(backup)
    }

    /**
     * Merges a backup into what is already stored: a row from the backup
     * overwrites the row with the same id and everything else stays.
     *
     * Rows are upserted, never REPLACEd. REPLACE deletes the old row before
     * inserting, so for a car it set off ON DELETE CASCADE and silently took
     * every record logged since the backup was made.
     */
    suspend fun importJson(jsonContent: String) {
        val backup = decodeBackup(jsonContent)
        db.withTransaction {
            writeBackup(backup, localVehicles = db.vehicleDao().listAll())
        }
        afterCommit(
            cancelAlarmsFor = backup.maintenanceEvents.map { it.id },
            staleFileUris = emptyList(),
        )
    }

    /**
     * Makes the stored data match the backup exactly, as a cloud pull needs.
     *
     * The payload is decoded before anything is touched and the wipe shares one
     * transaction with the import, so a payload that cannot be decoded or
     * inserted leaves the current data as it was.
     *
     * Attachments are local files a backup does not carry. Those whose record is
     * still there afterwards keep pointing at it; only the ones left without a
     * record are dropped, and their files go once the transaction has committed.
     */
    suspend fun replaceAllData(jsonContent: String) {
        val backup = decodeBackup(jsonContent)
        val outcome = db.withTransaction {
            val previousVehicles = db.vehicleDao().listAll()
            val previousEventIds = db.maintenanceEventDao().listAll().map { it.id }

            db.vehicleDao().deleteAll()
            db.loyaltyCardDao().deleteAll()
            writeBackup(backup, localVehicles = previousVehicles)

            val orphans = db.attachmentDao().listOrphans()
            db.attachmentDao().deleteAll(orphans)

            val keptVehicleIds = backup.vehicles.mapTo(HashSet()) { it.id }
            val droppedVehicleFiles = previousVehicles
                .filter { it.id !in keptVehicleIds }
                .flatMap { listOfNotNull(it.photoUri, it.insurancePdfUri) }

            ReplaceOutcome(
                previousEventIds = previousEventIds,
                staleFileUris = orphans.map { it.uri } + droppedVehicleFiles,
            )
        }
        afterCommit(
            cancelAlarmsFor = outcome.previousEventIds,
            staleFileUris = outcome.staleFileUris,
        )
    }

    suspend fun clearAllData() {
        val eventIds = db.withTransaction {
            val ids = db.maintenanceEventDao().listAll().map { it.id }
            db.vehicleDao().deleteAll()
            db.loyaltyCardDao().deleteAll()
            db.attachmentDao().deleteAll()
            ids
        }
        // Alarms and files cannot roll back, so they go only once the rows have.
        eventIds.forEach { alarmScheduler?.cancelAlarm(it) }
        attachmentManager?.deleteAllAttachments()
    }

    private fun decodeBackup(jsonContent: String): CarGenomeBackup {
        val backup = json.decodeFromString<CarGenomeBackup>(jsonContent)
        // Unknown keys are ignored, so a newer format would import with parts missing.
        require(backup.version <= BACKUP_VERSION) {
            "Backup format ${backup.version} is newer than this app supports ($BACKUP_VERSION)"
        }
        return backup
    }

    /**
     * Writes the backup's rows in foreign-key order. A backup does not carry a
     * car's photo, insurance PDF or cached VIN decode, which exist on this device
     * only, so those are kept from [localVehicles] for the car with the same id.
     */
    private suspend fun writeBackup(backup: CarGenomeBackup, localVehicles: List<VehicleEntity>) {
        val local = localVehicles.associateBy { it.id }
        db.vehicleDao().upsertAll(
            backup.vehicles.map { dto ->
                val restored = dto.toEntity()
                val existing = local[restored.id] ?: return@map restored
                restored.copy(
                    photoUri = existing.photoUri,
                    insurancePdfUri = existing.insurancePdfUri,
                    vinDecodeJson = existing.vinDecodeJson.takeIf { existing.vin == restored.vin },
                )
            },
        )
        // Schedules before service records and events, which point at them.
        db.maintenanceScheduleDao().upsertAll(backup.maintenanceSchedules.map { it.toEntity() })
        db.fuelRecordDao().upsertAll(backup.fuelRecords.map { it.toEntity() })
        db.serviceRecordDao().upsertAll(backup.serviceRecords.map { it.toEntity() })
        db.expenseDao().upsertAll(backup.expenses.map { it.toEntity() })
        db.odometerReadingDao().upsertAll(backup.odometerReadings.map { it.toEntity() })
        db.loyaltyCardDao().upsertAll(backup.loyaltyCards.map { it.toEntity() })
        db.maintenanceEventDao().upsertAll(backup.maintenanceEvents.map { it.toEntity() })
    }

    /**
     * Alarms and files cannot roll back with a transaction, so they are touched
     * only after the commit. The data is in place by then, so a failure here is
     * logged instead of being reported as a failed import.
     */
    private suspend fun afterCommit(cancelAlarmsFor: Collection<Long>, staleFileUris: Collection<String>) {
        try {
            cancelAlarmsFor.forEach { alarmScheduler?.cancelAlarm(it) }
            alarmScheduler?.rescheduleAllAlarms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not reschedule maintenance alarms after import", e)
        }
        staleFileUris.forEach { attachmentManager?.deleteAttachmentFile(it) }
    }

    private class ReplaceOutcome(
        val previousEventIds: List<Long>,
        val staleFileUris: List<String>,
    )

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

    private fun com.cargenome.app.data.db.entity.LoyaltyCardEntity.toDto() = LoyaltyCardBackupDto(
        id = id,
        title = title,
        cardNumber = cardNumber,
        barcodeType = barcodeType.name,
        barcodeRawValue = barcodeRawValue,
        category = category.name,
        colorHex = colorHex,
        note = note,
        vehicleId = vehicleId,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    private fun LoyaltyCardBackupDto.toEntity() = com.cargenome.app.data.db.entity.LoyaltyCardEntity(
        id = id,
        title = title,
        cardNumber = cardNumber,
        barcodeType = runCatching { com.cargenome.app.data.db.entity.BarcodeType.valueOf(barcodeType) }
            .getOrDefault(com.cargenome.app.data.db.entity.BarcodeType.Code128),
        barcodeRawValue = barcodeRawValue.ifBlank { cardNumber },
        category = runCatching { com.cargenome.app.data.db.entity.LoyaltyCategory.valueOf(category) }
            .getOrDefault(com.cargenome.app.data.db.entity.LoyaltyCategory.Fuel),
        colorHex = colorHex,
        note = note,
        vehicleId = vehicleId,
        createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.now()),
        updatedAt = runCatching { Instant.parse(updatedAt) }.getOrDefault(Instant.now()),
    )
}
