package com.cargenome.app.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class MaintenanceReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val vehicles: VehicleRepository,
    private val service: ServiceRepository,
    private val odometer: OdometerRepository,
    private val appSettings: AppSettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!MaintenanceNotificationHelper.canSendNotifications(appContext)) {
            return Result.success()
        }

        val isPersistent = appSettings.settings.first().persistentMaintenanceNotification

        val activeVehicles = vehicles.observeActive().first()
        val validNotificationIds = mutableSetOf<Int>()

        for (vehicle in activeVehicles) {
            val schedules = service.observeSchedules(vehicle.id).first()
            if (schedules.isNotEmpty()) {
                val currentKm = odometer.currentKm(vehicle.id)
                val statuses = MaintenanceScheduleCalculator.calculateAll(
                    schedules = schedules,
                    currentOdometerKm = currentKm,
                    initialOdometerKm = vehicle.initialOdometerKm,
                    purchasedOn = vehicle.purchasedOn,
                    vehicleCreatedAt = vehicle.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                )

                for (status in statuses) {
                    if (status.isOverdue || status.isDueSoon) {
                        validNotificationIds.add(status.schedule.id.toInt())
                        MaintenanceNotificationHelper.showReminder(
                            context = appContext,
                            vehicle = vehicle,
                            status = status,
                            isPersistent = isPersistent,
                        )
                    }
                }
            }

            val events = service.observeEvents(vehicle.id).first()
            val today = java.time.LocalDate.now()
            val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val startMinutes = appSettings.settings.first().maintenanceReminderStartTimeMinutes

            for (event in events) {
                if (event.isCompleted || event.remindAdvanceDays < 0) continue
                if (event.scheduledDate.isBefore(today)) continue

                val reminderDate = if (event.remindAdvanceDays > 0) {
                    event.scheduledDate.minusDays(event.remindAdvanceDays.toLong())
                } else {
                    event.scheduledDate
                }

                // If reminder date is in the future, exact AlarmManager alarm is scheduled for it
                if (today.isBefore(reminderDate)) continue

                val reminderTimeMinutes = if (event.remindAdvanceDays == 0 && event.scheduledTimeMinutes != null) {
                    event.scheduledTimeMinutes
                } else {
                    startMinutes
                }

                val reminderDateTime = reminderDate.atTime(
                    java.time.LocalTime.of(
                        (reminderTimeMinutes / 60).coerceIn(0, 23),
                        (reminderTimeMinutes % 60).coerceIn(0, 59),
                    ),
                )
                val reminderEpochMillis = reminderDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                // Check if user explicitly recorded this event today with an exact time in the past
                val wasExplicitlyCreatedInPastToday = event.scheduledDate == today &&
                    event.remindAdvanceDays == 0 &&
                    event.scheduledTimeMinutes != null &&
                    reminderEpochMillis <= event.createdAt.toEpochMilli()

                if (wasExplicitlyCreatedInPastToday) continue

                // If scheduled for an exact minute later today, exact AlarmManager will trigger at that exact minute
                val isFutureTimeToday = reminderDate == today &&
                    event.remindAdvanceDays == 0 &&
                    event.scheduledTimeMinutes != null &&
                    reminderTimeMinutes > nowMinutes

                if (isFutureTimeToday) continue

                // If today is the reminder date and morning reminder start time has not arrived yet
                val isBeforeStartTimeToday = reminderDate == today &&
                    (event.remindAdvanceDays > 0 || event.scheduledTimeMinutes == null) &&
                    nowMinutes < startMinutes

                if (isBeforeStartTimeToday) continue

                val notificationId = (100_000 + event.id).toInt()
                validNotificationIds.add(notificationId)
                val vehicleName = vehicle.nickname?.takeIf { it.isNotBlank() }
                    ?: listOf(vehicle.make, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")
                MaintenanceNotificationHelper.showEventReminder(
                    context = appContext,
                    eventId = event.id,
                    vehicleId = event.vehicleId,
                    title = event.title,
                    vehicleName = vehicleName,
                    scheduledDate = event.scheduledDate,
                    scheduledTimeMinutes = event.scheduledTimeMinutes,
                    shop = event.shop,
                    notes = event.notes,
                    isPersistent = isPersistent,
                )
            }
        }

        MaintenanceNotificationHelper.cancelStaleNotifications(appContext, validNotificationIds)

        return Result.success()
    }
}
