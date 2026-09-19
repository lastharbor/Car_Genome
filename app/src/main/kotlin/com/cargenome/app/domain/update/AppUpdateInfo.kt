package com.cargenome.app.domain.update

data class AppUpdateInfo(
    val currentVersion: String,
    val newVersion: String,
    val updateType: UpdateType,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String?,
    val assetId: Long,
    val assetName: String,
    val assetSize: Long,
    val downloadUrl: String,
    /** Hex SHA-256 of the APK asset, taken from the release's .sha256 companion asset. */
    val sha256: String,
)
