package com.cargenome.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.data.settings.AppSettings
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.data.settings.ThemeMode
import com.cargenome.app.ui.CarGenomeApp
import com.cargenome.app.ui.theme.CarGenomeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.media.MediaScannerConnection
import com.cargenome.app.domain.service.MaintenanceReminderScheduler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: AppSettingsRepository

    @Inject
    lateinit var reminderScheduler: MaintenanceReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()

        if (!BuildConfig.DEBUG) {
            if (com.cargenome.app.domain.security.SecurityIntegrityChecker.isHookFrameworkDetected() ||
                com.cargenome.app.domain.security.SecurityIntegrityChecker.isDebuggerOrTracerAttached()
            ) {
                android.util.Log.w("Security", "Integrity check triggered")
            }
        }

        // Ensure downloaded APKs are indexed by MediaStore for file managers
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val filesToScan = listOfNotNull(
            java.io.File(downloadsDir, "CarGenome.apk"),
            java.io.File(downloadsDir.parentFile, "Downloads/CarGenome.apk"),
        ).map { it.absolutePath }.toTypedArray()
        MediaScannerConnection.scanFile(
            applicationContext,
            filesToScan,
            arrayOf("application/vnd.android.package-archive"),
            null,
        )
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            CarGenomeTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoledDark,
            ) {
                CarGenomeApp(settingsRepository = settingsRepository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reminderScheduler.runImmediately()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableHighRefreshRate()
        }
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentDisplay = display ?: return
            val maxRate = currentDisplay.supportedModes.maxOfOrNull { it.refreshRate } ?: return
            val params = window.attributes
            params.preferredRefreshRate = maxRate
            window.attributes = params
        } else {
            @Suppress("DEPRECATION")
            val currentDisplay = windowManager.defaultDisplay
            val maxRate = currentDisplay?.supportedModes?.maxOfOrNull { it.refreshRate }
            if (maxRate != null) {
                val params = window.attributes
                params.preferredRefreshRate = maxRate
                window.attributes = params
            }
        }
    }
}

