package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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
}
