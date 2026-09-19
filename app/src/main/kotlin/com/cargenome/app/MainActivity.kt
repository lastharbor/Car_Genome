package com.cargenome.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
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

    @Inject
    lateinit var appUpdateManager: com.cargenome.app.domain.update.AppUpdateManager

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

        // Ensure downloaded APKs are indexed by MediaStore without blocking the main thread
        lifecycleScope.launch(Dispatchers.IO) {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val filesToScan = listOfNotNull(
                java.io.File(downloadsDir, "CarGenome.apk"),
                java.io.File(downloadsDir.parentFile, "Downloads/CarGenome.apk"),
            ).filter { it.exists() }.map { it.absolutePath }.toTypedArray()
            if (filesToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    filesToScan,
                    arrayOf("application/vnd.android.package-archive"),
                    null,
                )
            }
        }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            var startupUpdateInfo by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<com.cargenome.app.domain.update.AppUpdateInfo?>(null)
            }
            var startupDownloadState by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<com.cargenome.app.domain.update.UpdateDownloadState>(
                    com.cargenome.app.domain.update.UpdateDownloadState.Idle,
                )
            }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            androidx.compose.runtime.LaunchedEffect(settings.autoCheckUpdates) {
                if (settings.autoCheckUpdates) {
                    val result = appUpdateManager.checkForUpdate()
                    result.getOrNull()?.let { info ->
                        startupUpdateInfo = info
                    }
                }
            }

            CarGenomeTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoledDark,
            ) {
                CarGenomeApp(settingsRepository = settingsRepository)

                startupUpdateInfo?.let { updateInfo ->
                    com.cargenome.app.ui.update.AppUpdateDialog(
                        updateInfo = updateInfo,
                        downloadState = startupDownloadState,
                        canInstallPackages = appUpdateManager.canInstallPackages(),
                        onStartDownload = {
                            coroutineScope.launch {
                                appUpdateManager.downloadApk(updateInfo).collect {
                                    startupDownloadState = it
                                }
                            }
                        },
                        onInstall = { apkFile ->
                            appUpdateManager.installApk(this@MainActivity, apkFile)
                        },
                        onOpenInstallSettings = {
                            appUpdateManager.openInstallPermissionSettings(this@MainActivity)
                        },
                        onDismiss = {
                            startupUpdateInfo = null
                            startupDownloadState = com.cargenome.app.domain.update.UpdateDownloadState.Idle
                        },
                    )
                }
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
            if (params.preferredRefreshRate != maxRate) {
                params.preferredRefreshRate = maxRate
                window.attributes = params
            }
        } else {
            @Suppress("DEPRECATION")
            val currentDisplay = windowManager.defaultDisplay
            val maxRate = currentDisplay?.supportedModes?.maxOfOrNull { it.refreshRate }
            if (maxRate != null) {
                val params = window.attributes
                if (params.preferredRefreshRate != maxRate) {
                    params.preferredRefreshRate = maxRate
                    window.attributes = params
                }
            }
        }
    }
}

