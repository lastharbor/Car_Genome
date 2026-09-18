package com.cargenome.app.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cargenome.app.BuildConfig
import com.cargenome.app.data.update.GitHubReleaseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int,
    ) : UpdateDownloadState
    data class Completed(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val owner: String = BuildConfig.GITHUB_REPO_OWNER
    private val repo: String = BuildConfig.GITHUB_REPO_NAME
    private val token: String = BuildConfig.GITHUB_UPDATE_TOKEN

    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("AppUpdateManager", "Starting check for update...")
            val url = "https://api.github.com/repos/$owner/$repo/releases"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CarGenome-App/${BuildConfig.VERSION_NAME}")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            android.util.Log.d("AppUpdateManager", "Response code: ${response.code}")
            if (!response.isSuccessful) {
                return@runCatching null
            }

            val bodyString = response.body.string()
            val releases = json.decodeFromString<List<GitHubReleaseDto>>(bodyString)
            android.util.Log.d("AppUpdateManager", "Found ${releases.size} releases")

            val currentVersion = AppVersion.parse(BuildConfig.VERSION_NAME)
            android.util.Log.d("AppUpdateManager", "Current version: $currentVersion")

            // Find the latest valid release with an APK asset that is newer than current
            val candidate = releases
                .filter { !it.draft && (!it.prerelease || BuildConfig.DEBUG) }
                .firstOrNull { release ->
                    val remoteVer = AppVersion.parse(release.tagName)
                    val isNewer = remoteVer.isNewerThan(currentVersion)
                    android.util.Log.d("AppUpdateManager", "Release ${release.tagName} isNewerThan $currentVersion: $isNewer")
                    isNewer
                }
            android.util.Log.d("AppUpdateManager", "Candidate selected: ${candidate?.tagName}")
            if (candidate == null) return@runCatching null

            val apkAsset = candidate.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            android.util.Log.d("AppUpdateManager", "ApkAsset: ${apkAsset?.name}")
            if (apkAsset == null) return@runCatching null

            val remoteVersion = AppVersion.parse(candidate.tagName)
            val updateType = remoteVersion.determineUpdateType(currentVersion) ?: UpdateType.Minor

            AppUpdateInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                newVersion = candidate.tagName.removePrefix("v").removePrefix("V"),
                updateType = updateType,
                releaseTitle = candidate.name?.takeIf { it.isNotBlank() } ?: candidate.tagName,
                releaseNotes = candidate.body?.trim() ?: "",
                publishedAt = candidate.publishedAt,
                assetId = apkAsset.id,
                assetName = apkAsset.name,
                assetSize = apkAsset.size,
                downloadUrl = apkAsset.browserDownloadUrl ?: apkAsset.url.orEmpty(),
            ).also {
                android.util.Log.d("AppUpdateManager", "Created update info: $it")
            }
        }.onFailure {
            android.util.Log.e("AppUpdateManager", "Error in checkForUpdate", it)
        }
    }

    fun downloadApk(info: AppUpdateInfo): Flow<UpdateDownloadState> = flow {
        emit(UpdateDownloadState.Downloading(0L, info.assetSize, 0))

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val apkFile = File(updatesDir, "CarGenome-v${info.newVersion}.apk")
        if (apkFile.exists() && info.assetSize > 0L && apkFile.length() == info.assetSize) {
            emit(UpdateDownloadState.Completed(apkFile))
            return@flow
        }

        val tempFile = File(updatesDir, "CarGenome-v${info.newVersion}.apk.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val assetUrl = if (info.assetId > 0L) {
            "https://api.github.com/repos/$owner/$repo/releases/assets/${info.assetId}"
        } else {
            info.downloadUrl
        }

        val requestBuilder = Request.Builder()
            .url(assetUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "CarGenome-App/${BuildConfig.VERSION_NAME}")

        if (token.isNotBlank() && info.assetId > 0L) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                emit(UpdateDownloadState.Error("HTTP error: ${response.code}"))
                return@flow
            }

            val body = response.body
            val totalBytes = if (info.assetSize > 0L) info.assetSize else body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var lastEmittedPercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastEmittedPercent || downloadedBytes == totalBytes) {
                            lastEmittedPercent = percent
                            emit(UpdateDownloadState.Downloading(downloadedBytes, totalBytes, percent))
                        }
                    }
                    output.flush()
                }
            }

            if (apkFile.exists()) {
                apkFile.delete()
            }
            tempFile.renameTo(apkFile)

            emit(UpdateDownloadState.Completed(apkFile))
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            emit(UpdateDownloadState.Error(e.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
