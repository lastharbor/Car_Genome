package com.cargenome.app.domain.update

import com.cargenome.app.data.update.AppUpdateManifestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManifestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun deserializesManifestSuccessfully() {
        val jsonString = """
            {
              "versionCode": 15,
              "versionName": "1.1.13",
              "tagName": "v1.1.13",
              "apkName": "CarGenome-v1.1.13.apk",
              "downloadUrl": "https://github.com/lastharbor/Car_Genome/releases/download/v1.1.13/CarGenome-v1.1.13.apk",
              "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              "size": 45000000,
              "changelog": "New features and bug fixes",
              "publishedAt": "2026-10-08T12:00:00Z"
            }
        """.trimIndent()

        val manifest = json.decodeFromString<AppUpdateManifestDto>(jsonString)

        assertEquals(15, manifest.versionCode)
        assertEquals("1.1.13", manifest.versionName)
        assertEquals("v1.1.13", manifest.tagName)
        assertEquals("CarGenome-v1.1.13.apk", manifest.apkName)
        assertEquals("https://github.com/lastharbor/Car_Genome/releases/download/v1.1.13/CarGenome-v1.1.13.apk", manifest.downloadUrl)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.sha256)
        assertEquals(45000000L, manifest.size)
        assertEquals("New features and bug fixes", manifest.changelog)
        assertEquals("2026-10-08T12:00:00Z", manifest.publishedAt)

        val remoteVersion = AppVersion.parse(manifest.versionName)
        val currentVersion = AppVersion.parse("1.1.12")
        assertTrue(remoteVersion.isNewerThan(currentVersion))
        assertEquals(UpdateType.Patch, remoteVersion.determineUpdateType(currentVersion))
    }

    @Test
    fun handlesManifestWithSameOrOlderVersion() {
        val currentVersion = AppVersion.parse("1.1.12")
        val manifestSame = AppUpdateManifestDto(
            versionCode = 14,
            versionName = "1.1.12",
            downloadUrl = "https://example.com/app.apk",
            sha256 = "abc",
        )
        val manifestOlder = AppUpdateManifestDto(
            versionCode = 13,
            versionName = "1.1.11",
            downloadUrl = "https://example.com/app.apk",
            sha256 = "abc",
        )

        assertFalse(AppVersion.parse(manifestSame.versionName).isNewerThan(currentVersion))
        assertFalse(AppVersion.parse(manifestOlder.versionName).isNewerThan(currentVersion))
    }
}
