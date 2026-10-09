package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query(
        """
        SELECT * FROM attachments
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        ORDER BY addedAt ASC, id ASC
        """,
    )
    fun observeForOwner(ownerType: AttachmentOwner, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query(
        """
        SELECT * FROM attachments
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """,
    )
    suspend fun listForOwner(ownerType: AttachmentOwner, ownerId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: AttachmentOwner, ownerId: Long)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()

    /**
     * Everything attached to a car, including its fuel, service and expense
     * records. Deleting the car cascades those child rows away, but attachments
     * are linked by owner type rather than a foreign key, so they need clearing
     * by hand or they would outlive the records they describe.
     */
    suspend fun listForVehicle(vehicleId: Long): List<AttachmentEntity> = listForVehicle(
        vehicleId = vehicleId,
        vehicleOwner = AttachmentOwner.Vehicle,
        fuelOwner = AttachmentOwner.FuelRecord,
        serviceOwner = AttachmentOwner.ServiceRecord,
        expenseOwner = AttachmentOwner.Expense,
    )

    suspend fun deleteForVehicle(vehicleId: Long) = deleteForVehicle(
        vehicleId = vehicleId,
        vehicleOwner = AttachmentOwner.Vehicle,
        fuelOwner = AttachmentOwner.FuelRecord,
        serviceOwner = AttachmentOwner.ServiceRecord,
        expenseOwner = AttachmentOwner.Expense,
    )

    @Query(
        """
        SELECT * FROM attachments
        WHERE (ownerType = :vehicleOwner AND ownerId = :vehicleId)
           OR (ownerType = :fuelOwner
               AND ownerId IN (SELECT id FROM fuel_records WHERE vehicleId = :vehicleId))
           OR (ownerType = :serviceOwner
               AND ownerId IN (SELECT id FROM service_records WHERE vehicleId = :vehicleId))
           OR (ownerType = :expenseOwner
               AND ownerId IN (SELECT id FROM expenses WHERE vehicleId = :vehicleId))
        """,
    )
    suspend fun listForVehicle(
        vehicleId: Long,
        vehicleOwner: AttachmentOwner,
        fuelOwner: AttachmentOwner,
        serviceOwner: AttachmentOwner,
        expenseOwner: AttachmentOwner,
    ): List<AttachmentEntity>

    @Query(
        """
        DELETE FROM attachments
        WHERE (ownerType = :vehicleOwner AND ownerId = :vehicleId)
           OR (ownerType = :fuelOwner
               AND ownerId IN (SELECT id FROM fuel_records WHERE vehicleId = :vehicleId))
           OR (ownerType = :serviceOwner
               AND ownerId IN (SELECT id FROM service_records WHERE vehicleId = :vehicleId))
           OR (ownerType = :expenseOwner
               AND ownerId IN (SELECT id FROM expenses WHERE vehicleId = :vehicleId))
        """,
    )
    suspend fun deleteForVehicle(
        vehicleId: Long,
        vehicleOwner: AttachmentOwner,
        fuelOwner: AttachmentOwner,
        serviceOwner: AttachmentOwner,
        expenseOwner: AttachmentOwner,
    )

    @Delete
    suspend fun deleteAll(attachments: List<AttachmentEntity>)

    @Insert
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    /** One transaction, so a failure cannot leave only some of the rows swapped. */
    @Transaction
    suspend fun swap(added: List<AttachmentEntity>, removed: List<AttachmentEntity>) {
        insertAll(added)
        deleteAll(removed)
    }

    /**
     * Attachments whose record no longer exists. With no foreign key to follow,
     * a bulk replacement of the records leaves these behind and they have to be
     * swept up explicitly.
     */
    suspend fun listOrphans(): List<AttachmentEntity> = listOrphans(
        vehicleOwner = AttachmentOwner.Vehicle,
        fuelOwner = AttachmentOwner.FuelRecord,
        serviceOwner = AttachmentOwner.ServiceRecord,
        expenseOwner = AttachmentOwner.Expense,
    )

    @Query(
        """
        SELECT * FROM attachments
        WHERE (ownerType = :vehicleOwner AND ownerId NOT IN (SELECT id FROM vehicles))
           OR (ownerType = :fuelOwner AND ownerId NOT IN (SELECT id FROM fuel_records))
           OR (ownerType = :serviceOwner AND ownerId NOT IN (SELECT id FROM service_records))
           OR (ownerType = :expenseOwner AND ownerId NOT IN (SELECT id FROM expenses))
        """,
    )
    suspend fun listOrphans(
        vehicleOwner: AttachmentOwner,
        fuelOwner: AttachmentOwner,
        serviceOwner: AttachmentOwner,
        expenseOwner: AttachmentOwner,
    ): List<AttachmentEntity>
}
