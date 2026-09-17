package com.cargenome.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponseDto(
    val token: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val email: String,
    @SerialName("user_id") val userId: Int,
)

@Serializable
data class SyncPushRequestDto(
    @SerialName("device_id") val deviceId: String,
    val payload: JsonObject,
)

@Serializable
data class SyncPushResponseDto(
    val status: String,
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SyncPullResponseDto(
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String,
    val payload: JsonObject? = null,
)

@Serializable
data class SyncStatusResponseDto(
    @SerialName("has_data") val hasData: Boolean,
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String? = null,
    val email: String,
)
