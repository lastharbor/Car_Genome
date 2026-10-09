package com.cargenome.app.domain.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateDownloadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val updatesDir: File
        get() = File(context.cacheDir, "updates")

    // Big enough for several progress steps with the 8 KiB copy buffer.
    private val apk = ByteArray(64 * 1024) { (it % 251).toByte() }

    @After
    fun tearDown() {
        updatesDir.deleteRecursively()
    }

    @Test
    fun aVerifiedDownloadIsStored() = runTest {
        val last = manager().downloadApk(info(sha256(apk))).toList().last()

        val completed = last as UpdateDownloadState.Completed
        assertArrayEquals(apk, completed.apkFile.readBytes())
        assertEquals(listOf(completed.apkFile.name), filesLeft())
    }

    @Test
    fun aChecksumMismatchKeepsNothing() = runTest {
        val last = manager().downloadApk(info("0".repeat(64))).toList().last()

        assertTrue(last is UpdateDownloadState.Error)
        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun closingTheDialogMidDownloadLeavesNoPartialFile() = runTest {
        // A slow collector: without buffering the download waits at every progress
        // update, so stopping early really does interrupt it half way.
        manager().downloadApk(info(sha256(apk)))
            .buffer(0)
            .first { it is UpdateDownloadState.Downloading && it.percent > 0 }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), filesLeft())
    }

    @Test
    fun theCollectorsOwnFailureIsNotTurnedIntoAnErrorState() = runTest {
        val boom = IllegalStateException("dialog failed")

        val thrown = runCatching {
            manager().downloadApk(info(sha256(apk))).collect { state ->
                if (state is UpdateDownloadState.Downloading && state.percent > 0) throw boom
            }
        }.exceptionOrNull()

        // Crossing flowOn's channel may copy the exception, so compare it by content.
        assertTrue(thrown is IllegalStateException)
        assertEquals(boom.message, thrown?.message)
    }

    private fun TestScope.manager() = AppUpdateManager(
        context = context,
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(apk.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build(),
        json = Json,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun info(sha256: String) = AppUpdateInfo(
        currentVersion = "1.0.0",
        newVersion = "9.9.9",
        updateType = UpdateType.Minor,
        releaseTitle = "CarGenome v9.9.9",
        releaseNotes = "",
        publishedAt = null,
        assetId = 0L,
        assetName = "CarGenome-v9.9.9.apk",
        assetSize = apk.size.toLong(),
        downloadUrl = "https://example.invalid/CarGenome-v9.9.9.apk",
        sha256 = sha256,
    )

    private fun filesLeft(): List<String> = updatesDir.list()?.sorted().orEmpty()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
