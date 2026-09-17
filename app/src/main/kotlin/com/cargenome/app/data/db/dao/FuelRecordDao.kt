package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {

    /** Newest first, which is what the log screen shows. */
    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY filledAt DESC, id DESC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<FuelRecordEntity>>

    /**
     * Oldest first. Consumption is worked out by walking forward from one full
     * tank to the next, so the calculation needs the opposite order to the log.
     */
    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY filledAt ASC, id ASC
        """,
    )
    fun observeChronological(vehicleId: Long): Flow<List<FuelRecordEntity>>

    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY filledAt ASC, id ASC
        """,
    )
    suspend fun listChronological(vehicleId: Long): List<FuelRecordEntity>

    @Query("SELECT * FROM fuel_records WHERE id = :id")
    suspend fun findById(id: Long): FuelRecordEntity?

    @Query("SELECT * FROM fuel_records ORDER BY id ASC")
    suspend fun listAll(): List<FuelRecordEntity>

    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY filledAt DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatest(vehicleId: Long): Flow<FuelRecordEntity?>

    @Query(
        """
        SELECT COALESCE(SUM(total_cost_minor), 0) FROM fuel_records
        WHERE vehicleId = :vehicleId AND filledAt >= :from AND filledAt < :to
        """,
    )
    fun observeSpendBetween(vehicleId: Long, from: Long, to: Long): Flow<Long>

    @Insert
    suspend fun insert(record: FuelRecordEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<FuelRecordEntity>): List<Long>

    @Update
    suspend fun update(record: FuelRecordEntity)

    @Delete
    suspend fun delete(record: FuelRecordEntity)

    @Query("DELETE FROM fuel_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
