package com.cargenome.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = true,
    val defaultDistanceUnit: DistanceUnit = DistanceUnit.Kilometres,
    val defaultVolumeUnit: VolumeUnit = VolumeUnit.Litres,
    val defaultCurrencyCode: String = "RUB",
    val selectedVehicleId: Long? = null,
    val persistentMaintenanceNotification: Boolean = true,
    val maintenanceReminderIntervalMinutes: Int = 30,
    val maintenanceReminderStartTimeMinutes: Int = 9 * 60,
    val syncServerUrl: String = "http://10.0.2.2:8000",
    val syncUserEmail: String? = null,
    val syncAuthToken: String? = null,
    val lastSyncTimestamp: Long? = null,
    val autoSyncEnabled: Boolean = false,
    val isPremiumPurchased: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckTimestamp: Long? = null,
) {
    val isPremiumActive: Boolean
        get() {
            if (com.cargenome.app.BuildConfig.IS_PREMIUM) return true
            if (isPremiumPurchased) return true
            return false
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cargenome_settings")

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val DEFAULT_DISTANCE_UNIT = stringPreferencesKey("default_distance_unit")
        val DEFAULT_VOLUME_UNIT = stringPreferencesKey("default_volume_unit")
        val DEFAULT_CURRENCY_CODE = stringPreferencesKey("default_currency_code")
        val SELECTED_VEHICLE_ID = longPreferencesKey("selected_vehicle_id")
        val PERSISTENT_MAINTENANCE_NOTIFICATION = booleanPreferencesKey("persistent_maintenance_notification")
        val MAINTENANCE_REMINDER_INTERVAL_MINUTES = intPreferencesKey("maintenance_reminder_interval_minutes")
        val MAINTENANCE_REMINDER_START_TIME_MINUTES = intPreferencesKey("maintenance_reminder_start_time_minutes")
        val SYNC_SERVER_URL = stringPreferencesKey("sync_server_url")
        val SYNC_USER_EMAIL = stringPreferencesKey("sync_user_email")
        val SYNC_AUTH_TOKEN = stringPreferencesKey("sync_auth_token")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val IS_PREMIUM_PURCHASED = booleanPreferencesKey("is_premium_purchased")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK_TIMESTAMP = longPreferencesKey("last_update_check_timestamp")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeStr = preferences[Keys.THEME_MODE]
        val themeMode = runCatching { ThemeMode.valueOf(themeStr ?: "") }.getOrDefault(ThemeMode.System)
        val dynamic = preferences[Keys.DYNAMIC_COLOR] ?: true
        val amoled = preferences[Keys.AMOLED_DARK] ?: true

        val distStr = preferences[Keys.DEFAULT_DISTANCE_UNIT]
        val distUnit = runCatching { DistanceUnit.valueOf(distStr ?: "") }.getOrDefault(DistanceUnit.Kilometres)

        val volStr = preferences[Keys.DEFAULT_VOLUME_UNIT]
        val volUnit = runCatching { VolumeUnit.valueOf(volStr ?: "") }.getOrDefault(VolumeUnit.Litres)

        val currency = preferences[Keys.DEFAULT_CURRENCY_CODE] ?: "RUB"
        val vehicleId = preferences[Keys.SELECTED_VEHICLE_ID]
        val persistent = preferences[Keys.PERSISTENT_MAINTENANCE_NOTIFICATION] ?: true
        val reminderInterval = preferences[Keys.MAINTENANCE_REMINDER_INTERVAL_MINUTES] ?: 30
        val reminderStartTime = preferences[Keys.MAINTENANCE_REMINDER_START_TIME_MINUTES] ?: (9 * 60)

        val syncServerUrl = preferences[Keys.SYNC_SERVER_URL] ?: "http://10.0.2.2:8000"
        val syncUserEmail = preferences[Keys.SYNC_USER_EMAIL]
        val syncAuthToken = preferences[Keys.SYNC_AUTH_TOKEN]
        val lastSyncTimestamp = preferences[Keys.LAST_SYNC_TIMESTAMP]
        val autoSyncEnabled = preferences[Keys.AUTO_SYNC_ENABLED] ?: false
        val isPurchased = preferences[Keys.IS_PREMIUM_PURCHASED] ?: false
        val autoCheckUpdates = preferences[Keys.AUTO_CHECK_UPDATES] ?: true
        val lastUpdateCheck = preferences[Keys.LAST_UPDATE_CHECK_TIMESTAMP]

        AppSettings(
            themeMode = themeMode,
            dynamicColor = dynamic,
            amoledDark = amoled,
            defaultDistanceUnit = distUnit,
            defaultVolumeUnit = volUnit,
            defaultCurrencyCode = currency,
            selectedVehicleId = vehicleId,
            persistentMaintenanceNotification = persistent,
            maintenanceReminderIntervalMinutes = reminderInterval,
            maintenanceReminderStartTimeMinutes = reminderStartTime,
            syncServerUrl = syncServerUrl,
            syncUserEmail = syncUserEmail,
            syncAuthToken = syncAuthToken,
            lastSyncTimestamp = lastSyncTimestamp,
            autoSyncEnabled = autoSyncEnabled,
            isPremiumPurchased = isPurchased,
            autoCheckUpdates = autoCheckUpdates,
            lastUpdateCheckTimestamp = lastUpdateCheck,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setDefaultDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[Keys.DEFAULT_DISTANCE_UNIT] = unit.name }
    }

    suspend fun setDefaultVolumeUnit(unit: VolumeUnit) {
        context.dataStore.edit { it[Keys.DEFAULT_VOLUME_UNIT] = unit.name }
    }

    suspend fun setDefaultCurrencyCode(code: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CURRENCY_CODE] = code.trim().uppercase() }
    }

    suspend fun setSelectedVehicleId(vehicleId: Long?) {
        context.dataStore.edit { prefs ->
            if (vehicleId != null) {
                prefs[Keys.SELECTED_VEHICLE_ID] = vehicleId
            } else {
                prefs.remove(Keys.SELECTED_VEHICLE_ID)
            }
        }
    }

    suspend fun setPersistentMaintenanceNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_MAINTENANCE_NOTIFICATION] = enabled }
    }

    suspend fun setMaintenanceReminderIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.MAINTENANCE_REMINDER_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setMaintenanceReminderStartTimeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.MAINTENANCE_REMINDER_START_TIME_MINUTES] = minutes.coerceIn(0, 23 * 60 + 59) }
    }

    suspend fun setSyncServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SYNC_SERVER_URL] = url.trim().removeSuffix("/") }
    }

    suspend fun setSyncCredentials(email: String?, token: String?) {
        context.dataStore.edit { prefs ->
            if (email != null) prefs[Keys.SYNC_USER_EMAIL] = email else prefs.remove(Keys.SYNC_USER_EMAIL)
            if (token != null) prefs[Keys.SYNC_AUTH_TOKEN] = token else prefs.remove(Keys.SYNC_AUTH_TOKEN)
        }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long?) {
        context.dataStore.edit { prefs ->
            if (timestamp != null) prefs[Keys.LAST_SYNC_TIMESTAMP] = timestamp else prefs.remove(Keys.LAST_SYNC_TIMESTAMP)
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setPremiumPurchased(purchased: Boolean) {
        context.dataStore.edit { it[Keys.IS_PREMIUM_PURCHASED] = purchased }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setLastUpdateCheckTimestamp(timestamp: Long?) {
        context.dataStore.edit { prefs ->
            if (timestamp != null) {
                prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP] = timestamp
            } else {
                prefs.remove(Keys.LAST_UPDATE_CHECK_TIMESTAMP)
            }
        }
    }
}

