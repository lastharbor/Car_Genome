package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.cargenome.app.data.db.entity.VehicleEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** A car plus the few running totals the garage list shows next to it. */
@androidx.compose.runtime.Immutable
data class VehicleSummary(
    @Embedded val vehicle: VehicleEntity,
    val currentOdometerKm: Double?,
    val lastFuelAt: Instant?,
    val fuelCount: Int,
    val serviceCount: Int,
)

@Dao
interface VehicleDao {

    /**
     * The garage list. The totals come from correlated subqueries rather than
     * joins so that a car with no records still appears, with nulls where its
     * history would be.
     */
    @Query(
        """
        SELECT v.*,
            COALESCE((SELECT MAX(o.odometer_km) FROM odometer_readings o WHERE o.vehicleId = v.id), NULLIF(v.initial_odometer_km, 0.0))
                AS currentOdometerKm,
            (SELECT MAX(f.filledAt) FROM fuel_records f WHERE f.vehicleId = v.id)
                AS lastFuelAt,
            (SELECT COUNT(*) FROM fuel_records f WHERE f.vehicleId = v.id) AS fuelCount,
            (SELECT COUNT(*) FROM service_records s WHERE s.vehicleId = v.id) AS serviceCount
        FROM vehicles v
        WHERE v.isArchived = 0
        ORDER BY v.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<VehicleSummary>>

    @Query("SELECT * FROM vehicles WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY isArchived ASC, createdAt DESC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun findById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE vin = :vin")
    suspend fun findByVin(vin: String): VehicleEntity?

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun listAll(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicles WHERE isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehicles: List<VehicleEntity>): List<Long>

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity): Long

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE vehicles SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}
