package com.cargenome.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lightweight, CDN-hosted update manifest published with every release (`update.json`).
 *
 * This allows client apps to check for updates with a single HTTP request to
 * `https://github.com/{owner}/{repo}/releases/latest/download/update.json`, which:
 * 1. Bypasses GitHub REST API rate limits (60 req/hour on unauthenticated IP).
 * 2. Delivers instant response from GitHub's CDN.
 * 3. Contains all metadata needed for verification (SHA-256, size, download URL).
 */
@Serializable
data class AppUpdateManifestDto(
    @SerialName("versionCode") val versionCode: Int = 0,
    @SerialName("versionName") val versionName: String,
    @SerialName("tagName") val tagName: String = "v$versionName",
    @SerialName("apkName") val apkName: String = "",
    @SerialName("downloadUrl") val downloadUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long = 0L,
    @SerialName("changelog") val changelog: String = "",
    @SerialName("publishedAt") val publishedAt: String? = null,
    @SerialName("minSupportedVersion") val minSupportedVersion: String? = null,
)
