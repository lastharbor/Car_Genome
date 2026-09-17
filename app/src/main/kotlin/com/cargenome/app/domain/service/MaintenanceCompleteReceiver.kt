package com.cargenome.app.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cargenome.app.data.repository.ServiceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered from the persistent maintenance notification action
 * to mark the maintenance event completed and dismiss the ongoing notification.
 */
@AndroidEntryPoint
class MaintenanceCompleteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var serviceRepo: ServiceRepository

    @Inject
    lateinit var alarmScheduler: MaintenanceAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return

        alarmScheduler.cancelAlarm(eventId)
        MaintenanceNotificationHelper.cancelEventNotification(context, eventId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serviceRepo.completeEvent(
                    eventId = eventId,
                    createServiceRecord = true,
                    actualOdometerKm = null,
                    labourCostMinor = 0L,
                    partsCostMinor = 0L,
                    shop = null,
                    notes = null,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "complete_event_id"
    }
}
