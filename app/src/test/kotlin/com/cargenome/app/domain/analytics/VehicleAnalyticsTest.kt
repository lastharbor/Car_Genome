package com.cargenome.app.domain.analytics

import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAnalyticsTest {

    private val baseTime = Instant.parse("2026-01-01T12:00:00Z")

    private val vehicle = VehicleEntity(
        id = 1,
        make = "Volkswagen",
        model = "Golf",
        fuelType = FuelType.Petrol,
        distanceUnit = DistanceUnit.Kilometres,
        volumeUnit = VolumeUnit.Litres,
        currencyCode = "EUR",
        initialOdometerKm = 100_000.0,
    )

    @Test
    fun `empty vehicle records yield zero spend and null cost per distance`() {
        val result = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = emptyList(),
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 100_000.0,
        )

        assertEquals(0L, result.totalSpendMinor)
        assertEquals(0L, result.fuelSpendMinor)
        assertEquals(0.0, result.trackedDistanceKm, 0.001)
        assertEquals(null, result.totalCostPerKmMinor)
    }

    @Test
    fun `calculates totals and cost per km correctly across fuel, service and expenses`() {
        val fuels = listOf(
            FuelRecordEntity(
                id = 1,
                vehicleId = 1,
                filledAt = baseTime,
                odometerKm = 100_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 8_000,
                isFullTank = true,
            ),
            FuelRecordEntity(
                id = 2,
                vehicleId = 1,
                filledAt = baseTime.plusSeconds(86400 * 7),
                odometerKm = 100_500.0,
                volumeLitres = 40.0,
                totalCostMinor = 8_000,
                isFullTank = true,
            ),
        )

        val services = listOf(
            ServiceRecordEntity(
                id = 1,
                vehicleId = 1,
                performedAt = baseTime.plusSeconds(86400 * 10),
                odometerKm = 100_700.0,
                category = ServiceCategory.RoutineService,
                title = "Oil Change",
                labourCostMinor = 5_000,
                partsCostMinor = 5_000,
            ),
        )

        val expenses = listOf(
            ExpenseEntity(
                id = 1,
                vehicleId = 1,
                incurredAt = baseTime.plusSeconds(86400 * 12),
                category = ExpenseCategory.Insurance,
                title = "Insurance",
                amountMinor = 14_000,
            ),
        )

        val result = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = services,
            expenses = expenses,
            currentOdometerKm = 101_000.0, // 1000 km tracked
        )

        // Totals: fuel = 16_000, service = 10_000, expense = 14_000, total = 40_000 minor
        assertEquals(40_000L, result.totalSpendMinor)
        assertEquals(16_000L, result.fuelSpendMinor)
        assertEquals(10_000L, result.serviceSpendMinor)
        assertEquals(14_000L, result.otherSpendMinor)

        // Tracked distance = 101_000 - 100_000 = 1000 km
        assertEquals(1_000.0, result.trackedDistanceKm, 0.001)

        // Cost per km = 40_000 / 1000 = 40.0 minor units per km
        assertEquals(40.0, result.totalCostPerKmMinor!!, 0.001)

        // Fuel consumption: 40L over 500km = 8.0 L/100km
        assertEquals(8.0, result.averageConsumption!!, 0.001)

        // Categories
        assertEquals(3, result.categorySpends.size)
        assertTrue(result.categorySpends.any { it.key == "fuel" && it.percentage == 40.0f })
        assertTrue(result.categorySpends.any { it.key == "insurance" && it.percentage == 35.0f })
        assertTrue(result.categorySpends.any { it.key == "service" && it.percentage == 25.0f })
    }

    @Test
    fun `tracked distance takes true maximum when record odometer exceeds currentOdometerKm`() {
        val fuels = listOf(
            FuelRecordEntity(
                id = 1,
                vehicleId = 1,
                filledAt = baseTime,
                odometerKm = 105_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 5_000,
                isFullTank = true,
            ),
        )

        val result = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 102_000.0, // Stale / lower than record
        )

        // Min is initialOdometerKm = 100_000.0, Max should be 105_000.0 (from fuel record)
        assertEquals(5_000.0, result.trackedDistanceKm, 0.001)
    }

    @Test
    fun `filters records correctly by time range`() {
        val now = Instant.parse("2026-07-01T12:00:00Z")
        val fuels = listOf(
            FuelRecordEntity(
                id = 1,
                vehicleId = 1,
                filledAt = now.minusSeconds(86400L * 400), // > 1 year ago
                odometerKm = 100_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 5_000,
                isFullTank = true,
            ),
            FuelRecordEntity(
                id = 2,
                vehicleId = 1,
                filledAt = now.minusSeconds(86400L * 150), // within 6 months
                odometerKm = 102_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 6_000,
                isFullTank = true,
            ),
            FuelRecordEntity(
                id = 3,
                vehicleId = 1,
                filledAt = now.minusSeconds(86400L * 30), // within 3 months
                odometerKm = 104_000.0,
                volumeLitres = 40.0,
                totalCostMinor = 7_000,
                isFullTank = true,
            ),
        )

        val allTimeResult = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.ALL_TIME,
            now = now,
        )
        assertEquals(18_000L, allTimeResult.totalSpendMinor)
        assertEquals(3, allTimeResult.totalEntriesCount)

        val months3Result = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.MONTHS_3,
            now = now,
        )
        assertEquals(7_000L, months3Result.totalSpendMinor)
        assertEquals(1, months3Result.totalEntriesCount)

        val months6Result = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.MONTHS_6,
            now = now,
        )
        assertEquals(13_000L, months6Result.totalSpendMinor)
        assertEquals(2, months6Result.totalEntriesCount)

        val thisMonthResult = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.THIS_MONTH,
            now = now,
            zoneId = ZoneOffset.UTC,
        )
        // Fuel 3 was filled 30 days before July 1 (June 1), not in July
        assertEquals(0L, thisMonthResult.totalSpendMinor)

        val thisYearResult = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.THIS_YEAR,
            now = now,
            zoneId = ZoneOffset.UTC,
        )
        // Fuels in 2026: Fuel 2 (150 days prior ~ Feb 2026) and Fuel 3 (30 days prior ~ June 2026)
        assertEquals(13_000L, thisYearResult.totalSpendMinor)
        assertEquals(2, thisYearResult.totalEntriesCount)

        val customResult = VehicleAnalyticsCalculator.calculate(
            vehicle = vehicle,
            fuelRecords = fuels,
            serviceRecords = emptyList(),
            expenses = emptyList(),
            currentOdometerKm = 104_000.0,
            timeRange = AnalyticsTimeRange.CUSTOM,
            customStartDate = LocalDate.of(2026, 1, 1),
            customEndDate = LocalDate.of(2026, 6, 15),
            now = now,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(13_000L, customResult.totalSpendMinor)
        assertEquals(2, customResult.totalEntriesCount)
    }
}
