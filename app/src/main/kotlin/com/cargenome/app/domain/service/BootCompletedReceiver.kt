package com.cargenome.app.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: MaintenanceAlarmScheduler

    @Inject
    lateinit var reminderScheduler: MaintenanceReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            reminderScheduler.schedulePeriodicCheck()

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    alarmScheduler.rescheduleAllAlarms()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
