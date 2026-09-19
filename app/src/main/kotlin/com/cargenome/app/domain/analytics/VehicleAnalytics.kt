package com.cargenome.app.domain.analytics

import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.totalCostMinor
import com.cargenome.app.domain.fuel.FuelConsumption
import com.cargenome.app.domain.model.ConsumptionUnit
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class AnalyticsTimeRange {
    ALL_TIME,
    YEAR_1,
    MONTHS_6,
    MONTHS_3,
}

@Immutable
data class CategorySpend(
    val key: String,
    val amountMinor: Long,
    val percentage: Float,
    val count: Int = 0,
)

@Immutable
data class MonthlySpend(
    val yearMonth: YearMonth,
    val amountMinor: Long,
    val count: Int = 0,
    val percentageOfTotal: Float = 0f,
)

@Immutable
data class ConsumptionPoint(
    val date: Instant,
    val consumptionValue: Double,
    val odometerKm: Double? = null,
    val deltaFromAverage: Double? = null,
)

@Immutable
data class VehicleAnalyticsData(
    val totalSpendMinor: Long = 0,
    val fuelSpendMinor: Long = 0,
    val serviceSpendMinor: Long = 0,
    val otherSpendMinor: Long = 0,
    val trackedDistanceKm: Double = 0.0,
    val totalCostPerKmMinor: Double? = null,
    val fuelCostPerKmMinor: Double? = null,
    val averageConsumption: Double? = null,
    val bestConsumption: Double? = null,
    val worstConsumption: Double? = null,
    val averageMonthlySpendMinor: Long = 0,
    val costPerDayMinor: Long? = null,
    val categorySpends: List<CategorySpend> = emptyList(),
    val monthlySpends: List<MonthlySpend> = emptyList(),
    val consumptionHistory: List<ConsumptionPoint> = emptyList(),
    val totalEntriesCount: Int = 0,
)

object VehicleAnalyticsCalculator {

