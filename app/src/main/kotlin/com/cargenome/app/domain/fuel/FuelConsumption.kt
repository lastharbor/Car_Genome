package com.cargenome.app.domain.fuel

import com.cargenome.app.data.db.entity.FuelRecordEntity
import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * One stretch between two full tanks, which is the only interval where the
 * fuel burnt is actually known.
 */
@Immutable
data class FuelSegment(
    val startRecordId: Long,
    val endRecordId: Long,
    val startAt: Instant,
    val endAt: Instant,
    val startOdometerKm: Double,
    val endOdometerKm: Double,
    val litres: Double,
    val costMinor: Long,
    /** How many fill-ups closed the stretch, counting partial ones along the way. */
    val fillUps: Int,
) {
    val distanceKm: Double get() = endOdometerKm - startOdometerKm

    val litresPer100Km: Double get() = litres * 100.0 / distanceKm

    val costPerKmMinor: Double get() = costMinor / distanceKm
}

@Immutable
data class FuelStatistics(
    val segments: List<FuelSegment> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val totalLitres: Double = 0.0,
    val totalCostMinor: Long = 0,
    /** Every litre in the log, including the ones no segment could account for. */
    val loggedLitres: Double = 0.0,
    val loggedCostMinor: Long = 0,
) {
    /**
     * Weighted by distance rather than an average of the segment figures: a
     * 600 km motorway run should not count the same as a 40 km errand.
     */
    val averageLitresPer100Km: Double?
        get() = if (totalDistanceKm > 0.0 && totalLitres > 0.0) {
            totalLitres * 100.0 / totalDistanceKm
        } else {
            null
        }

    val bestLitresPer100Km: Double? get() = segments.minOfOrNull { it.litresPer100Km }

    val worstLitresPer100Km: Double? get() = segments.maxOfOrNull { it.litresPer100Km }

    val costPerKmMinor: Double?
        get() = if (totalDistanceKm > 0.0) totalCostMinor / totalDistanceKm else null
}

/**
 * Fuel economy by the tank-to-tank method.
 *
 * Only a run from one full tank to the next says how much fuel went into a
 * known distance: a partial fill leaves an unknown amount already in the tank.
 * Partial fill-ups are therefore not skipped but rolled into the stretch they
 * fall in, and a stretch the owner has flagged as having an unrecorded fill-up
 * is dropped entirely, because its litres cannot be trusted.
 *
 * The first fill-up of a car never yields a figure either: there is nothing
 * before it to measure the distance from.
 */
object FuelConsumption {

    fun analyse(records: List<FuelRecordEntity>): FuelStatistics {
        val ordered = records.sortedWith(compareBy({ it.filledAt }, { it.id }))
        val segments = mutableListOf<FuelSegment>()

        var anchor: FuelRecordEntity? = null
        var litres = 0.0
        var cost = 0L
        var fillUps = 0

        for (record in ordered) {
            // Fuel went in without being logged, so nothing spanning this gap
            // can be measured. Start again from here.
            if (record.missedPreviousFillUp) {
                anchor = null
                litres = 0.0
                cost = 0L
                fillUps = 0
            }

            val current = anchor
            if (current == null) {
                if (record.isFullTank) anchor = record
                continue
            }

            litres += record.volumeLitres
            cost += record.totalCostMinor
            fillUps++

            if (!record.isFullTank) continue

            // A backwards or unchanged odometer means a typo somewhere; report
            // nothing rather than a nonsense figure.
            if (record.odometerKm > current.odometerKm && litres > 0.0) {
                segments += FuelSegment(
                    startRecordId = current.id,
                    endRecordId = record.id,
                    startAt = current.filledAt,
                    endAt = record.filledAt,
                    startOdometerKm = current.odometerKm,
                    endOdometerKm = record.odometerKm,
                    litres = litres,
                    costMinor = cost,
                    fillUps = fillUps,
                )
            }

            anchor = record
            litres = 0.0
            cost = 0L
            fillUps = 0
        }

        return FuelStatistics(
            segments = segments,
            totalDistanceKm = segments.sumOf { it.distanceKm },
            totalLitres = segments.sumOf { it.litres },
            totalCostMinor = segments.sumOf { it.costMinor },
            loggedLitres = ordered.sumOf { it.volumeLitres },
            loggedCostMinor = ordered.sumOf { it.totalCostMinor },
        )
    }

    /**
     * Consumption keyed by the fill-up that closed each stretch, so the log can
     * show a figure against the row the owner is looking at.
     */
    fun byClosingRecord(statistics: FuelStatistics): Map<Long, FuelSegment> =
        statistics.segments.associateBy { it.endRecordId }
}
