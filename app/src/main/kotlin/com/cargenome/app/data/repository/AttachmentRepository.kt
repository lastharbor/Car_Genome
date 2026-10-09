package com.cargenome.app.data.repository

import androidx.core.net.toUri
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.dao.AttachmentDao
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AttachmentRepository @Inject constructor(
    private val dao: AttachmentDao,
    private val attachmentManager: AttachmentManager? = null,
) {
    fun observe(ownerType: AttachmentOwner, ownerId: Long): Flow<List<AttachmentEntity>> =
        dao.observeForOwner(ownerType, ownerId)

    suspend fun listForOwner(ownerType: AttachmentOwner, ownerId: Long): List<AttachmentEntity> =
        dao.listForOwner(ownerType, ownerId)

    suspend fun add(attachment: AttachmentEntity): Long = dao.insert(attachment)

    suspend fun delete(attachment: AttachmentEntity) {
        attachmentManager?.deleteAttachmentFile(attachment.uri)
        dao.delete(attachment)
    }

    /**
     * Makes an owner's attachments match [uris], as an editor's save needs.
     *
     * New sources are copied in before any row changes. If one cannot be read,
     * the copies made so far are removed and the call throws with the stored
     * attachments untouched. Files of dropped attachments go only after their
     * rows have.
     */
    suspend fun syncForOwner(ownerType: AttachmentOwner, ownerId: Long, uris: List<String>) {
        val manager = checkNotNull(attachmentManager) { "AttachmentManager is required to save attachments" }
        val existing = dao.listForOwner(ownerType, ownerId)
        val existingUris = existing.mapTo(HashSet()) { it.uri }
        val removed = existing.filter { it.uri !in uris }
        val added = mutableListOf<AttachmentEntity>()
        try {
            for (uri in uris.distinct()) {
                if (uri !in existingUris) {
                    added += manager.saveAttachment(uri.toUri(), ownerType, ownerId)
                }
            }
            dao.swap(added = added, removed = removed)
        } catch (e: Exception) {
            added.forEach { manager.deleteAttachmentFile(it.uri) }
            throw e
        }
        removed.forEach { manager.deleteAttachmentFile(it.uri) }
    }

    suspend fun deleteForOwner(ownerType: AttachmentOwner, ownerId: Long) {
        val items = dao.listForOwner(ownerType, ownerId)
        for (item in items) {
            attachmentManager?.deleteAttachmentFile(item.uri)
        }
        dao.deleteForOwner(ownerType, ownerId)
    }

    suspend fun deleteForVehicle(vehicleId: Long) {
        val items = dao.listForVehicle(vehicleId)
        for (item in items) {
            attachmentManager?.deleteAttachmentFile(item.uri)
        }
        dao.deleteForVehicle(vehicleId)
    }
}
