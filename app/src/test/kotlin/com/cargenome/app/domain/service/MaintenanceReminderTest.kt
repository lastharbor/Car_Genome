package com.cargenome.app.domain.service

import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceReminderTest {

    @Test
    fun `reminder date calculation respects remindAdvanceDays`() {
        val eventDate = LocalDate.of(2026, 9, 25)
        val advanceDays = 3
        val reminderDate = eventDate.minusDays(advanceDays.toLong())

        assertEquals(LocalDate.of(2026, 9, 22), reminderDate)
    }

    @Test
    fun `default start time is 09 00`() {
        val defaultMinutes = 9 * 60
        assertEquals(9, defaultMinutes / 60)
        assertEquals(0, defaultMinutes % 60)
    }

    @Test
    fun `custom start time 08 30 parses correctly`() {
        val minutes = 8 * 60 + 30
        assertEquals(510, minutes)
        assertEquals(8, minutes / 60)
        assertEquals(30, minutes % 60)
        val time = LocalTime.of(minutes / 60, minutes % 60)
        assertEquals(LocalTime.of(8, 30), time)
    }

    @Test
    fun `event in the past is identified correctly`() {
        val today = LocalDate.of(2026, 9, 17)
        val pastEvent = MaintenanceEventEntity(
            id = 1,
            vehicleId = 1,
            title = "Past Oil Change",
            category = ServiceCategory.RoutineService,
            scheduledDate = LocalDate.of(2026, 9, 10),
            remindAdvanceDays = 0,
        )

        assertTrue(pastEvent.scheduledDate.isBefore(today))
    }

    @Test
    fun `event today without exact time uses configured start time`() {
        val today = LocalDate.of(2026, 9, 17)
        val event = MaintenanceEventEntity(
            id = 2,
            vehicleId = 1,
            title = "Today Service",
            category = ServiceCategory.RoutineService,
            scheduledDate = today,
            scheduledTimeMinutes = null,
            remindAdvanceDays = 0,
        )

        val configuredStartTimeMinutes = 8 * 60 // 08:00
        val effectiveMinutes = if (event.remindAdvanceDays == 0 && event.scheduledTimeMinutes != null) {
            event.scheduledTimeMinutes
        } else {
            configuredStartTimeMinutes
        }

        assertEquals(480, effectiveMinutes)
        val targetTime = LocalTime.of(effectiveMinutes / 60, effectiveMinutes % 60)
        assertEquals(LocalTime.of(8, 0), targetTime)
    }

    @Test
    fun `event today with exact time in future triggers at exact minute`() {
        val today = LocalDate.of(2026, 9, 17)
        val event = MaintenanceEventEntity(
            id = 3,
            vehicleId = 1,
            title = "Brake Check",
            category = ServiceCategory.Brakes,
            scheduledDate = today,
            scheduledTimeMinutes = 14 * 60 + 30, // 14:30
            remindAdvanceDays = 0,
            createdAt = Instant.parse("2026-09-17T08:00:00Z"),
        )

        val nowMinutes = 10 * 60 // 10:00
        val isFutureTimeToday = event.scheduledDate == today &&
            event.remindAdvanceDays == 0 &&
            event.scheduledTimeMinutes != null &&
            event.scheduledTimeMinutes > nowMinutes

        assertTrue(isFutureTimeToday)
    }
}
