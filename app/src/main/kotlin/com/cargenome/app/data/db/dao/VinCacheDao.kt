package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cargenome.app.data.db.entity.VinCacheEntryEntity

@Dao
interface VinCacheDao {

    @Query("SELECT * FROM vin_cache WHERE vin = :vin")
    suspend fun find(vin: String): VinCacheEntryEntity?

    @Upsert
    suspend fun put(entry: VinCacheEntryEntity)

    @Query("DELETE FROM vin_cache WHERE vin = :vin")
    suspend fun delete(vin: String)

    @Query("DELETE FROM vin_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM vin_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM vin_cache")
    suspend fun count(): Int
}
