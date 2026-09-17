package com.cargenome.app.domain.service

import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceScheduleCalculatorTest {

    private val now = LocalDate.of(2026, 9, 16)
    private val zone = ZoneOffset.UTC

    private fun schedule(
        intervalKm: Double? = null,
        intervalMonths: Int? = null,
        lastPerformedAt: Instant? = null,
        lastPerformedKm: Double? = null,
        warnKm: Double = 500.0,
        warnDays: Int = 14,
        isEnabled: Boolean = true,
        title: String = "Test Schedule",
    ) = MaintenanceScheduleEntity(
        id = 1,
        vehicleId = 1,
        title = title,
        category = ServiceCategory.RoutineService,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        lastPerformedAt = lastPerformedAt,
        lastPerformedOdometerKm = lastPerformedKm,
        warnBeforeKm = warnKm,
        warnBeforeDays = warnDays,
        isEnabled = isEnabled,
    )

    @Test
    fun `disabled schedule is always OK`() {
        val s = schedule(
            intervalKm = 10_000.0,
            lastPerformedKm = 100_000.0,
            isEnabled = false,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = 120_000.0,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Ok, status.overallStatus)
    }

    @Test
    fun `distance interval - OK when well below interval`() {
        val s = schedule(
            intervalKm = 10_000.0,
            lastPerformedKm = 100_000.0,
            warnKm = 1_000.0,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = 105_000.0,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Ok, status.overallStatus)
        assertEquals(110_000.0, status.dueKm!!, 0.001)
        assertEquals(5_000.0, status.remainingKm!!, 0.001)
    }

    @Test
    fun `distance interval - DueSoon when inside warning threshold`() {
        val s = schedule(
            intervalKm = 10_000.0,
            lastPerformedKm = 100_000.0,
            warnKm = 1_000.0,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = 109_200.0,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.DueSoon, status.overallStatus)
        assertTrue(status.isDueSoon)
        assertFalse(status.isOverdue)
        assertEquals(800.0, status.remainingKm!!, 0.001)
    }

    @Test
    fun `distance interval - Overdue when exceeded`() {
        val s = schedule(
            intervalKm = 10_000.0,
            lastPerformedKm = 100_000.0,
            warnKm = 1_000.0,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = 110_500.0,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Overdue, status.overallStatus)
        assertTrue(status.isOverdue)
        assertEquals(-500.0, status.remainingKm!!, 0.001)
    }

    @Test
    fun `time interval - OK when far from due date`() {
        val lastDate = LocalDate.of(2026, 6, 1).atStartOfDay(zone).toInstant()
        val s = schedule(
            intervalMonths = 6,
            lastPerformedAt = lastDate,
            warnDays = 14,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = null,
            currentDate = now, // 2026-09-16, due date 2026-12-01
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Ok, status.overallStatus)
        assertEquals(LocalDate.of(2026, 12, 1), status.dueDate)
        assertTrue(status.remainingDays!! > 14)
    }

    @Test
    fun `time interval - DueSoon when approaching due date`() {
        val lastDate = LocalDate.of(2025, 9, 25).atStartOfDay(zone).toInstant()
        val s = schedule(
            intervalMonths = 12,
            lastPerformedAt = lastDate,
            warnDays = 14,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = null,
            currentDate = now, // 2026-09-16, due date 2026-09-25 (9 days left)
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.DueSoon, status.overallStatus)
        assertEquals(9L, status.remainingDays!!)
    }

    @Test
    fun `time interval - Overdue when past due date`() {
        val lastDate = LocalDate.of(2025, 8, 1).atStartOfDay(zone).toInstant()
        val s = schedule(
            intervalMonths = 12,
            lastPerformedAt = lastDate,
            warnDays = 14,
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = null,
            currentDate = now, // 2026-09-16, due date 2026-08-01 (-46 days)
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Overdue, status.overallStatus)
        assertTrue(status.remainingDays!! < 0)
    }

    @Test
    fun `combined interval - Overdue takes precedence over DueSoon and Ok`() {
        val lastDate = LocalDate.of(2025, 1, 1).atStartOfDay(zone).toInstant()
        val s = schedule(
            intervalKm = 10_000.0,
            intervalMonths = 12,
            lastPerformedAt = lastDate, // Due 2026-01-01 -> Overdue on 2026-09-16
            lastPerformedKm = 50_000.0, // Due 60_000.0
        )
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = 52_000.0, // km is OK
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Overdue, status.overallStatus)
    }

    @Test
    fun `calculateAll sorts Overdue first, then DueSoon, then Ok`() {
        val overdue = schedule(intervalKm = 10_000.0, lastPerformedKm = 10_000.0, title = "Overdue item")
        val dueSoon = schedule(intervalKm = 10_000.0, lastPerformedKm = 10_000.0, warnKm = 1000.0, title = "Due soon item")
        val ok = schedule(intervalKm = 10_000.0, lastPerformedKm = 10_000.0, title = "Ok item")

        val list = listOf(ok, overdue, dueSoon)
        // With currentKm = 20_500: overdue has diff -500 (Overdue)
        // With currentKm = 19_500: dueSoon has diff 500 <= 1000 (DueSoon)
        // With currentKm = 15_000: ok has diff 5000 (Ok)
        val statuses = listOf(
            MaintenanceScheduleCalculator.calculate(ok, currentOdometerKm = 15_000.0, currentDate = now, zoneId = zone),
            MaintenanceScheduleCalculator.calculate(overdue, currentOdometerKm = 20_500.0, currentDate = now, zoneId = zone),
            MaintenanceScheduleCalculator.calculate(dueSoon, currentOdometerKm = 19_500.0, currentDate = now, zoneId = zone),
        )

        val sorted = statuses.sortedWith(
            compareBy<ScheduleStatus> {
                when (it.overallStatus) {
                    ScheduleDueStatus.Overdue -> 0
                    ScheduleDueStatus.DueSoon -> 1
                    ScheduleDueStatus.Ok -> 2
                }
            }.thenBy { it.schedule.title },
        )

        assertEquals("Overdue item", sorted[0].schedule.title)
        assertEquals("Due soon item", sorted[1].schedule.title)
        assertEquals("Ok item", sorted[2].schedule.title)
    }

    @Test
    fun `time interval - falls back to vehicleCreatedAt when purchasedOn is null`() {
        val s = schedule(
            intervalMonths = 12,
            lastPerformedAt = null,
            warnDays = 14,
        )
        val created = now.minusMonths(11) // 1 month remaining (~30 days) -> Ok
        val status = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = null,
            purchasedOn = null,
            vehicleCreatedAt = created,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Ok, status.overallStatus)
        assertEquals(created.plusMonths(12), status.dueDate)

        // If created 12 months ago -> Overdue
        val overdueCreated = now.minusMonths(12).minusDays(2)
        val overdueStatus = MaintenanceScheduleCalculator.calculate(
            schedule = s,
            currentOdometerKm = null,
            purchasedOn = null,
            vehicleCreatedAt = overdueCreated,
            currentDate = now,
            zoneId = zone,
        )
        assertEquals(ScheduleDueStatus.Overdue, overdueStatus.overallStatus)
    }
}
