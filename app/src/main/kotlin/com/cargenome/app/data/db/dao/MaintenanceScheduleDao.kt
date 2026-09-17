package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceScheduleDao {

    @Query(
        """
        SELECT * FROM maintenance_schedules
        WHERE vehicleId = :vehicleId
        ORDER BY isEnabled DESC, title COLLATE NOCASE ASC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules WHERE isEnabled = 1")
    suspend fun listEnabled(): List<MaintenanceScheduleEntity>

    @Query("SELECT * FROM maintenance_schedules WHERE id = :id")
    suspend fun findById(id: Long): MaintenanceScheduleEntity?

    @Query("SELECT * FROM maintenance_schedules ORDER BY id ASC")
    suspend fun listAll(): List<MaintenanceScheduleEntity>

    @Insert
    suspend fun insert(schedule: MaintenanceScheduleEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<MaintenanceScheduleEntity>): List<Long>

    @Update
    suspend fun update(schedule: MaintenanceScheduleEntity)

    @Delete
    suspend fun delete(schedule: MaintenanceScheduleEntity)

    @Query(
        """
        UPDATE maintenance_schedules
        SET last_performed_at = :performedAt, last_performed_odometer_km = :odometerKm
        WHERE id = :id
        """,
    )
    suspend fun markPerformed(id: Long, performedAt: Long?, odometerKm: Double?)
}
