package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import kotlinx.coroutines.flow.Flow

@Dao
interface OdometerReadingDao {

    @Query(
        """
        SELECT * FROM odometer_readings
        WHERE vehicleId = :vehicleId
        ORDER BY recordedAt DESC, id DESC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<OdometerReadingEntity>>

    @Query(
        """
        SELECT * FROM odometer_readings
        WHERE vehicleId = :vehicleId
        ORDER BY recordedAt ASC, id ASC
        """,
    )
    fun observeChronological(vehicleId: Long): Flow<List<OdometerReadingEntity>>

    /**
     * Highest reading rather than newest. An odometer only counts up, so a
     * backdated entry must not make the car look like it travelled less.
     */
    @Query("SELECT MAX(odometer_km) FROM odometer_readings WHERE vehicleId = :vehicleId")
    fun observeCurrentKm(vehicleId: Long): Flow<Double?>

    @Query("SELECT MAX(odometer_km) FROM odometer_readings WHERE vehicleId = :vehicleId")
    suspend fun currentKm(vehicleId: Long): Double?

    @Query("SELECT * FROM odometer_readings ORDER BY id ASC")
    suspend fun listAll(): List<OdometerReadingEntity>

    @Insert
    suspend fun insert(reading: OdometerReadingEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<OdometerReadingEntity>): List<Long>

    @Update
    suspend fun update(reading: OdometerReadingEntity)

    @Delete
    suspend fun delete(reading: OdometerReadingEntity)

    /** Used when the fuel or service record that carried the reading is removed. */
    @Query(
        """
        DELETE FROM odometer_readings
        WHERE source = :source AND sourceRecordId = :recordId
        """,
    )
    suspend fun deleteBySource(source: OdometerSource, recordId: Long)

    @Query(
        """
        UPDATE odometer_readings
        SET odometer_km = :odometerKm, recordedAt = :recordedAt
        WHERE source = :source AND sourceRecordId = :recordId
        """,
    )
    suspend fun updateBySource(
        source: OdometerSource,
        recordId: Long,
        odometerKm: Double,
        recordedAt: Long,
    )
}
