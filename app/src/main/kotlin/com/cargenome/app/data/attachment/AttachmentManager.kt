package com.cargenome.app.data.attachment

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile,
        )
    }

    suspend fun saveAttachment(
        sourceUri: Uri,
        ownerType: AttachmentOwner,
        ownerId: Long,
        displayName: String? = null,
    ): AttachmentEntity {
        val filename = "att_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val destFile = File(attachmentsDir, filename)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Clean up temporary camera capture if it was saved from cacheDir
        cleanTempCameraFile(sourceUri)

        val destUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile,
        )

        return AttachmentEntity(
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
    ): AttachmentEntity {
        val safeName = displayName?.substringBeforeLast('.')?.take(25)?.replace(Regex("[^a-zA-Z0-9а-яА-Я_\\-]"), "_") ?: "policy"
        val filename = "pdf_${safeName}_${System.currentTimeMillis()}.pdf"
        val destFile = File(attachmentsDir, filename)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val destUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile,
        )

        return AttachmentEntity(
            ownerType = ownerType,
            ownerId = ownerId,
            uri = destUri.toString(),
            displayName = displayName ?: filename,
            mimeType = "application/pdf",
            sizeBytes = destFile.length(),
            addedAt = Instant.now(),
        )
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
