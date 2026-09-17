package com.cargenome.app.domain.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class MaintenanceAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val eventDao: MaintenanceEventDao,
    private val vehicleDao: VehicleDao,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    suspend fun scheduleEventAlarm(event: MaintenanceEventEntity, vehicleName: String) {
        if (event.isCompleted || event.remindAdvanceDays < 0) {
            cancelAlarm(event.id)
            return
        }

        val today = LocalDate.now()
        val settings = appSettings.settings.first()
        val startMinutes = settings.maintenanceReminderStartTimeMinutes
        val isPersistent = settings.persistentMaintenanceNotification
        val repeatInterval = settings.maintenanceReminderIntervalMinutes

        val isOverdue = event.scheduledDate.isBefore(today)
        val isToday = event.scheduledDate == today

        val reminderDate = if (event.remindAdvanceDays > 0) {
            event.scheduledDate.minusDays(event.remindAdvanceDays.toLong())
        } else {
            event.scheduledDate
        }

        val reminderTimeMinutes = if (isToday && event.scheduledTimeMinutes != null) {
            event.scheduledTimeMinutes
        } else {
            startMinutes
        }

        val hour = (reminderTimeMinutes / 60).coerceIn(0, 23)
        val minute = (reminderTimeMinutes % 60).coerceIn(0, 59)
        val targetTime = LocalTime.of(hour, minute)
        val targetDateTime = reminderDate.atTime(targetTime)
        val epochMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMillis = System.currentTimeMillis()

        // Check if event was intentionally created for a specific past time earlier today without persistent notification
        val wasExplicitlyCreatedInPastToday = isToday &&
            event.remindAdvanceDays == 0 &&
            event.scheduledTimeMinutes != null &&
            epochMillis <= event.createdAt.toEpochMilli()

        if (wasExplicitlyCreatedInPastToday && !isPersistent) {
            cancelAlarm(event.id)
            return
        }

        // 1. Should we post/update the notification right now?
        // Post immediately if:
        // - It is overdue
        // - It is today AND persistent notifications are enabled ("Неудаляемое уведомление в день ТО")
        // - Or the reminder time has already arrived today or in advance
        val shouldShowImmediately = isOverdue ||
            (isToday && isPersistent) ||
            (!today.isBefore(reminderDate) && epochMillis <= nowMillis)

        if (shouldShowImmediately) {
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

            if (isPersistent && repeatInterval > 0) {
                scheduleRepeatAlarm(event.id, repeatInterval)
            }
        }

        // 2. If the reminder date/time is in the future, schedule AlarmManager to alert at that time!
        if (epochMillis > nowMillis) {
            val intent = Intent(context, MaintenanceAlarmReceiver::class.java).apply {
                putExtra(EXTRA_EVENT_ID, event.id)
                putExtra(EXTRA_VEHICLE_ID, event.vehicleId)
                putExtra(EXTRA_TITLE, event.title)
                putExtra(EXTRA_VEHICLE_NAME, vehicleName)
                putExtra(EXTRA_DATE_EPOCH_DAY, event.scheduledDate.toEpochDay())
                putExtra(EXTRA_TIME_MINUTES, event.scheduledTimeMinutes ?: -1)
                putExtra(EXTRA_SHOP, event.shop ?: "")
                putExtra(EXTRA_NOTES, event.notes ?: "")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            alarmManager?.let { am ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
                        }
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
                    }
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
                }
            }
        }
    }

    suspend fun rescheduleAllAlarms() {
        val pendingEvents = eventDao.listPendingWithReminders()
        for (event in pendingEvents) {
            val vehicle = vehicleDao.findById(event.vehicleId)
            val vehicleName = vehicle?.let { v ->
                v.nickname?.takeIf { it.isNotBlank() }
                    ?: listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" ")
            }.orEmpty()
            scheduleEventAlarm(event, vehicleName)
        }
    }

    fun scheduleRepeatAlarm(eventId: Long, delayMinutes: Int) {
        if (delayMinutes <= 0) return
        val triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60 * 1000L
        val intent = Intent(context, MaintenanceAlarmReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancelAlarm(eventId: Long) {
        val intent = Intent(context, MaintenanceAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        MaintenanceNotificationHelper.cancelEventNotification(context, eventId)
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"
        const val EXTRA_DATE_EPOCH_DAY = "extra_date_epoch_day"
        const val EXTRA_TIME_MINUTES = "extra_time_minutes"
        const val EXTRA_SHOP = "extra_shop"
        const val EXTRA_NOTES = "extra_notes"
    }
}
