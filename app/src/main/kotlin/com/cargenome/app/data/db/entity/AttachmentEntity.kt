package com.cargenome.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Which kind of record an attachment hangs off. */
enum class AttachmentOwner {
    Vehicle,
    FuelRecord,
    ServiceRecord,
    Expense,
}

/**
 * A receipt or photo.
 *
 * The owner is a type plus a row id rather than a foreign key, because one
 * table has to serve four parents. Deletes are handled in the repositories,
 * which remove attachments alongside the record they belong to.
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["ownerType", "ownerId"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val ownerType: AttachmentOwner,
    val ownerId: Long,

    /** Content URI the app holds a persisted read permission for. */
    val uri: String,
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val addedAt: Instant,
)
