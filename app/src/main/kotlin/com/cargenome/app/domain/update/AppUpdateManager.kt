package com.cargenome.app.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cargenome.app.BuildConfig
import com.cargenome.app.data.update.AppUpdateManifestDto
import com.cargenome.app.data.update.GitHubAssetDto
import com.cargenome.app.data.update.GitHubReleaseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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

    /**
     * Checks for updates using a 2-tier resilient approach:
     * 1. High-speed direct CDN check: `https://github.com/$owner/$repo/releases/latest/download/update.json`
     *    - Free of GitHub REST API rate limits (bypasses 60 req/hr anonymous limit).
     *    - Instant response from GitHub CDN.
     * 2. REST API fallback: `https://api.github.com/repos/$owner/$repo/releases/latest` or `/releases`
     *    - Used if `update.json` is missing or on older releases.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("AppUpdateManager", "Starting check for update...")
            val currentVersion = AppVersion.parse(BuildConfig.VERSION_NAME)

            // Step 1: Try direct CDN update manifest (rate-limit immune)
            val manifestResult = fetchCdnManifest(currentVersion)
            if (manifestResult != null) {
                return@runCatching manifestResult.getOrNull()
            }

            // Step 2: Fallback to GitHub REST API
            fetchFromGitHubApi(currentVersion)
        }.onFailure {
            android.util.Log.e("AppUpdateManager", "Error in checkForUpdate", it)
        }
    }

    private fun fetchCdnManifest(currentVersion: AppVersion): Result<AppUpdateInfo?>? {
        val manifestUrl = "https://github.com/$owner/$repo/releases/latest/download/update.json"
        val request = Request.Builder()
            .url(manifestUrl)
            .header("User-Agent", "CarGenome-App/${BuildConfig.VERSION_NAME}")
            .build()

        return runCatching {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.d("AppUpdateManager", "CDN manifest returned ${response.code}, falling back to REST API")
                return null
            }

            val bodyString = response.body.string()
            val manifest = json.decodeFromString<AppUpdateManifestDto>(bodyString)
            val remoteVer = AppVersion.parse(manifest.versionName)
            val isNewer = remoteVer.isNewerThan(currentVersion)
            android.util.Log.d("AppUpdateManager", "CDN manifest version: ${manifest.versionName}, isNewer: $isNewer")

            if (!isNewer) return Result.success(null)

            val updateType = remoteVer.determineUpdateType(currentVersion) ?: UpdateType.Minor
            val info = AppUpdateInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                newVersion = manifest.versionName.removePrefix("v").removePrefix("V"),
                updateType = updateType,
                releaseTitle = "CarGenome v${manifest.versionName}",
                releaseNotes = manifest.changelog,
                publishedAt = manifest.publishedAt,
                assetId = 0L,
                assetName = manifest.apkName.ifBlank { "CarGenome-${manifest.tagName}.apk" },
                assetSize = manifest.size,
                downloadUrl = manifest.downloadUrl,
                sha256 = manifest.sha256.trim().lowercase(),
            )
            Result.success(info)
        }.getOrNull()
    }

    private fun fetchFromGitHubApi(currentVersion: AppVersion): AppUpdateInfo? {
        val url = "https://api.github.com/repos/$owner/$repo/releases"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "CarGenome-App/${BuildConfig.VERSION_NAME}")

        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errorMsg = when (response.code) {
                404 -> "Репозиторий не найден или является приватным (HTTP 404)"
                401, 403 -> "Доступ к GitHub API ограничен (HTTP ${response.code}). Попробуйте позже."
                else -> "Ошибка запроса к GitHub API: HTTP ${response.code}"
            }
            android.util.Log.e("AppUpdateManager", errorMsg)
            throw java.io.IOException(errorMsg)
        }

        val bodyString = response.body.string()
        val releases = json.decodeFromString<List<GitHubReleaseDto>>(bodyString)

        val candidate = releases
            .filter { !it.draft && (!it.prerelease || BuildConfig.DEBUG) }
            .firstOrNull { release ->
                val remoteVer = AppVersion.parse(release.tagName)
                remoteVer.isNewerThan(currentVersion)
            } ?: return null

        val apkAsset = candidate.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null

        val remoteVersion = AppVersion.parse(candidate.tagName)
        val updateType = remoteVersion.determineUpdateType(currentVersion) ?: UpdateType.Minor

        return AppUpdateInfo(
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
            sha256 = fetchChecksum(candidate.assets, apkAsset.name, candidate.tagName).orEmpty(),
        )
    }

    private fun fetchChecksum(assets: List<GitHubAssetDto>, apkName: String, tagName: String = ""): String? {
        val asset = assets.firstOrNull { it.name.equals("$apkName.sha256", ignoreCase = true) }

        // Prefer direct browser download url from CDN if token is absent
        val url = if (token.isNotBlank() && asset != null && asset.id > 0L) {
            "https://api.github.com/repos/$owner/$repo/releases/assets/${asset.id}"
        } else {
            asset?.browserDownloadUrl
                ?: if (tagName.isNotBlank()) "https://github.com/$owner/$repo/releases/download/$tagName/$apkName.sha256" else null
        } ?: return null

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "CarGenome-App/${BuildConfig.VERSION_NAME}")
        if (token.isNotBlank() && asset != null && asset.id > 0L) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return runCatching {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string().trim().substringBefore(' ').lowercase()
                    .takeIf { hex -> hex.length == 64 && hex.all { it in "0123456789abcdef" } }
            }
        }.onFailure {
            android.util.Log.e("AppUpdateManager", "Error fetching checksum for $apkName", it)
        }.getOrNull()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun downloadApk(info: AppUpdateInfo): Flow<UpdateDownloadState> = flow {
        if (info.sha256.isBlank()) {
            emit(UpdateDownloadState.Error("Release publishes no SHA-256 checksum"))
            return@flow
        }

        emit(UpdateDownloadState.Downloading(0L, info.assetSize, 0))

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val apkFile = File(updatesDir, "CarGenome-v${info.newVersion}.apk")
        if (apkFile.exists()) {
            if (sha256Of(apkFile).equals(info.sha256, ignoreCase = true)) {
                emit(UpdateDownloadState.Completed(apkFile))
                return@flow
            }
            apkFile.delete()
        }

        val tempFile = File(updatesDir, "CarGenome-v${info.newVersion}.apk.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val assetUrl = if (token.isNotBlank() && info.assetId > 0L) {
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

            val actualSha256 = sha256Of(tempFile)
            if (!actualSha256.equals(info.sha256, ignoreCase = true)) {
                tempFile.delete()
                emit(UpdateDownloadState.Error("Checksum mismatch, the download was discarded"))
                return@flow
            }

            if (apkFile.exists()) {
                apkFile.delete()
            }
            if (!tempFile.renameTo(apkFile)) {
                tempFile.delete()
                emit(UpdateDownloadState.Error("Could not store the verified download"))
                return@flow
            }

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
