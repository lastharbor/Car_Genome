package com.cargenome.app.data.demo

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
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: CarGenomeDatabase,
    private val fuelRepo: FuelRepository,
    private val serviceRepo: ServiceRepository,
    private val settingsRepo: AppSettingsRepository,
) {

    suspend fun seedDemoVehicle(): Long = db.withTransaction {
        val now = Instant.now()

        val vehicle = VehicleEntity(
            make = "BMW",
            model = "3 Series 330i xDrive (G20)",
            modelYear = 2021,
            trim = "M Sport Pro",
            engine = "2.0 L B48 Turbo (258 л.с.)",
            fuelType = FuelType.Petrol,
            plateNumber = "А 777 АА 777",
            nickname = "Черный бумер",
            distanceUnit = DistanceUnit.Kilometres,
            volumeUnit = VolumeUnit.Litres,
            currencyCode = "RUB",
            initialOdometerKm = 42000.0,
            purchasedOn = LocalDate.now().minusMonths(6),
            vin = "WBA5R1103MFL98765",
            createdAt = now.minus(180, ChronoUnit.DAYS),
            isArchived = false,
        )
        val vehicleId = db.vehicleDao().insert(vehicle)

        // Initial odometer reading
        db.odometerReadingDao().insert(
            OdometerReadingEntity(
                vehicleId = vehicleId,
                recordedAt = now.minus(180, ChronoUnit.DAYS),
                odometerKm = 42000.0,
                source = OdometerSource.Manual,
                note = "Начальный пробег при покупке",
            ),
        )

        // Maintenance schedules
        val oilScheduleId = db.maintenanceScheduleDao().insert(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Моторное масло и фильтр",
                category = ServiceCategory.RoutineService,
                intervalKm = 8000.0,
                intervalMonths = 12,
                warnBeforeKm = 1000.0,
                warnBeforeDays = 30,
                lastPerformedAt = now.minus(15, ChronoUnit.DAYS),
                lastPerformedOdometerKm = 48800.0,
                notes = "BMW Longlife-01 / LL-04 5W-30 (5.25 л)",
            ),
        )

        val airFilterScheduleId = db.maintenanceScheduleDao().insert(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Воздушный фильтр двигателя",
                category = ServiceCategory.Engine,
                intervalKm = 15000.0,
                intervalMonths = 12,
                warnBeforeKm = 1500.0,
                warnBeforeDays = 30,
                lastPerformedAt = now.minus(95, ChronoUnit.DAYS),
                lastPerformedOdometerKm = 45500.0,
            ),
        )

        db.maintenanceScheduleDao().insert(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Салонный фильтр (угольный)",
                category = ServiceCategory.RoutineService,
                intervalKm = 15000.0,
                intervalMonths = 12,
                warnBeforeKm = 1500.0,
                warnBeforeDays = 30,
                lastPerformedAt = now.minus(95, ChronoUnit.DAYS),
                lastPerformedOdometerKm = 45500.0,
            ),
        )

        db.maintenanceScheduleDao().insert(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Тормозная жидкость DOT 4 LV",
                category = ServiceCategory.Brakes,
                intervalKm = 40000.0,
                intervalMonths = 24,
                warnBeforeKm = 2000.0,
                warnBeforeDays = 45,
                lastPerformedAt = now.minus(140, ChronoUnit.DAYS),
                lastPerformedOdometerKm = 43000.0,
            ),
        )

        db.maintenanceScheduleDao().insert(
            MaintenanceScheduleEntity(
                vehicleId = vehicleId,
                title = "Свечи зажигания (иридиевые)",
                category = ServiceCategory.Engine,
                intervalKm = 40000.0,
                intervalMonths = 36,
                warnBeforeKm = 2000.0,
                warnBeforeDays = 45,
                lastPerformedAt = now.minus(140, ChronoUnit.DAYS),
                lastPerformedOdometerKm = 43000.0,
            ),
        )

        // 11 fuel fill-ups (chronological)
        val fillUps = listOf(
            Triple(160L, 42650.0, Triple(52.3, 324260L, "Лукойл")),
            Triple(145L, 43320.0, Triple(54.1, 335420L, "Газпромнефть")),
            Triple(130L, 43980.0, Triple(51.8, 323750L, "Роснефть")),
            Triple(115L, 44640.0, Triple(53.0, 333900L, "Лукойл")),
            Triple(100L, 45310.0, Triple(55.2, 347760L, "Газпромнефть")),
            Triple(85L, 45990.0, Triple(52.4, 335360L, "Татнефть")),
            Triple(70L, 46680.0, Triple(53.8, 344320L, "Teboil")),
            Triple(52L, 47350.0, Triple(51.5, 332175L, "Лукойл")),
            Triple(35L, 48020.0, Triple(54.6, 352170L, "Роснефть")),
            Triple(18L, 48700.0, Triple(53.2, 345800L, "Газпромнефть")),
            Triple(3L, 49420.0, Triple(52.0, 338000L, "Лукойл")),
        )

        for ((daysAgo, odo, data) in fillUps) {
            val (vol, costMinor, station) = data
            val record = FuelRecordEntity(
                vehicleId = vehicleId,
                filledAt = now.minus(daysAgo, ChronoUnit.DAYS),
                odometerKm = odo,
                volumeLitres = vol,
                totalCostMinor = costMinor,
                station = station,
                isFullTank = true,
                missedPreviousFillUp = false,
                notes = "АИ-95 Премиум",
            )
            fuelRepo.add(record)
        }

        // Service records
        val services = listOf(
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = now.minus(140, ChronoUnit.DAYS),
                odometerKm = 43000.0,
                category = ServiceCategory.RoutineService,
                title = "Регламентное ТО: масло двигателя и фильтр",
                labourCostMinor = 350000L,
                partsCostMinor = 820000L,
                shop = "BMW Сервис",
                notes = "Масло BMW TwinPower Turbo 5W-30 LL-01, масляный фильтр MANN HU816x",
                scheduleId = oilScheduleId,
            ),
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = now.minus(95, ChronoUnit.DAYS),
                odometerKm = 45500.0,
                category = ServiceCategory.Engine,
                title = "Замена воздушного и салонного фильтров",
                labourCostMinor = 120000L,
                partsCostMinor = 480000L,
                shop = "СТО Авторитет",
                notes = "Фильтр салона антиаллергенный угольный, воздушный фильтр Mahle",
                scheduleId = airFilterScheduleId,
            ),
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = now.minus(75, ChronoUnit.DAYS),
                odometerKm = 46500.0,
                category = ServiceCategory.Brakes,
                title = "Замена передних тормозных колодок",
                labourCostMinor = 250000L,
                partsCostMinor = 950000L,
                shop = "BMW Сервис",
                notes = "Колодки Brembo, новый датчик износа",
            ),
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = now.minus(40, ChronoUnit.DAYS),
                odometerKm = 48000.0,
                category = ServiceCategory.Tyres,
                title = "Сезонный шиномонтаж и балансировка R19",
                labourCostMinor = 380000L,
                partsCostMinor = 0L,
                shop = "Шиномонтаж 24",
                notes = "Переобувка на летний комплект Michelin Pilot Sport 4S",
            ),
            ServiceRecordEntity(
                vehicleId = vehicleId,
                performedAt = now.minus(15, ChronoUnit.DAYS),
                odometerKm = 48800.0,
                category = ServiceCategory.RoutineService,
                title = "Замена моторного масла и фильтра",
                labourCostMinor = 300000L,
                partsCostMinor = 780000L,
                shop = "СТО Авторитет",
                notes = "Масло Motul 8100 X-cess 5W-40, оригинальный фильтр",
                scheduleId = oilScheduleId,
            ),
        )

        for (s in services) {
            serviceRepo.addRecord(s)
        }

        // Expenses
        val expenses = listOf(
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(175, ChronoUnit.DAYS),
                category = ExpenseCategory.Insurance,
                title = "Страховка ОСАГО",
                amountMinor = 850000L,
                odometerKm = 42050.0,
                notes = "Полис АльфаСтрахование на 1 год",
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(175, ChronoUnit.DAYS),
                category = ExpenseCategory.Insurance,
                title = "Страховка КАСКО",
                amountMinor = 6500000L,
                odometerKm = 42050.0,
                notes = "Полное КАСКО без франшизы",
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(135, ChronoUnit.DAYS),
                category = ExpenseCategory.Wash,
                title = "Комплексная мойка и гидрофоб",
                amountMinor = 220000L,
                odometerKm = 43700.0,
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(110, ChronoUnit.DAYS),
                category = ExpenseCategory.Toll,
                title = "Платная трасса М-11 'Нева'",
                amountMinor = 285000L,
                odometerKm = 44900.0,
                notes = "Транспондер T-Pass",
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(80, ChronoUnit.DAYS),
                category = ExpenseCategory.Parking,
                title = "Парковка в аэропорту",
                amountMinor = 180000L,
                odometerKm = 46200.0,
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(45, ChronoUnit.DAYS),
                category = ExpenseCategory.Wash,
                title = "Мойка самообслуживания",
                amountMinor = 50000L,
                odometerKm = 47700.0,
            ),
            ExpenseEntity(
                vehicleId = vehicleId,
                incurredAt = now.minus(25, ChronoUnit.DAYS),
                category = ExpenseCategory.Accessories,
                title = "Омывающая жидкость и автохимия",
                amountMinor = 160000L,
                odometerKm = 48400.0,
            ),
        )

        for (e in expenses) {
            db.expenseDao().insert(e)
            e.odometerKm?.let { km ->
                db.odometerReadingDao().insert(
                    OdometerReadingEntity(
                        vehicleId = vehicleId,
                        recordedAt = e.incurredAt,
                        odometerKm = km,
                        source = OdometerSource.Expense,
                    ),
                )
            }
        }

        // Calendar maintenance events
        val demoEvents = listOf(
            MaintenanceEventEntity(
                vehicleId = vehicleId,
                title = "Сезонный шиномонтаж (зима)",
                category = ServiceCategory.Tyres,
                scheduledDate = LocalDate.now().plusDays(20),
                scheduledTimeMinutes = 11 * 60,
                targetOdometerKm = 50000.0,
                estimatedCostMinor = 400000L,
                shop = "Шиномонтаж 24",
                notes = "Зимний комплект R19 на дисках",
                remindAdvanceDays = 1,
            ),
            MaintenanceEventEntity(
                vehicleId = vehicleId,
                title = "Диагностика подвески перед зимой",
                category = ServiceCategory.Suspension,
                scheduledDate = LocalDate.now().plusDays(35),
                scheduledTimeMinutes = 14 * 60 + 30,
                estimatedCostMinor = 250000L,
                shop = "BMW Сервис",
                notes = "Проверка сайлентблоков и амортизаторов",
                remindAdvanceDays = 3,
            ),
        )
        for (event in demoEvents) {
            serviceRepo.addEvent(event)
        }

        settingsRepo.setSelectedVehicleId(vehicleId)
        vehicleId
    }
}
