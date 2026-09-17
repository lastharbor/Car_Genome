package com.cargenome.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.VinCacheEntryEntity
import com.cargenome.app.data.repository.AttachmentRepository
import com.cargenome.app.data.repository.ExpenseRepository
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.repository.VinCacheRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarGenomeDatabaseTest {

    private lateinit var database: CarGenomeDatabase
    private lateinit var attachments: AttachmentRepository
    private lateinit var vehicles: VehicleRepository
    private lateinit var fuel: FuelRepository
    private lateinit var service: ServiceRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var odometer: OdometerRepository
    private lateinit var vinCache: VinCacheRepository

    private val start: Instant = Instant.parse("2025-01-10T08:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarGenomeDatabase::class.java,
        ).build()

        attachments = AttachmentRepository(database.attachmentDao())
        vehicles = VehicleRepository(
            database,
            database.vehicleDao(),
            database.odometerReadingDao(),
            attachments,
        )
        fuel = FuelRepository(
            database,
            database.fuelRecordDao(),
            database.odometerReadingDao(),
            attachments,
        )
        service = ServiceRepository(
            database,
            database.serviceRecordDao(),
            database.maintenanceScheduleDao(),
            database.odometerReadingDao(),
            attachments,
        )
        expenses = ExpenseRepository(
            database,
            database.expenseDao(),
            database.odometerReadingDao(),
            attachments,
        )
        odometer = OdometerRepository(database.odometerReadingDao())
        vinCache = VinCacheRepository(database.vinCacheDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun addVehicle(
        vin: String? = "XTA21099062546789",
        initialKm: Double = 120_000.0,
    ): Long = vehicles.add(
        VehicleEntity(
            vin = vin,
            make = "LADA",
            model = "2110",
            modelYear = 2006,
            purchasedOn = LocalDate.of(2024, 3, 1),
            initialOdometerKm = initialKm,
        ),
        now = start,
    )

    @Test
    fun `adding a vehicle seeds the odometer log with its purchase mileage`() = runTest {
        val id = addVehicle(initialKm = 120_000.0)

        assertEquals(120_000.0, odometer.currentKm(id)!!, 0.001)
        val readings = odometer.observe(id).first()
        assertEquals(1, readings.size)
        assertEquals(OdometerSource.Manual, readings.single().source)
    }

    @Test
    fun `a vehicle without a starting mileage gets no odometer row`() = runTest {
        val id = addVehicle(initialKm = 0.0)

        assertNull(odometer.currentKm(id))
    }

    @Test
    fun `the same VIN cannot be added twice`() = runTest {
        addVehicle(vin = "WVWZZZ1JZ3W386752")

        val failure = runCatching { addVehicle(vin = "WVWZZZ1JZ3W386752") }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(
            "expected a uniqueness failure, got $failure",
            failure!!.message.orEmpty().contains("UNIQUE", ignoreCase = true),
        )
    }

    @Test
    fun `cars without a VIN do not collide with each other`() = runTest {
        addVehicle(vin = null)
        addVehicle(vin = null)

        assertEquals(2, vehicles.observeActive().first().size)
    }

    @Test
    fun `a fill-up records its mileage in the odometer log`() = runTest {
        val id = addVehicle()

        val fuelId = fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(3)),
                odometerKm = 120_450.0,
                volumeLitres = 42.5,
                totalCostMinor = 234_600,
            ),
        )

        val readings = odometer.observe(id).first()
        assertEquals(2, readings.size)
        val fromFuel = readings.single { it.source == OdometerSource.FuelRecord }
        assertEquals(fuelId, fromFuel.sourceRecordId)
        assertEquals(120_450.0, odometer.currentKm(id)!!, 0.001)
    }

    @Test
    fun `editing a fill-up moves its odometer reading with it`() = runTest {
        val id = addVehicle()
        val fuelId = fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(3)),
                odometerKm = 120_450.0,
                volumeLitres = 42.5,
                totalCostMinor = 234_600,
            ),
        )

        fuel.update(fuel.find(fuelId)!!.copy(odometerKm = 120_500.0))

        assertEquals(120_500.0, odometer.currentKm(id)!!, 0.001)
    }

    @Test
    fun `deleting a fill-up takes its odometer reading and receipts with it`() = runTest {
        val id = addVehicle()
        val fuelId = fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(3)),
                odometerKm = 120_450.0,
                volumeLitres = 42.5,
                totalCostMinor = 234_600,
            ),
        )
        attachments.add(
            AttachmentEntity(
                ownerType = AttachmentOwner.FuelRecord,
                ownerId = fuelId,
                uri = "content://receipts/1",
                addedAt = start,
            ),
        )

        fuel.delete(fuelId)

        assertNull(fuel.find(fuelId))
        assertEquals(120_000.0, odometer.currentKm(id)!!, 0.001)
        assertTrue(attachments.observe(AttachmentOwner.FuelRecord, fuelId).first().isEmpty())
    }

    @Test
    fun `current mileage is the highest reading, not the most recent one`() = runTest {
        val id = addVehicle()
        fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(10)),
                odometerKm = 121_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 220_000,
            ),
        )
        // A fill-up entered late, from a week before the last one.
        fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(3)),
                odometerKm = 120_400.0,
                volumeLitres = 38.0,
                totalCostMinor = 209_000,
            ),
        )

        assertEquals(121_000.0, odometer.currentKm(id)!!, 0.001)
    }

    @Test
    fun `deleting a car removes its records and every attachment below it`() = runTest {
        val id = addVehicle()
        val fuelId = fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start,
                odometerKm = 120_100.0,
                volumeLitres = 40.0,
                totalCostMinor = 220_000,
            ),
        )
        val serviceId = service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = start,
                odometerKm = 120_100.0,
                title = "Oil change",
                labourCostMinor = 150_000,
                partsCostMinor = 320_000,
            ),
        )
        attachments.add(
            AttachmentEntity(
                ownerType = AttachmentOwner.FuelRecord,
                ownerId = fuelId,
                uri = "content://receipts/fuel",
                addedAt = start,
            ),
        )
        attachments.add(
            AttachmentEntity(
                ownerType = AttachmentOwner.ServiceRecord,
                ownerId = serviceId,
                uri = "content://receipts/service",
                addedAt = start,
            ),
        )
        attachments.add(
            AttachmentEntity(
                ownerType = AttachmentOwner.Vehicle,
                ownerId = id,
                uri = "content://photos/car",
                addedAt = start,
            ),
        )

        vehicles.delete(id)

        assertNull(vehicles.find(id))
        assertTrue(fuel.observe(id).first().isEmpty())
        assertTrue(service.observeRecords(id).first().isEmpty())
        assertTrue(odometer.observe(id).first().isEmpty())
        assertTrue(attachments.observe(AttachmentOwner.FuelRecord, fuelId).first().isEmpty())
        assertTrue(attachments.observe(AttachmentOwner.ServiceRecord, serviceId).first().isEmpty())
        assertTrue(attachments.observe(AttachmentOwner.Vehicle, id).first().isEmpty())
    }

    @Test
    fun `booking a job against a plan item resets its interval`() = runTest {
        val id = addVehicle()
        val scheduleId = service.addSchedule(
            MaintenanceScheduleEntity(
                vehicleId = id,
                title = "Engine oil",
                intervalKm = 10_000.0,
                intervalMonths = 12,
            ),
        )

        val performedAt = start.plus(Duration.ofDays(20))
        service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = performedAt,
                odometerKm = 120_800.0,
                title = "Engine oil and filter",
                scheduleId = scheduleId,
            ),
        )

        val schedule = service.findSchedule(scheduleId)!!
        assertEquals(performedAt, schedule.lastPerformedAt)
        assertEquals(120_800.0, schedule.lastPerformedOdometerKm!!, 0.001)
    }

    @Test
    fun `removing the only job against a plan item clears its last-performed mark`() = runTest {
        val id = addVehicle()
        val scheduleId = service.addSchedule(
            MaintenanceScheduleEntity(vehicleId = id, title = "Engine oil", intervalKm = 10_000.0),
        )
        val recordId = service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = start.plus(Duration.ofDays(20)),
                odometerKm = 120_800.0,
                title = "Engine oil and filter",
                scheduleId = scheduleId,
            ),
        )

        service.deleteRecord(recordId)

        val schedule = service.findSchedule(scheduleId)!!
        assertNull(schedule.lastPerformedAt)
        assertNull(schedule.lastPerformedOdometerKm)
    }

    @Test
    fun `removing the newer of two jobs falls back to the older one`() = runTest {
        val id = addVehicle()
        val scheduleId = service.addSchedule(
            MaintenanceScheduleEntity(vehicleId = id, title = "Engine oil", intervalKm = 10_000.0),
        )
        val older = start.plus(Duration.ofDays(20))
        service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = older,
                odometerKm = 120_800.0,
                title = "Oil",
                scheduleId = scheduleId,
            ),
        )
        val newerId = service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = start.plus(Duration.ofDays(400)),
                odometerKm = 131_000.0,
                title = "Oil",
                scheduleId = scheduleId,
            ),
        )

        service.deleteRecord(newerId)

        val schedule = service.findSchedule(scheduleId)!!
        assertEquals(older, schedule.lastPerformedAt)
        assertEquals(120_800.0, schedule.lastPerformedOdometerKm!!, 0.001)
    }

    @Test
    fun `deleting a plan item leaves the jobs booked against it in the history`() = runTest {
        val id = addVehicle()
        val scheduleId = service.addSchedule(
            MaintenanceScheduleEntity(vehicleId = id, title = "Engine oil", intervalKm = 10_000.0),
        )
        service.addRecord(
            ServiceRecordEntity(
                vehicleId = id,
                performedAt = start,
                odometerKm = 120_100.0,
                title = "Oil",
                scheduleId = scheduleId,
            ),
        )

        service.deleteSchedule(service.findSchedule(scheduleId)!!)

        val records = service.observeRecords(id).first()
        assertEquals(1, records.size)
        assertNull(records.single().scheduleId)
    }

    @Test
    fun `spending is grouped by category`() = runTest {
        val id = addVehicle()
        expenses.add(
            ExpenseEntity(
                vehicleId = id,
                incurredAt = start,
                category = ExpenseCategory.Insurance,
                title = "OSAGO",
                amountMinor = 850_000,
            ),
        )
        expenses.add(
            ExpenseEntity(
                vehicleId = id,
                incurredAt = start.plus(Duration.ofDays(5)),
                category = ExpenseCategory.Tyres,
                title = "Winter set",
                amountMinor = 3_200_000,
            ),
        )
        expenses.add(
            ExpenseEntity(
                vehicleId = id,
                incurredAt = start.plus(Duration.ofDays(6)),
                category = ExpenseCategory.Insurance,
                title = "KASKO",
                amountMinor = 4_100_000,
            ),
        )

        val totals = expenses.observeTotalsByCategory(
            vehicleId = id,
            fromEpochMilli = start.minus(Duration.ofDays(1)).toEpochMilli(),
            toEpochMilli = start.plus(Duration.ofDays(30)).toEpochMilli(),
        ).first()

        assertEquals(2, totals.size)
        assertEquals(ExpenseCategory.Insurance, totals.first().category)
        assertEquals(4_950_000L, totals.first().totalMinor)
        assertEquals(3_200_000L, totals.last().totalMinor)
    }

    @Test
    fun `spending outside the window is left out`() = runTest {
        val id = addVehicle()
        expenses.add(
            ExpenseEntity(
                vehicleId = id,
                incurredAt = start.minus(Duration.ofDays(400)),
                category = ExpenseCategory.Tax,
                title = "Last year",
                amountMinor = 500_000,
            ),
        )

        val spend = expenses.observeSpendBetween(
            vehicleId = id,
            fromEpochMilli = start.toEpochMilli(),
            toEpochMilli = start.plus(Duration.ofDays(30)).toEpochMilli(),
        ).first()

        assertEquals(0L, spend)
    }

    @Test
    fun `a hand-entered reading can be deleted but a derived one cannot`() = runTest {
        val id = addVehicle()
        val manual = OdometerReadingEntity(
            vehicleId = id,
            recordedAt = start.plus(Duration.ofDays(1)),
            odometerKm = 120_050.0,
        )
        val manualId = odometer.add(manual)
        fuel.add(
            FuelRecordEntity(
                vehicleId = id,
                filledAt = start.plus(Duration.ofDays(2)),
                odometerKm = 120_200.0,
                volumeLitres = 40.0,
                totalCostMinor = 220_000,
            ),
        )

        odometer.delete(manual.copy(id = manualId))
        val derived = odometer.observe(id).first().single { it.source == OdometerSource.FuelRecord }
        val failure = runCatching { odometer.delete(derived) }.exceptionOrNull()

        assertEquals(2, odometer.observe(id).first().size)
        assertTrue("expected a rejection, got $failure", failure is IllegalArgumentException)
    }

    @Test
    fun `a cached VIN comes back as it was stored`() = runTest {
        vinCache.put(
            VinCacheEntryEntity(
                vin = "wvwzzz1jz3w386752",
                payloadJson = """{"Make":"VOLKSWAGEN"}""",
                fetchedAt = start,
            ),
        )

        val entry = vinCache.find("WVWZZZ1JZ3W386752")

        assertNotNull(entry)
        assertEquals("""{"Make":"VOLKSWAGEN"}""", entry!!.payloadJson)
    }

    @Test
    fun `an empty vPIC answer is retried after a week but a real one is not`() = runTest {
        vinCache.put(
            VinCacheEntryEntity(
                vin = "XTA21099062546789",
                payloadJson = "{}",
                fetchedAt = start,
                isEmptyResult = true,
            ),
        )
        vinCache.put(
            VinCacheEntryEntity(
                vin = "WVWZZZ1JZ3W386752",
                payloadJson = """{"Make":"VOLKSWAGEN"}""",
                fetchedAt = start,
            ),
        )

        val later = start.plus(Duration.ofDays(8))

        assertNull(vinCache.find("XTA21099062546789", now = later))
        assertNotNull(vinCache.find("WVWZZZ1JZ3W386752", now = later))
        assertNotNull(vinCache.find("XTA21099062546789", now = start.plus(Duration.ofDays(2))))
    }

    @Test
    fun `adding backdated service record keeps the latest record date on schedule`() = runTest {
        val vehicleId = addVehicle()
        val scheduleId = service.addSchedule(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Oil change",
                category = com.cargenome.app.data.db.entity.ServiceCategory.RoutineService,
                intervalKm = 10_000.0,
            ),
        )

        val newerDate = start.plus(Duration.ofDays(60))
        val olderDate = start.plus(Duration.ofDays(10))

        // First add the newer record
        service.addRecord(
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = newerDate,
                odometerKm = 130_000.0,
                category = com.cargenome.app.data.db.entity.ServiceCategory.RoutineService,
                title = "Oil change 2",
                labourCostMinor = 1000,
                partsCostMinor = 2000,
                scheduleId = scheduleId,
            ),
        )

        var schedule = service.findSchedule(scheduleId)!!
        assertEquals(newerDate, schedule.lastPerformedAt)
        assertEquals(130_000.0, schedule.lastPerformedOdometerKm!!, 0.001)

        // Now add backdated record
        service.addRecord(
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = olderDate,
                odometerKm = 125_000.0,
                category = com.cargenome.app.data.db.entity.ServiceCategory.RoutineService,
                title = "Oil change 1",
                labourCostMinor = 1000,
                partsCostMinor = 2000,
                scheduleId = scheduleId,
            ),
        )

        // Schedule must still reflect the newer record (130_000 km, newerDate)
        schedule = service.findSchedule(scheduleId)!!
        assertEquals(newerDate, schedule.lastPerformedAt)
        assertEquals(130_000.0, schedule.lastPerformedOdometerKm!!, 0.001)
    }

    @Test
    fun `vehicle summary reflects initial odometer when no readings exist`() = runTest {
        val vId = database.vehicleDao().insert(
            VehicleEntity(
                make = "Lada",
                model = "Vesta",
                initialOdometerKm = 45_000.0,
            ),
        )
        val summaries = database.vehicleDao().observeSummaries().first()
        val summary = summaries.first { it.vehicle.id == vId }
        assertEquals(45_000.0, summary.currentOdometerKm!!, 0.001)
    }

    @Test
    fun `deleting vehicle cascades to maintenance events and attachments`() = runTest {
        val vehicleId = addVehicle()

        val eventId = database.maintenanceEventDao().insert(
            MaintenanceEventEntity(
                vehicleId = vehicleId,
                title = "Replace brake pads",
                category = com.cargenome.app.data.db.entity.ServiceCategory.Brakes,
                scheduledDate = LocalDate.now().plusDays(7),
                remindAdvanceDays = 3,
            ),
        )
        assertNotNull(database.maintenanceEventDao().findById(eventId))

        attachments.add(
            AttachmentEntity(
                ownerType = AttachmentOwner.Vehicle,
                ownerId = vehicleId,
                uri = "content://test/vehicle_insurance.pdf",
                displayName = "insurance.pdf",
                mimeType = "application/pdf",
                sizeBytes = 1024,
                addedAt = Instant.now(),
            ),
        )
        assertEquals(1, database.attachmentDao().listForOwner(AttachmentOwner.Vehicle, vehicleId).size)

        // Delete the vehicle
        vehicles.delete(vehicleId)

        // Verify vehicle is gone
        assertNull(vehicles.find(vehicleId))
        // Verify maintenance events for that vehicle are cascaded and deleted
        assertNull(database.maintenanceEventDao().findById(eventId))
        assertEquals(0, database.maintenanceEventDao().listForVehicle(vehicleId).size)
        // Verify attachments are deleted
        assertEquals(0, database.attachmentDao().listForOwner(AttachmentOwner.Vehicle, vehicleId).size)
    }

    @Test
    fun `changing service record schedule refreshes both old and new schedules`() = runTest {
        val vehicleId = addVehicle()

        val schedule1Id = service.addSchedule(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Oil Change",
                intervalKm = 10_000.0,
            ),
        )
        val schedule2Id = service.addSchedule(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Spark Plugs",
                intervalKm = 30_000.0,
            ),
        )

        val recordDate = start.plus(Duration.ofDays(10))
        val recordId = service.addRecord(
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = recordDate,
                odometerKm = 125_000.0,
                category = com.cargenome.app.data.db.entity.ServiceCategory.RoutineService,
                title = "Service job",
                labourCostMinor = 1000,
                partsCostMinor = 2000,
                scheduleId = schedule1Id,
            ),
        )

        assertEquals(125_000.0, service.findSchedule(schedule1Id)!!.lastPerformedOdometerKm!!, 0.001)
        assertNull(service.findSchedule(schedule2Id)!!.lastPerformedOdometerKm)

        // Now update record to link to schedule2 instead
        val record = service.findRecord(recordId)!!
        service.updateRecord(record.copy(scheduleId = schedule2Id))

        // Old schedule1 must have its lastPerformed reset to null
        assertNull(service.findSchedule(schedule1Id)!!.lastPerformedOdometerKm)
        assertNull(service.findSchedule(schedule1Id)!!.lastPerformedAt)

        // New schedule2 must have its lastPerformed updated
        assertEquals(125_000.0, service.findSchedule(schedule2Id)!!.lastPerformedOdometerKm!!, 0.001)
        assertEquals(recordDate, service.findSchedule(schedule2Id)!!.lastPerformedAt)
    }

    @Test
    fun `updating fuel record synchronizes odometer reading`() = runTest {
        val vehicleId = addVehicle()
        val fillDate = start.plus(Duration.ofDays(5))

        val fuelId = fuel.add(
            FuelRecordEntity(
                vehicleId = vehicleId,
                filledAt = fillDate,
                odometerKm = 121_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 250_000L,
            ),
        )

        assertEquals(121_000.0, odometer.currentKm(vehicleId)!!, 0.001)

        // Update fuel record with corrected mileage
        val record = fuel.find(fuelId)!!
        fuel.update(record.copy(odometerKm = 122_500.0))

        assertEquals(122_500.0, odometer.currentKm(vehicleId)!!, 0.001)
    }

    @Test
    fun `completing maintenance event creates service record with non-negative costs`() = runTest {
        val vehicleId = addVehicle()
        val eventDate = LocalDate.now().minusDays(2)

        val eventId = service.addEvent(
            MaintenanceEventEntity(
                vehicleId = vehicleId,
                title = "Change coolant",
                category = com.cargenome.app.data.db.entity.ServiceCategory.RoutineService,
                scheduledDate = eventDate,
                targetOdometerKm = 124_000.0,
            ),
        )

        val createdRecordId = service.completeEvent(
            eventId = eventId,
            createServiceRecord = true,
            actualOdometerKm = 124_500.0,
            labourCostMinor = 1500L,
            partsCostMinor = 2500L,
            shop = "Master Garage",
            notes = "Replaced with G12+",
        )

        assertNotNull(createdRecordId)
        val event = service.findEvent(eventId)!!
        assertTrue(event.isCompleted)
        assertEquals(createdRecordId, event.serviceRecordId)

        val record = service.findRecord(createdRecordId!!)!!
        assertEquals("Change coolant", record.title)
        assertEquals(124_500.0, record.odometerKm!!, 0.001)
        assertEquals(1500L, record.labourCostMinor)
        assertEquals(2500L, record.partsCostMinor)
        assertEquals("Master Garage", record.shop)
    }
}
