package com.cargenome.app.di

import com.cargenome.app.BuildConfig
import com.cargenome.app.data.network.AndroidNetworkMonitor
import com.cargenome.app.data.network.NetworkMonitor
import com.cargenome.app.data.network.NhtsaVpicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(20))
        .callTimeout(Duration.ofSeconds(30))
        // vPIC rejects requests that arrive without one.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build(),
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC),
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NhtsaVpicApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideVpicApi(retrofit: Retrofit): NhtsaVpicApi = retrofit.create(NhtsaVpicApi::class.java)

    @Provides
    @Singleton
    fun provideNetworkMonitor(monitor: AndroidNetworkMonitor): NetworkMonitor = monitor

    private val USER_AGENT = "CarGenome/${BuildConfig.VERSION_NAME} (Android)"
}
