package com.cargenome.app.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cargenome.app.MainActivity
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.displayName
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.shortRes
import java.util.Locale
import kotlin.math.abs

object MaintenanceNotificationHelper {

    const val CHANNEL_ID = "cargenome_maintenance_v2"

    fun ensureChannel(context: Context) {
        val name = context.getString(R.string.notification_channel_maintenance)
        val descriptionText = context.getString(R.string.notification_channel_maintenance_desc)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    fun canSendNotifications(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
            return permission == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun showReminder(
        context: Context,
        vehicle: VehicleEntity,
        status: ScheduleStatus,
        isPersistent: Boolean = true,
    ) {
        if (!canSendNotifications(context)) return

        ensureChannel(context)

        val locale = Locale.getDefault()
        val vehicleName = vehicle.displayName()
        val schedule = status.schedule

        val isTodayOrOverdue = status.isOverdue || (status.remainingDays != null && status.remainingDays <= 0)
        val makeOngoing = isTodayOrOverdue && isPersistent

        val title = if (status.isOverdue) {
            context.getString(R.string.notification_overdue_title, schedule.title)
        } else {
            context.getString(R.string.notification_due_soon_title, schedule.title)
        }

        val details = mutableListOf<String>()
        details.add(vehicleName)

        status.remainingKm?.let { diffKm ->
            val formattedDiff = context.getString(
                vehicle.distanceUnit.shortRes(),
                Format.distance(abs(diffKm), vehicle.distanceUnit, locale),
            )
            if (diffKm <= 0) {
                details.add(context.getString(R.string.schedule_overdue_by_km, formattedDiff))
            } else {
                details.add(context.getString(R.string.schedule_due_in_km, formattedDiff))
            }
        }

        status.remainingDays?.let { days ->
            val absDays = abs(days)
            if (days < 0) {
                details.add(context.getString(R.string.schedule_overdue_by_days, absDays))
            } else {
                details.add(context.getString(R.string.schedule_due_in_days, absDays))
            }
        }

        val contentText = details.joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(makeOngoing)
            .setAutoCancel(!makeOngoing)
            .build().apply {
                if (makeOngoing) {
                    flags = flags or android.app.Notification.FLAG_ONGOING_EVENT or android.app.Notification.FLAG_NO_CLEAR
                }
            }

        try {
            NotificationManagerCompat.from(context).notify(schedule.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission check raced or was revoked
        }
    }

    fun showEventReminder(
        context: Context,
        eventId: Long,
        vehicleId: Long,
        title: String,
        vehicleName: String,
        scheduledDate: java.time.LocalDate?,
        scheduledTimeMinutes: Int?,
        shop: String?,
        notes: String?,
        isPersistent: Boolean = true,
    ) {
        if (!canSendNotifications(context)) return

        ensureChannel(context)

        val today = java.time.LocalDate.now()
        val isOverdue = scheduledDate != null && scheduledDate.isBefore(today)
        val isTodayOrOverdue = scheduledDate != null && !scheduledDate.isAfter(today)
        val makeOngoing = isTodayOrOverdue && isPersistent

        val notificationTitle = if (isOverdue) {
            context.getString(R.string.notification_overdue_title, title)
        } else {
            context.getString(R.string.notification_event_title, title)
        }

        val details = mutableListOf<String>()
        if (vehicleName.isNotBlank()) {
            details.add(vehicleName)
        }

        if (scheduledDate != null) {
            val dateStr = if (scheduledDate == today) {
                context.getString(R.string.calendar_today)
            } else {
                Format.date(scheduledDate, java.util.Locale.getDefault())
            }
            val timeStr = if (scheduledTimeMinutes != null) {
                val h = scheduledTimeMinutes / 60
                val m = scheduledTimeMinutes % 60
                String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
            } else null

            val whenStr = if (timeStr != null) "$dateStr, $timeStr" else dateStr
            details.add(context.getString(R.string.notification_event_scheduled, whenStr))
        }

        if (!shop.isNullOrBlank()) {
            details.add(shop)
        }
        if (!notes.isNullOrBlank()) {
            details.add(notes)
        }

        val contentText = details.joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (100_000 + eventId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completeIntent = Intent(context, MaintenanceCompleteReceiver::class.java).apply {
            putExtra(MaintenanceCompleteReceiver.EXTRA_EVENT_ID, eventId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (200_000 + eventId).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, MaintenanceDismissReceiver::class.java).apply {
            putExtra(MaintenanceDismissReceiver.EXTRA_EVENT_ID, eventId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (300_000 + eventId).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setOngoing(makeOngoing)
            .setAutoCancel(!makeOngoing)
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.event_action_complete),
                completePendingIntent,
            )
            .build().apply {
                if (makeOngoing) {
                    flags = flags or android.app.Notification.FLAG_ONGOING_EVENT or android.app.Notification.FLAG_NO_CLEAR
                }
            }

        try {
            NotificationManagerCompat.from(context).notify((100_000 + eventId).toInt(), notification)
        } catch (_: SecurityException) {
            // Permission check raced or was revoked
        }
    }

    fun cancelEventNotification(context: Context, eventId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel((100_000 + eventId).toInt())
        } catch (_: Exception) {
            // Ignored
        }
    }

    fun cancelStaleNotifications(context: Context, validIds: Set<Int>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val active = manager.activeNotifications ?: return
            for (sbn in active) {
                if (sbn.packageName == context.packageName && !validIds.contains(sbn.id)) {
                    manager.cancel(sbn.id)
                }
            }
        } catch (_: Exception) {
            // Ignored
        }
    }
}

