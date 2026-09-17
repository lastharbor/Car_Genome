package com.cargenome.app.domain.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedulePeriodicCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<MaintenanceReminderWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun runImmediately() {
        val request = OneTimeWorkRequestBuilder<MaintenanceReminderWorker>()
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "cargenome_maintenance_reminder"
    }
}