    fun calculate(
        vehicle: VehicleEntity,
        fuelRecords: List<FuelRecordEntity>,
        serviceRecords: List<ServiceRecordEntity>,
        expenses: List<ExpenseEntity>,
        currentOdometerKm: Double?,
        timeRange: AnalyticsTimeRange = AnalyticsTimeRange.ALL_TIME,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): VehicleAnalyticsData {
        val cutoff: Instant? = when (timeRange) {
            AnalyticsTimeRange.ALL_TIME -> null
            AnalyticsTimeRange.YEAR_1 -> now.minus(365, java.time.temporal.ChronoUnit.DAYS)
            AnalyticsTimeRange.MONTHS_6 -> now.minus(183, java.time.temporal.ChronoUnit.DAYS)
            AnalyticsTimeRange.MONTHS_3 -> now.minus(92, java.time.temporal.ChronoUnit.DAYS)
        }

        val filteredFuels = if (cutoff == null) fuelRecords else fuelRecords.filter { !it.filledAt.isBefore(cutoff) }
        val filteredServices = if (cutoff == null) serviceRecords else serviceRecords.filter { !it.performedAt.isBefore(cutoff) }
        val filteredExpenses = if (cutoff == null) expenses else expenses.filter { !it.incurredAt.isBefore(cutoff) }

        val fuelStats = FuelConsumption.analyse(filteredFuels)
        val fuelSpend = filteredFuels.sumOf { it.totalCostMinor }
        val serviceSpend = filteredServices.sumOf { it.totalCostMinor }
        val otherSpend = filteredExpenses.sumOf { it.amountMinor }
        val totalSpend = fuelSpend + serviceSpend + otherSpend
        val totalEntries = filteredFuels.size + filteredServices.size + filteredExpenses.size

        val minOdo = if (timeRange == AnalyticsTimeRange.ALL_TIME) {
            listOfNotNull(
                vehicle.initialOdometerKm.takeIf { it > 0.0 },
                filteredFuels.minOfOrNull { it.odometerKm },
                filteredServices.mapNotNull { it.odometerKm }.minOrNull(),
                filteredExpenses.mapNotNull { it.odometerKm }.minOrNull(),
            ).minOrNull() ?: 0.0
        } else {
            listOfNotNull(
                filteredFuels.minOfOrNull { it.odometerKm },
                filteredServices.mapNotNull { it.odometerKm }.minOrNull(),
                filteredExpenses.mapNotNull { it.odometerKm }.minOrNull(),
            ).minOrNull() ?: (vehicle.initialOdometerKm.takeIf { it > 0.0 } ?: 0.0)
        }

        val maxOdo = listOfNotNull(
            currentOdometerKm,
            filteredFuels.maxOfOrNull { it.odometerKm },
            filteredServices.mapNotNull { it.odometerKm }.maxOrNull(),
            filteredExpenses.mapNotNull { it.odometerKm }.maxOrNull(),
            minOdo,
        ).maxOrNull() ?: minOdo

        val trackedDistance = (maxOdo - minOdo).coerceAtLeast(0.0)

        val totalCostPerKm = if (trackedDistance > 0.0 && totalSpend > 0) {
            totalSpend.toDouble() / trackedDistance
        } else {
            null
        }

        val fuelCostPerKm = fuelStats.costPerKmMinor

        val unit = vehicle.consumptionUnit()
        val avgConsumption = fuelStats.averageLitresPer100Km?.let(unit::fromLitresPer100Km)
        val bestConsumption = fuelStats.bestLitresPer100Km?.let(unit::fromLitresPer100Km)
        val worstConsumption = fuelStats.worstLitresPer100Km?.let(unit::fromLitresPer100Km)

        // Spending by category breakdown with counts
        val rawCategoryMap = mutableMapOf<String, Long>()
        val rawCategoryCountMap = mutableMapOf<String, Int>()

        if (fuelSpend > 0) {
            rawCategoryMap["fuel"] = fuelSpend
            rawCategoryCountMap["fuel"] = filteredFuels.size
        }
        if (serviceSpend > 0) {
            rawCategoryMap["service"] = serviceSpend
            rawCategoryCountMap["service"] = filteredServices.size
        }
        filteredExpenses.groupBy { it.category.name.lowercase() }.forEach { (cat, list) ->
            rawCategoryMap[cat] = (rawCategoryMap[cat] ?: 0L) + list.sumOf { it.amountMinor }
            rawCategoryCountMap[cat] = (rawCategoryCountMap[cat] ?: 0) + list.size
        }

        val categorySpends = if (totalSpend > 0) {
            rawCategoryMap.map { (cat, amount) ->
                CategorySpend(
                    key = cat,
                    amountMinor = amount,
                    percentage = (amount.toDouble() / totalSpend * 100.0).toFloat(),
                    count = rawCategoryCountMap[cat] ?: 0,
                )
            }.sortedByDescending { it.amountMinor }
        } else {
            emptyList()
        }

        // Monthly spending with count and percentage
        val monthlyMap = mutableMapOf<YearMonth, Long>()
        val monthlyCountMap = mutableMapOf<YearMonth, Int>()

        filteredFuels.forEach {
            val ym = it.filledAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.totalCostMinor
            monthlyCountMap[ym] = (monthlyCountMap[ym] ?: 0) + 1
        }
        filteredServices.forEach {
            val ym = it.performedAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.totalCostMinor
            monthlyCountMap[ym] = (monthlyCountMap[ym] ?: 0) + 1
        }
        filteredExpenses.forEach {
            val ym = it.incurredAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.amountMinor
            monthlyCountMap[ym] = (monthlyCountMap[ym] ?: 0) + 1
        }

        val monthlySpends = monthlyMap.map { (ym, amount) ->
            MonthlySpend(
                yearMonth = ym,
                amountMinor = amount,
                count = monthlyCountMap[ym] ?: 0,
                percentageOfTotal = if (totalSpend > 0) (amount.toDouble() / totalSpend * 100.0).toFloat() else 0f,
            )
        }.sortedBy { it.yearMonth }

        val avgMonthlySpend = if (monthlySpends.isNotEmpty()) {
            totalSpend / monthlySpends.size
        } else 0L

        val allInstants = buildList {
            filteredFuels.forEach { add(it.filledAt) }
            filteredServices.forEach { add(it.performedAt) }
            filteredExpenses.forEach { add(it.incurredAt) }
        }
        val earliestInstant = allInstants.minOrNull()
        val latestInstant = allInstants.maxOrNull()

        val costPerDay = if (earliestInstant != null && latestInstant != null && totalSpend > 0) {
            val days = java.time.Duration.between(earliestInstant, latestInstant).toDays().coerceAtLeast(1)
            totalSpend / days
        } else null

        // Consumption history over time
        val consumptionHistory = fuelStats.segments.mapNotNull { segment ->
            unit.fromLitresPer100Km(segment.litresPer100Km)?.let { value ->
                ConsumptionPoint(
                    date = segment.endAt,
                    consumptionValue = value,
                    odometerKm = segment.endOdometerKm,
                    deltaFromAverage = avgConsumption?.let { value - it },
                )
            }
        }

        return VehicleAnalyticsData(
            totalSpendMinor = totalSpend,
            fuelSpendMinor = fuelSpend,
            serviceSpendMinor = serviceSpend,
            otherSpendMinor = otherSpend,
            trackedDistanceKm = trackedDistance,
            totalCostPerKmMinor = totalCostPerKm,
            fuelCostPerKmMinor = fuelCostPerKm,
            averageConsumption = avgConsumption,
            bestConsumption = bestConsumption,
            worstConsumption = worstConsumption,
            averageMonthlySpendMinor = avgMonthlySpend,
            costPerDayMinor = costPerDay,
            categorySpends = categorySpends,
            monthlySpends = monthlySpends,
            consumptionHistory = consumptionHistory,
            totalEntriesCount = totalEntries,
        )
    }

    private fun VehicleEntity.consumptionUnit(): ConsumptionUnit = when {
        distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.UsGallons ->
            ConsumptionUnit.MilesPerUsGallon
        distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.ImperialGallons ->
            ConsumptionUnit.MilesPerImperialGallon
        else -> ConsumptionUnit.LitresPer100Km
    }
}
