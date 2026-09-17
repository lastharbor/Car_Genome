package com.cargenome.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cargenome.app.domain.service.MaintenanceNotificationHelper
import com.cargenome.app.domain.service.MaintenanceReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarGenomeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: MaintenanceReminderScheduler

    override fun onCreate() {
        super.onCreate()
        MaintenanceNotificationHelper.ensureChannel(this)
        reminderScheduler.schedulePeriodicCheck()
        reminderScheduler.runImmediately()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

