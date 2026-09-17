package com.cargenome.app.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataBackupManagerTest {

    private lateinit var database: CarGenomeDatabase
    private lateinit var backupManager: DataBackupManager

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarGenomeDatabase::class.java,
        ).build()
        backupManager = DataBackupManager(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportAndImportJsonRoundTrip() = runTest {
        val v1 = VehicleEntity(
            id = 1L,
            vin = "WVWZZZ3CZAE123456",
            make = "Volkswagen",
            model = "Passat",
            modelYear = 2010,
            plateNumber = "A123BC77",
            fuelType = FuelType.Diesel,
            distanceUnit = DistanceUnit.Kilometres,
            volumeUnit = VolumeUnit.Litres,
            currencyCode = "RUB",
            initialOdometerKm = 100_000.0,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        )
        database.vehicleDao().insert(v1)

        val fuelRecord = FuelRecordEntity(
            id = 10L,
            vehicleId = 1L,
            filledAt = Instant.parse("2025-01-05T10:00:00Z"),
            odometerKm = 100_500.0,
            volumeLitres = 50.0,
            totalCostMinor = 325_000L,
            isFullTank = true,
            missedPreviousFillUp = false,
            station = "Lukoil",
            notes = "Winter diesel",
        )
        database.fuelRecordDao().insert(fuelRecord)

        val serviceRecord = ServiceRecordEntity(
            id = 20L,
            vehicleId = 1L,
            performedAt = Instant.parse("2025-01-10T12:00:00Z"),
            odometerKm = 101_000.0,
            category = ServiceCategory.RoutineService,
            title = "Oil service",
            labourCostMinor = 200_000L,
            partsCostMinor = 500_000L,
            shop = "Local Garage",
            scheduleId = null,
            notes = "5W-30 synthetic",
        )
        database.serviceRecordDao().insert(serviceRecord)

        val schedule = MaintenanceScheduleEntity(
            id = 30L,
            vehicleId = 1L,
            title = "Engine oil",
            category = ServiceCategory.RoutineService,
            intervalKm = 10_000.0,
            intervalMonths = 12,
            warnBeforeKm = 1_000.0,
            warnBeforeDays = 30,
            isEnabled = true,
            lastPerformedAt = Instant.parse("2025-01-10T12:00:00Z"),
            lastPerformedOdometerKm = 101_000.0,
        )
        database.maintenanceScheduleDao().insert(schedule)

        val expense = ExpenseEntity(
            id = 40L,
            vehicleId = 1L,
            incurredAt = Instant.parse("2025-01-12T15:00:00Z"),
            amountMinor = 80_000L,
            category = ExpenseCategory.Wash,
            title = "Complex car wash",
            odometerKm = 101_200.0,
            notes = null,
        )
        database.expenseDao().insert(expense)

        val odo = OdometerReadingEntity(
            id = 50L,
            vehicleId = 1L,
            recordedAt = Instant.parse("2025-01-15T09:00:00Z"),
            odometerKm = 101_500.0,
            source = OdometerSource.Manual,
            sourceRecordId = null,
        )
        database.odometerReadingDao().insert(odo)

        // Export
        val json = backupManager.exportJson()
        assertTrue(json.contains("WVWZZZ3CZAE123456"))
        assertTrue(json.contains("Oil service"))
        assertTrue(json.contains("Lukoil"))

        // Clear all data
        backupManager.clearAllData()
        assertEquals(0, database.vehicleDao().listAll().size)
        assertEquals(0, database.fuelRecordDao().listAll().size)
        assertEquals(0, database.serviceRecordDao().listAll().size)

        // Restore
        backupManager.importJson(json)

        val restoredVehicles = database.vehicleDao().listAll()
        assertEquals(1, restoredVehicles.size)
        assertEquals("Passat", restoredVehicles.first().model)

        val restoredFuel = database.fuelRecordDao().listAll()
        assertEquals(1, restoredFuel.size)
        assertEquals(325_000L, restoredFuel.first().totalCostMinor)

        val restoredService = database.serviceRecordDao().listAll()
        assertEquals(1, restoredService.size)
        assertEquals("Oil service", restoredService.first().title)

        val restoredSchedules = database.maintenanceScheduleDao().listAll()
        assertEquals(1, restoredSchedules.size)
        assertEquals("Engine oil", restoredSchedules.first().title)

        val restoredExpenses = database.expenseDao().listAll()
        assertEquals(1, restoredExpenses.size)
        assertEquals(80_000L, restoredExpenses.first().amountMinor)

        val restoredOdo = database.odometerReadingDao().listAll()
        assertEquals(1, restoredOdo.size)
        assertEquals(101_500.0, restoredOdo.first().odometerKm, 0.001)
    }

    @Test
    fun exportCsvGeneratesValidContent() = runTest {
        val v1 = VehicleEntity(
            id = 1L,
            vin = "WVWZZZ3CZAE123456",
            make = "Volkswagen",
            model = "Passat",
            distanceUnit = DistanceUnit.Kilometres,
            volumeUnit = VolumeUnit.Litres,
            currencyCode = "RUB",
            createdAt = Instant.now(),
        )
        database.vehicleDao().insert(v1)

        val fuel = FuelRecordEntity(
            vehicleId = 1L,
            filledAt = Instant.parse("2025-01-05T10:00:00Z"),
            odometerKm = 100_500.0,
            volumeLitres = 50.0,
            totalCostMinor = 325_000L,
            isFullTank = true,
            missedPreviousFillUp = false,
            station = "Lukoil",
            notes = "Test fill-up",
        )
        database.fuelRecordDao().insert(fuel)

        val csvFuel = backupManager.exportFuelCsv(1L)
        assertTrue(csvFuel.startsWith("id,date,odometer_km,volume_litres,total_cost_minor,station,full_tank,missed_previous,notes"))
        assertTrue(csvFuel.contains("Lukoil"))
        assertTrue(csvFuel.contains("325000"))

        val service = ServiceRecordEntity(
            vehicleId = 1L,
            performedAt = Instant.parse("2025-01-10T12:00:00Z"),
            odometerKm = 101_000.0,
            category = ServiceCategory.RoutineService,
            title = "Oil service",
            labourCostMinor = 200_000L,
            partsCostMinor = 500_000L,
            shop = "Local Garage",
            scheduleId = null,
            notes = null,
        )
        database.serviceRecordDao().insert(service)

        val csvService = backupManager.exportServiceCsv(1L)
        assertTrue(csvService.startsWith("id,date,odometer_km,category,title,labour_minor,parts_minor,total_minor,shop,notes"))
        assertTrue(csvService.contains("Oil service"))

        val expense = ExpenseEntity(
            vehicleId = 1L,
            incurredAt = Instant.parse("2025-01-12T15:00:00Z"),
            amountMinor = 80_000L,
            category = ExpenseCategory.Wash,
            title = "Car wash",
            odometerKm = 101_200.0,
            notes = null,
        )
        database.expenseDao().insert(expense)

        val csvExpense = backupManager.exportExpensesCsv(1L)
        assertTrue(csvExpense.startsWith("id,date,odometer_km,category,title,amount_minor,notes"))
        assertTrue(csvExpense.contains("Car wash"))
    }

    @Test
    fun testCsvEscapingCarriageReturnAndCommas() = runTest {
        val v = VehicleEntity(
            id = 2L,
            make = "Toyota",
            model = "Camry",
            currencyCode = "USD",
        )
        database.vehicleDao().insert(v)

        val expense = ExpenseEntity(
            vehicleId = 2L,
            incurredAt = Instant.parse("2025-02-01T10:00:00Z"),
            amountMinor = 5000L,
            category = ExpenseCategory.Other,
            title = "Toll, \"Bridge\" Pass",
            notes = "Line 1\r\nLine 2 with, comma",
        )
        database.expenseDao().insert(expense)

        val csv = backupManager.exportExpensesCsv(2L)
        // Title contains comma and quotes, so it must be escaped with double quotes: "Toll, ""Bridge"" Pass"
        assertTrue(csv.contains("\"Toll, \"\"Bridge\"\" Pass\""))
        // Notes contain \r\n and comma, so it must be enclosed in quotes
        assertTrue(csv.contains("\"Line 1\r\nLine 2 with, comma\""))
    }

    @Test
    fun clearAllDataRemovesAttachmentsFromDatabase() = runTest {
        val v = VehicleEntity(
            id = 5L,
            make = "BMW",
            model = "M3",
            currencyCode = "EUR",
        )
        database.vehicleDao().insert(v)

        val att = com.cargenome.app.data.db.entity.AttachmentEntity(
            ownerType = com.cargenome.app.data.db.entity.AttachmentOwner.Vehicle,
            ownerId = 5L,
            uri = "content://media/external/images/5",
            addedAt = Instant.now(),
        )
        database.attachmentDao().insert(att)
        assertEquals(1, database.attachmentDao().listForOwner(com.cargenome.app.data.db.entity.AttachmentOwner.Vehicle, 5L).size)

        backupManager.clearAllData()

        assertEquals(0, database.vehicleDao().listAll().size)
        assertEquals(0, database.attachmentDao().listForOwner(com.cargenome.app.data.db.entity.AttachmentOwner.Vehicle, 5L).size)
    }
}
