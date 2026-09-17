package com.cargenome.app.data.sync

import android.os.Build
import com.cargenome.app.data.export.DataBackupManager
import com.cargenome.app.data.settings.AppSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

sealed interface SyncResult<out T> {
    data class Success<T>(val data: T) : SyncResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult<Nothing>
}

@Singleton
class CloudSyncManager @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val backupManager: DataBackupManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private fun getRetrofit(rawUrl: String): Retrofit {
        val sanitized = rawUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(sanitized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private suspend fun getApiService(): Pair<SyncApiService, String?> {
        val settings = settingsRepository.settings.first()
        val retrofit = getRetrofit(settings.syncServerUrl)
        val api = retrofit.create(SyncApiService::class.java)
        return Pair(api, settings.syncAuthToken)
    }

    suspend fun register(serverUrl: String, email: String, pass: String): SyncResult<AuthResponseDto> {
        return try {
            val retrofit = getRetrofit(serverUrl)
            val api = retrofit.create(SyncApiService::class.java)
            val response = api.register(AuthRequestDto(email.trim(), pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                settingsRepository.setSyncServerUrl(serverUrl)
                settingsRepository.setSyncCredentials(body.email, body.token)
                SyncResult.Success(body)
            } else {
                val err = response.errorBody()?.string() ?: "Ошибка регистрации (${response.code()})"
                SyncResult.Error(err)
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Сетевая ошибка при регистрации", e)
        }
    }

    suspend fun login(serverUrl: String, email: String, pass: String): SyncResult<AuthResponseDto> {
        return try {
            val retrofit = getRetrofit(serverUrl)
            val api = retrofit.create(SyncApiService::class.java)
            val response = api.login(AuthRequestDto(email.trim(), pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                settingsRepository.setSyncServerUrl(serverUrl)
                settingsRepository.setSyncCredentials(body.email, body.token)
                SyncResult.Success(body)
            } else {
                val err = response.errorBody()?.string() ?: "Ошибка авторизации (${response.code()})"
                SyncResult.Error(err)
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Сетевая ошибка при авторизации", e)
        }
    }

    suspend fun logout() {
        settingsRepository.setSyncCredentials(null, null)
    }

    suspend fun getStatus(): SyncResult<SyncStatusResponseDto> {
        return try {
            val (api, token) = getApiService()
            if (token.isNullOrBlank()) {
                return SyncResult.Error("Требуется вход в аккаунт синхронизации")
            }
            val resp = api.getStatus("Bearer $token")
            if (resp.isSuccessful && resp.body() != null) {
                SyncResult.Success(resp.body()!!)
            } else {
                SyncResult.Error("Ошибка получения статуса: ${resp.code()}")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Ошибка связи с сервером", e)
        }
    }

    suspend fun pushData(): SyncResult<SyncPushResponseDto> {
        return try {
            val (api, token) = getApiService()
            if (token.isNullOrBlank()) {
                return SyncResult.Error("Требуется вход в аккаунт синхронизации")
            }
            val jsonBackup = backupManager.exportJson()
            val payloadObj = json.parseToJsonElement(jsonBackup).jsonObject
            val deviceId = "${Build.MANUFACTURER} ${Build.MODEL}"
            val resp = api.pushData("Bearer $token", SyncPushRequestDto(deviceId = deviceId, payload = payloadObj))
            if (resp.isSuccessful && resp.body() != null) {
                settingsRepository.setLastSyncTimestamp(System.currentTimeMillis())
                SyncResult.Success(resp.body()!!)
            } else {
                SyncResult.Error(resp.errorBody()?.string() ?: "Ошибка отправки данных (${resp.code()})")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Ошибка при отправке данных", e)
        }
    }

    suspend fun pullData(): SyncResult<SyncPullResponseDto> {
        return try {
            val (api, token) = getApiService()
            if (token.isNullOrBlank()) {
                return SyncResult.Error("Требуется вход в аккаунт синхронизации")
            }
            val resp = api.pullData("Bearer $token")
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                if (body.payload != null) {
                    val jsonStr = json.encodeToString(body.payload)
                    backupManager.clearAllData()
                    backupManager.importJson(jsonStr)
                    settingsRepository.setLastSyncTimestamp(System.currentTimeMillis())
                }
                SyncResult.Success(body)
            } else {
                SyncResult.Error(resp.errorBody()?.string() ?: "Ошибка загрузки данных (${resp.code()})")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Ошибка при загрузке данных", e)
        }
    }
}
