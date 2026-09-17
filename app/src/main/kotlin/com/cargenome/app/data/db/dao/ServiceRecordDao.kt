package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {

    @Query(
        """
        SELECT * FROM service_records
        WHERE vehicleId = :vehicleId
        ORDER BY performedAt DESC, id DESC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM service_records WHERE id = :id")
    suspend fun findById(id: Long): ServiceRecordEntity?

    @Query(
        """
        SELECT * FROM service_records
        WHERE vehicleId = :vehicleId
        ORDER BY performedAt ASC, id ASC
        """,
    )
    suspend fun listChronological(vehicleId: Long): List<ServiceRecordEntity>

    @Query("SELECT * FROM service_records ORDER BY id ASC")
    suspend fun listAll(): List<ServiceRecordEntity>

    /** The most recent job booked against a plan item, used to reset its interval. */
    @Query(
        """
        SELECT * FROM service_records
        WHERE scheduleId = :scheduleId
        ORDER BY performedAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestForSchedule(scheduleId: Long): ServiceRecordEntity?

    @Query(
        """
        SELECT COALESCE(SUM(labour_cost_minor + parts_cost_minor), 0) FROM service_records
        WHERE vehicleId = :vehicleId AND performedAt >= :from AND performedAt < :to
        """,
    )
    fun observeSpendBetween(vehicleId: Long, from: Long, to: Long): Flow<Long>

    @Insert
    suspend fun insert(record: ServiceRecordEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ServiceRecordEntity>): List<Long>

    @Update
    suspend fun update(record: ServiceRecordEntity)

    @Delete
    suspend fun delete(record: ServiceRecordEntity)

    @Query("DELETE FROM service_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
