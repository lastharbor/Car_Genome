package com.cargenome.app.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.domain.service.MaintenanceAlarmScheduler.Companion.EXTRA_EVENT_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MaintenanceAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appSettings: AppSettingsRepository

    @Inject
    lateinit var eventDao: MaintenanceEventDao

    @Inject
    lateinit var vehicleRepo: VehicleRepository

    @Inject
    lateinit var alarmScheduler: MaintenanceAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
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

                val settings = appSettings.settings.first()
                val isPersistent = settings.persistentMaintenanceNotification

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
                    isPersistent = isPersistent,
                )

                // If persistent notification is enabled and repeat interval > 0, schedule next reminder alarm
                if (isPersistent && settings.maintenanceReminderIntervalMinutes > 0) {
                    alarmScheduler.scheduleRepeatAlarm(event.id, settings.maintenanceReminderIntervalMinutes)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
