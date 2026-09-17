package com.cargenome.app.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered when the user dismisses / swipes away an ongoing
 * maintenance reminder notification.
 *
 * If the event or schedule is still active/due and persistent notifications are enabled:
 * - Immediately re-posts the notification so it behaves like an undismissable notification.
 * - If repeat interval > 0, also schedules a repeat alarm.
 */
@AndroidEntryPoint
class MaintenanceDismissReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appSettings: AppSettingsRepository

    @Inject
    lateinit var eventDao: MaintenanceEventDao

    @Inject
    lateinit var vehicleRepo: VehicleRepository

    @Inject
    lateinit var serviceRepo: ServiceRepository

    @Inject
    lateinit var odometerRepo: OdometerRepository

    @Inject
    lateinit var alarmScheduler: MaintenanceAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1L)
        if (eventId <= 0L && scheduleId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = appSettings.settings.first()
                if (!settings.persistentMaintenanceNotification) {
                    // User disabled persistent notifications, respect the dismissal
                    return@launch
                }
                val intervalMinutes = settings.maintenanceReminderIntervalMinutes

                if (eventId > 0L) {
                    val event = eventDao.findById(eventId)
                    if (event == null || event.isCompleted) {
                        alarmScheduler.cancelAlarm(eventId)
                        return@launch
                    }

                    val vehicle = vehicleRepo.find(event.vehicleId)
                    val vehicleName = vehicle?.let { v ->
                        v.nickname?.takeIf { it.isNotBlank() }
                            ?: listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" ")
                    }.orEmpty()

                    MaintenanceNotificationHelper.showEventReminder(
                        context = context,
                        eventId = event.id,
                        vehicleId = event.vehicleId,
                        title = event.title,
                        vehicleName = vehicleName,
                        scheduledDate = event.scheduledDate,
                        scheduledTimeMinutes = event.scheduledTimeMinutes,
                        shop = event.shop,
                        notes = event.notes,
                        isPersistent = true,
                    )

                    if (intervalMinutes > 0) {
                        alarmScheduler.scheduleRepeatAlarm(event.id, intervalMinutes)
                    }
                } else if (scheduleId > 0L && vehicleId > 0L) {
                    val vehicle = vehicleRepo.find(vehicleId) ?: return@launch
                    val schedules = serviceRepo.observeSchedules(vehicleId).first()
                    val schedule = schedules.find { it.id == scheduleId } ?: return@launch
                    if (!schedule.isEnabled) return@launch

                    val currentKm = odometerRepo.currentKm(vehicleId)
                    val status = MaintenanceScheduleCalculator.calculate(
                        schedule = schedule,
                        currentOdometerKm = currentKm,
                        initialOdometerKm = vehicle.initialOdometerKm,
                        purchasedOn = vehicle.purchasedOn,
                        vehicleCreatedAt = vehicle.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                    )
                    if (status.isOverdue || (status.remainingDays != null && status.remainingDays <= 0)) {
                        MaintenanceNotificationHelper.showReminder(
                            context = context,
                            vehicle = vehicle,
                            status = status,
                            isPersistent = true,
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "dismiss_event_id"
        const val EXTRA_SCHEDULE_ID = "dismiss_schedule_id"
        const val EXTRA_VEHICLE_ID = "dismiss_vehicle_id"
    }
}
