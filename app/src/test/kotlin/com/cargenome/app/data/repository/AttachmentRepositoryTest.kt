package com.cargenome.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val attachmentsDir = File(context.filesDir, "attachments")
    private lateinit var database: CarGenomeDatabase
    private lateinit var repository: AttachmentRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, CarGenomeDatabase::class.java).build()
        repository = AttachmentRepository(
            database.attachmentDao(),
            // FileProvider rejects Windows paths; hand out URIs of the same shape it would.
            AttachmentManager(context, Dispatchers.Unconfined) { file ->
                Uri.parse("content://${context.packageName}.fileprovider/attachments/${file.name}")
            },
        )
        attachmentsDir.deleteRecursively()
        attachmentsDir.mkdirs()
    }

    @After
    fun tearDown() {
        database.close()
        attachmentsDir.deleteRecursively()
    }

    @Test
    fun syncCopiesNewSourcesAndDropsRemovedAttachments() = runTest {
        val old = storedAttachment("old.jpg")

        repository.syncForOwner(AttachmentOwner.FuelRecord, OWNER_ID, listOf(source("new.jpg")))

        val rows = database.attachmentDao().listForOwner(AttachmentOwner.FuelRecord, OWNER_ID)
        assertEquals(1, rows.size)
        assertTrue(rows.single().uri != old.uri)
        assertFalse(File(attachmentsDir, "old.jpg").exists())
        assertEquals(listOf("receipt"), attachmentsDir.listFiles()!!.map { it.readText() })
    }

    @Test
    fun anUnreadableSourceLeavesStoredAttachmentsUntouched() = runTest {
        val old = storedAttachment("old.jpg")
        val missing = Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString()

        val result = runCatching {
            repository.syncForOwner(AttachmentOwner.FuelRecord, OWNER_ID, listOf(source("new.jpg"), missing))
        }

        assertTrue(result.isFailure)
        assertEquals(listOf(old.uri), database.attachmentDao().listForOwner(AttachmentOwner.FuelRecord, OWNER_ID).map { it.uri })
        // Only the untouched original is left: the copy of new.jpg was cleaned up.
        assertEquals(listOf("old.jpg"), attachmentsDir.listFiles()!!.map { it.name })
    }

    private suspend fun storedAttachment(fileName: String): AttachmentEntity {
        File(attachmentsDir, fileName).writeText("old")
        val attachment = AttachmentEntity(
            ownerType = AttachmentOwner.FuelRecord,
            ownerId = OWNER_ID,
            uri = "content://${context.packageName}.fileprovider/attachments/$fileName",
            addedAt = Instant.parse("2025-03-01T12:00:00Z"),
        )
        database.attachmentDao().insert(attachment)
        return attachment
    }

    private fun source(fileName: String): String {
        val file = File(context.cacheDir, fileName).apply { writeText("receipt") }
        return Uri.fromFile(file).toString()
    }

    private companion object {
        const val OWNER_ID = 10L
    }
}
