package com.cargenome.app.data.repository

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
