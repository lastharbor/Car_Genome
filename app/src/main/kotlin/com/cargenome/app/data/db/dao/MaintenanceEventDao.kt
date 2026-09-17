package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceEventDao {

    @Query(
        """
        SELECT * FROM maintenance_events 
        WHERE vehicleId = :vehicleId 
        ORDER BY isCompleted ASC, scheduledDate ASC, scheduledTimeMinutes ASC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<MaintenanceEventEntity>>

    @Query(
        """
        SELECT * FROM maintenance_events 
        WHERE vehicleId = :vehicleId AND isCompleted = 0 AND scheduledDate >= :fromEpochDay 
        ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC
        """,
    )
    fun observeUpcoming(vehicleId: Long, fromEpochDay: Long): Flow<List<MaintenanceEventEntity>>

    @Query(
        """
        SELECT * FROM maintenance_events 
        WHERE vehicleId = :vehicleId AND scheduledDate BETWEEN :startEpochDay AND :endEpochDay 
        ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC
        """,
    )
    fun observeForDateRange(vehicleId: Long, startEpochDay: Long, endEpochDay: Long): Flow<List<MaintenanceEventEntity>>

    @Query(
        """
        SELECT * FROM maintenance_events 
        WHERE isCompleted = 0 AND remindAdvanceDays >= 0
        ORDER BY scheduledDate ASC
        """,
    )
    suspend fun listPendingWithReminders(): List<MaintenanceEventEntity>

    @Query("SELECT * FROM maintenance_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MaintenanceEventEntity?

    @Query("SELECT * FROM maintenance_events WHERE vehicleId = :vehicleId")
    suspend fun listForVehicle(vehicleId: Long): List<MaintenanceEventEntity>

    @Query("SELECT * FROM maintenance_events")
    suspend fun listAll(): List<MaintenanceEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MaintenanceEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<MaintenanceEventEntity>)

    @Update
    suspend fun update(event: MaintenanceEventEntity)

    @Query("DELETE FROM maintenance_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE maintenance_events 
        SET isCompleted = 1, completedAt = :completedAt, serviceRecordId = :serviceRecordId 
        WHERE id = :id
        """,
    )
    suspend fun markCompleted(id: Long, completedAt: Long, serviceRecordId: Long?)

    @Query(
        """
        UPDATE maintenance_events 
        SET isCompleted = 0, completedAt = NULL, serviceRecordId = NULL 
        WHERE id = :id
        """,
    )
    suspend fun markIncomplete(id: Long)
}
