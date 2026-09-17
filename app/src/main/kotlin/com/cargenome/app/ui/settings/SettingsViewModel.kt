package com.cargenome.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cargenome.app.R
import com.cargenome.app.data.demo.DemoDataSeeder
import com.cargenome.app.data.export.DataBackupManager
import com.cargenome.app.data.settings.AppSettings
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.data.settings.ThemeMode
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.domain.service.MaintenanceAlarmScheduler
import com.cargenome.app.domain.service.MaintenanceReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.cargenome.app.data.sync.CloudSyncManager
import com.cargenome.app.data.sync.SyncResult

sealed interface SettingsEvent {
    data class Success(val messageRes: Int, val arg: String? = null) : SettingsEvent
    data class Error(val messageRes: Int, val arg: String? = null) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: AppSettingsRepository,
    private val backupManager: DataBackupManager,
    private val demoSeeder: DemoDataSeeder,
    private val alarmScheduler: MaintenanceAlarmScheduler,
    private val reminderScheduler: MaintenanceReminderScheduler,
    private val cloudSyncManager: CloudSyncManager,
    private val premiumManager: com.cargenome.app.domain.premium.PremiumManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppSettings(),
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _event = MutableStateFlow<SettingsEvent?>(null)
    val event: StateFlow<SettingsEvent?> = _event.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAmoledDark(enabled) }
    }

    fun setPersistentMaintenanceNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setPersistentMaintenanceNotification(enabled)
            alarmScheduler.rescheduleAllAlarms()
            reminderScheduler.runImmediately()
        }
    }

    fun setMaintenanceReminderIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepo.setMaintenanceReminderIntervalMinutes(minutes)
            alarmScheduler.rescheduleAllAlarms()
            reminderScheduler.runImmediately()
        }
    }

    fun setMaintenanceReminderStartTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepo.setMaintenanceReminderStartTimeMinutes(minutes)
            alarmScheduler.rescheduleAllAlarms()
            reminderScheduler.runImmediately()
        }
    }

    fun setDefaultDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settingsRepo.setDefaultDistanceUnit(unit) }
    }

    fun setDefaultVolumeUnit(unit: VolumeUnit) {
        viewModelScope.launch { settingsRepo.setDefaultVolumeUnit(unit) }
    }

    fun setDefaultCurrencyCode(code: String) {
        viewModelScope.launch { settingsRepo.setDefaultCurrencyCode(code) }
    }

    fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = backupManager.exportJson()
                    outputStream.use { it.write(json.toByteArray()) }
                }
            }.onSuccess {
                _event.value = SettingsEvent.Success(R.string.settings_backup_exported)
            }.onFailure {
                _event.value = SettingsEvent.Error(R.string.settings_export_failed, it.message ?: "")
            }
        }
    }

    fun importBackup(inputStream: InputStream) {
        viewModelScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = inputStream.bufferedReader().use { it.readText() }
                    backupManager.importJson(json)
                }
            }.onSuccess {
                _event.value = SettingsEvent.Success(R.string.settings_backup_restored)
            }.onFailure {
                _event.value = SettingsEvent.Error(R.string.settings_restore_failed, it.message ?: "")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            runCatching {
                settingsRepo.setSelectedVehicleId(null)
                backupManager.clearAllData()
            }.onSuccess {
                _event.value = SettingsEvent.Success(R.string.settings_data_cleared)
            }.onFailure {
                _event.value = SettingsEvent.Error(R.string.settings_clear_failed, it.message ?: "")
            }
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            runCatching {
                demoSeeder.seedDemoVehicle()
            }.onSuccess {
                _event.value = SettingsEvent.Success(R.string.settings_demo_seeded)
            }.onFailure {
                _event.value = SettingsEvent.Error(R.string.settings_seed_failed, it.message ?: "")
            }
        }
    }

    fun clearEvent() {
        _event.value = null
    }

    fun setSyncServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepo.setSyncServerUrl(url)
        }
    }

    fun loginSync(serverUrl: String, email: String, pass: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = cloudSyncManager.login(serverUrl, email, pass)) {
                is SyncResult.Success -> {
                    _event.value = SettingsEvent.Success(R.string.settings_sync_logged_in_as, res.data.email)
                }
                is SyncResult.Error -> {
                    _event.value = SettingsEvent.Error(R.string.settings_sync_login, res.message)
                }
            }
            _isSyncing.value = false
        }
    }

    fun registerSync(serverUrl: String, email: String, pass: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = cloudSyncManager.register(serverUrl, email, pass)) {
                is SyncResult.Success -> {
                    _event.value = SettingsEvent.Success(R.string.settings_sync_logged_in_as, res.data.email)
                }
                is SyncResult.Error -> {
                    _event.value = SettingsEvent.Error(R.string.settings_sync_register, res.message)
                }
            }
            _isSyncing.value = false
        }
    }

    fun logoutSync() {
        viewModelScope.launch {
            cloudSyncManager.logout()
        }
    }

    fun pushSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = cloudSyncManager.pushData()) {
                is SyncResult.Success -> {
                    _event.value = SettingsEvent.Success(R.string.settings_sync_push_success)
                }
                is SyncResult.Error -> {
                    _event.value = SettingsEvent.Error(R.string.settings_sync_push, res.message)
                }
            }
            _isSyncing.value = false
        }
    }

    fun pullSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = cloudSyncManager.pullData()) {
                is SyncResult.Success -> {
                    alarmScheduler.rescheduleAllAlarms()
                    reminderScheduler.runImmediately()
                    _event.value = SettingsEvent.Success(R.string.settings_sync_pull_success)
                }
                is SyncResult.Error -> {
                    _event.value = SettingsEvent.Error(R.string.settings_sync_pull, res.message)
                }
            }
            _isSyncing.value = false
        }
    }

    fun redeemPromoCode(code: String) {
        viewModelScope.launch {
            when (premiumManager.redeemCode(code)) {
                is com.cargenome.app.domain.premium.RedeemResult.Success -> {
                    _event.value = SettingsEvent.Success(R.string.settings_promo_success)
                }
                is com.cargenome.app.domain.premium.RedeemResult.Expired -> {
                    _event.value = SettingsEvent.Error(R.string.settings_promo_expired)
                }
                is com.cargenome.app.domain.premium.RedeemResult.InvalidCode -> {
                    _event.value = SettingsEvent.Error(R.string.settings_promo_invalid)
                }
            }
        }
    }
}

