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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class CategorySpend(
    val key: String,
    val amountMinor: Long,
    val percentage: Float,
)

data class MonthlySpend(
    val yearMonth: YearMonth,
    val amountMinor: Long,
)

data class ConsumptionPoint(
    val date: Instant,
    val consumptionValue: Double,
)

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
    val categorySpends: List<CategorySpend> = emptyList(),
    val monthlySpends: List<MonthlySpend> = emptyList(),
    val consumptionHistory: List<ConsumptionPoint> = emptyList(),
)

object VehicleAnalyticsCalculator {

    fun calculate(
        vehicle: VehicleEntity,
        fuelRecords: List<FuelRecordEntity>,
        serviceRecords: List<ServiceRecordEntity>,
        expenses: List<ExpenseEntity>,
        currentOdometerKm: Double?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): VehicleAnalyticsData {
        val fuelStats = FuelConsumption.analyse(fuelRecords)
        val fuelSpend = fuelRecords.sumOf { it.totalCostMinor }
        val serviceSpend = serviceRecords.sumOf { it.totalCostMinor }
        val otherSpend = expenses.sumOf { it.amountMinor }
        val totalSpend = fuelSpend + serviceSpend + otherSpend

        val minOdo = listOfNotNull(
            vehicle.initialOdometerKm.takeIf { it > 0.0 },
            fuelRecords.minOfOrNull { it.odometerKm },
            serviceRecords.mapNotNull { it.odometerKm }.minOrNull(),
            expenses.mapNotNull { it.odometerKm }.minOrNull(),
        ).minOrNull() ?: 0.0

        val maxOdo = listOfNotNull(
            currentOdometerKm,
            fuelRecords.maxOfOrNull { it.odometerKm },
            serviceRecords.mapNotNull { it.odometerKm }.maxOrNull(),
            expenses.mapNotNull { it.odometerKm }.maxOrNull(),
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

        // Spending by category breakdown
        val rawCategoryMap = mutableMapOf<String, Long>()
        if (fuelSpend > 0) rawCategoryMap["fuel"] = fuelSpend
        if (serviceSpend > 0) rawCategoryMap["service"] = serviceSpend
        expenses.groupBy { it.category.name.lowercase() }.forEach { (cat, list) ->
            rawCategoryMap[cat] = (rawCategoryMap[cat] ?: 0L) + list.sumOf { it.amountMinor }
        }

        val categorySpends = if (totalSpend > 0) {
            rawCategoryMap.map { (cat, amount) ->
                CategorySpend(
                    key = cat,
                    amountMinor = amount,
                    percentage = (amount.toDouble() / totalSpend * 100.0).toFloat(),
                )
            }.sortedByDescending { it.amountMinor }
        } else {
            emptyList()
        }

        // Monthly spending (last 12 months or chronological)
        val monthlyMap = mutableMapOf<YearMonth, Long>()
        fuelRecords.forEach {
            val ym = it.filledAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.totalCostMinor
        }
        serviceRecords.forEach {
            val ym = it.performedAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.totalCostMinor
        }
        expenses.forEach {
            val ym = it.incurredAt.atZone(zoneId).toLocalDate().let { d -> YearMonth.of(d.year, d.month) }
            monthlyMap[ym] = (monthlyMap[ym] ?: 0L) + it.amountMinor
        }

        val monthlySpends = monthlyMap.map { (ym, amount) ->
            MonthlySpend(ym, amount)
        }.sortedBy { it.yearMonth }

        // Consumption history over time
        val consumptionHistory = fuelStats.segments.mapNotNull { segment ->
            unit.fromLitresPer100Km(segment.litresPer100Km)?.let { value ->
                ConsumptionPoint(
                    date = segment.endAt,
                    consumptionValue = value,
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
            categorySpends = categorySpends,
            monthlySpends = monthlySpends,
            consumptionHistory = consumptionHistory,
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
