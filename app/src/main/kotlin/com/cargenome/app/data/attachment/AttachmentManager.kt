package com.cargenome.app.data.attachment

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class AttachmentManager internal constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    /** The URI other apps can open a stored file by. A seam because FileProvider only understands '/' paths. */
    private val shareableUri: (File) -> Uri,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context, ioDispatcher, { file ->
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    })

    private val attachmentsDir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    private val cameraDir: File
        get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun createTempCameraUri(): Uri {
        cameraDir.mkdirs()
        // Prune zero-byte or stale temporary camera captures
        cameraDir.listFiles()?.forEach { file ->
            if (file.length() == 0L || System.currentTimeMillis() - file.lastModified() > 24 * 3600 * 1000L) {
                file.delete()
            }
        }
        val tempFile = File(cameraDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        if (!tempFile.exists()) {
            tempFile.createNewFile()
        }
        return shareableUri(tempFile)
    }

    suspend fun saveAttachment(
        sourceUri: Uri,
        ownerType: AttachmentOwner,
        ownerId: Long,
        displayName: String? = null,
    ): AttachmentEntity = withContext(ioDispatcher) {
        val filename = "att_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val destFile = File(attachmentsDir, filename)

        val destUri = storeAs(destFile, sourceUri)

        // Clean up temporary camera capture if it was saved from cacheDir
        cleanTempCameraFile(sourceUri)

        AttachmentEntity(
            ownerType = ownerType,
            ownerId = ownerId,
            uri = destUri.toString(),
            displayName = displayName ?: filename,
            mimeType = "image/jpeg",
            sizeBytes = destFile.length(),
            addedAt = Instant.now(),
        )
    }

    suspend fun savePdfAttachment(
        sourceUri: Uri,
        ownerType: AttachmentOwner,
        ownerId: Long,
        displayName: String? = null,
    ): AttachmentEntity = withContext(ioDispatcher) {
        val safeName = displayName?.substringBeforeLast('.')?.take(25)?.replace(Regex("[^a-zA-Z0-9а-яА-Я_\\-]"), "_") ?: "policy"
        val filename = "pdf_${safeName}_${System.currentTimeMillis()}.pdf"
        val destFile = File(attachmentsDir, filename)

        val destUri = storeAs(destFile, sourceUri)

        AttachmentEntity(
            ownerType = ownerType,
            ownerId = ownerId,
            uri = destUri.toString(),
            displayName = displayName ?: filename,
            mimeType = "application/pdf",
            sizeBytes = destFile.length(),
            addedAt = Instant.now(),
        )
    }

    /**
     * Copies [source] to [destFile] and returns the stored file's URI. A source
     * that cannot be opened used to leave an empty file recorded as a valid
     * attachment; now any failure throws and leaves no file behind.
     */
    private fun storeAs(destFile: File, source: Uri): Uri {
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw FileNotFoundException("Cannot open $source")
            input.use { from -> destFile.outputStream().use { to -> from.copyTo(to) } }
            return shareableUri(destFile)
        } catch (e: Exception) {
            destFile.delete()
            throw e
        }
    }

    fun deleteAttachmentFile(uriString: String) {
        runCatching {
            val uri = uriString.toUri()
            val name = uri.lastPathSegment ?: return
            val file = File(attachmentsDir, name)
            if (file.exists()) file.delete()
        }
    }

    fun cleanTempCameraFile(uri: Uri?) {
        if (uri == null) return
        runCatching {
            val name = uri.lastPathSegment ?: return
            val file = File(cameraDir, name)
            if (file.exists()) file.delete()
        }
    }

    fun deleteAllAttachments() {
        runCatching {
            attachmentsDir.listFiles()?.forEach { it.delete() }
            cameraDir.listFiles()?.forEach { it.delete() }
        }
    }
}
