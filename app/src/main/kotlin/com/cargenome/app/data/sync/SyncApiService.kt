package com.cargenome.app.data.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface SyncApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @GET("api/v1/sync/status")
    suspend fun getStatus(@Header("Authorization") authHeader: String): Response<SyncStatusResponseDto>

    @POST("api/v1/sync/push")
    suspend fun pushData(
        @Header("Authorization") authHeader: String,
        @Body request: SyncPushRequestDto,
    ): Response<SyncPushResponseDto>

    @GET("api/v1/sync/pull")
    suspend fun pullData(@Header("Authorization") authHeader: String): Response<SyncPullResponseDto>
}
