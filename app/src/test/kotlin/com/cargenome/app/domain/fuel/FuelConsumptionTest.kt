package com.cargenome.app.domain.fuel

import com.cargenome.app.data.db.entity.FuelRecordEntity
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelConsumptionTest {

    private val start: Instant = Instant.parse("2025-03-01T09:00:00Z")
    private var nextId = 1L

    private fun fill(
        day: Long,
        odometerKm: Double,
        litres: Double,
        costMinor: Long = 0,
        full: Boolean = true,
        missedPrevious: Boolean = false,
    ) = FuelRecordEntity(
        id = nextId++,
        vehicleId = 1,
        filledAt = start.plus(Duration.ofDays(day)),
        odometerKm = odometerKm,
        volumeLitres = litres,
        totalCostMinor = costMinor,
        isFullTank = full,
        missedPreviousFillUp = missedPrevious,
    )

    @Test
    fun `a single fill-up says nothing about consumption`() {
        val stats = FuelConsumption.analyse(listOf(fill(day = 0, odometerKm = 10_000.0, litres = 45.0)))

        assertTrue(stats.segments.isEmpty())
        assertNull(stats.averageLitresPer100Km)
        assertEquals(45.0, stats.loggedLitres, 0.001)
    }

    @Test
    fun `two full tanks give one figure, counting only the second fill`() {
        // The first tank's fuel was burnt before the log started; what matters
        // is the 40 litres it took to cover the 500 km after it.
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 45.0),
                fill(day = 7, odometerKm = 10_500.0, litres = 40.0),
            ),
        )

        val segment = stats.segments.single()
        assertEquals(500.0, segment.distanceKm, 0.001)
        assertEquals(40.0, segment.litres, 0.001)
        assertEquals(8.0, segment.litresPer100Km, 0.001)
        assertEquals(8.0, stats.averageLitresPer100Km!!, 0.001)
    }

    @Test
    fun `a partial fill in between is rolled into the stretch, not skipped`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 45.0),
                fill(day = 3, odometerKm = 10_200.0, litres = 15.0, full = false),
                fill(day = 7, odometerKm = 10_500.0, litres = 25.0),
            ),
        )

        val segment = stats.segments.single()
        assertEquals(500.0, segment.distanceKm, 0.001)
        assertEquals(40.0, segment.litres, 0.001)
        assertEquals(2, segment.fillUps)
        assertEquals(8.0, segment.litresPer100Km, 0.001)
    }

    @Test
    fun `a run of partial fills before the first full tank is ignored`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 20.0, full = false),
                fill(day = 2, odometerKm = 10_150.0, litres = 20.0, full = false),
                fill(day = 5, odometerKm = 10_400.0, litres = 30.0),
                fill(day = 12, odometerKm = 10_900.0, litres = 40.0),
            ),
        )

        val segment = stats.segments.single()
        assertEquals(10_400.0, segment.startOdometerKm, 0.001)
        assertEquals(500.0, segment.distanceKm, 0.001)
        assertEquals(40.0, segment.litres, 0.001)
    }

    @Test
    fun `a missed fill-up throws away the stretch it falls in`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 45.0),
                fill(day = 7, odometerKm = 10_500.0, litres = 40.0, missedPrevious = true),
                fill(day = 14, odometerKm = 11_000.0, litres = 42.0),
            ),
        )

        val segment = stats.segments.single()
        assertEquals(10_500.0, segment.startOdometerKm, 0.001)
        assertEquals(42.0, segment.litres, 0.001)
        assertEquals(8.4, segment.litresPer100Km, 0.001)
    }

    @Test
    fun `a missed fill-up on a partial record waits for the next full tank`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 45.0),
                fill(day = 5, odometerKm = 10_300.0, litres = 20.0, full = false, missedPrevious = true),
                fill(day = 9, odometerKm = 10_600.0, litres = 30.0),
                fill(day = 16, odometerKm = 11_100.0, litres = 40.0),
            ),
        )

        val segment = stats.segments.single()
        assertEquals(10_600.0, segment.startOdometerKm, 0.001)
        assertEquals(11_100.0, segment.endOdometerKm, 0.001)
        assertEquals(40.0, segment.litres, 0.001)
    }

    @Test
    fun `the average is weighted by distance, not by the number of tanks`() {
        // 100 km at 12 L/100 km and 900 km at 6 L/100 km is 6.6 overall, not
        // the 9.0 an unweighted mean of the two figures would give.
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 0.0, litres = 50.0),
                fill(day = 2, odometerKm = 100.0, litres = 12.0),
                fill(day = 20, odometerKm = 1_000.0, litres = 54.0),
            ),
        )

        assertEquals(2, stats.segments.size)
        assertEquals(6.6, stats.averageLitresPer100Km!!, 0.001)
        assertEquals(12.0, stats.worstLitresPer100Km!!, 0.001)
        assertEquals(6.0, stats.bestLitresPer100Km!!, 0.001)
    }

    @Test
    fun `an odometer that goes backwards produces no figure`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_500.0, litres = 45.0),
                fill(day = 7, odometerKm = 10_000.0, litres = 40.0),
                fill(day = 14, odometerKm = 10_400.0, litres = 32.0),
            ),
        )

        // The bad row is skipped, and the next full tank measures from it.
        val segment = stats.segments.single()
        assertEquals(10_000.0, segment.startOdometerKm, 0.001)
        assertEquals(400.0, segment.distanceKm, 0.001)
    }

    @Test
    fun `records entered out of order are put back in date order`() {
        val later = fill(day = 7, odometerKm = 10_500.0, litres = 40.0)
        val earlier = fill(day = 0, odometerKm = 10_000.0, litres = 45.0)

        val stats = FuelConsumption.analyse(listOf(later, earlier))

        val segment = stats.segments.single()
        assertEquals(earlier.id, segment.startRecordId)
        assertEquals(later.id, segment.endRecordId)
    }

    @Test
    fun `cost per kilometre counts only the fuel a stretch can account for`() {
        val stats = FuelConsumption.analyse(
            listOf(
                fill(day = 0, odometerKm = 10_000.0, litres = 45.0, costMinor = 300_000),
                fill(day = 7, odometerKm = 10_500.0, litres = 40.0, costMinor = 250_000),
            ),
        )

        assertEquals(250_000L, stats.totalCostMinor)
        assertEquals(550_000L, stats.loggedCostMinor)
        assertEquals(500.0, stats.costPerKmMinor!!, 0.001)
    }

    @Test
    fun `consumption can be looked up by the fill-up that closed the stretch`() {
        val opening = fill(day = 0, odometerKm = 10_000.0, litres = 45.0)
        val closing = fill(day = 7, odometerKm = 10_500.0, litres = 40.0)

        val byRecord = FuelConsumption.byClosingRecord(
            FuelConsumption.analyse(listOf(opening, closing)),
        )

        assertNull(byRecord[opening.id])
        assertEquals(8.0, byRecord.getValue(closing.id).litresPer100Km, 0.001)
    }
}
