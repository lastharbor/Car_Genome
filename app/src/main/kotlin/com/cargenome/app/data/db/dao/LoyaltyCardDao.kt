package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.LoyaltyCardEntity
import com.cargenome.app.data.db.entity.LoyaltyCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface LoyaltyCardDao {

    @Query("SELECT * FROM loyalty_cards ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_cards WHERE category = :category ORDER BY updatedAt DESC, id DESC")
    fun observeByCategory(category: LoyaltyCategory): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_cards WHERE vehicleId = :vehicleId OR vehicleId IS NULL ORDER BY updatedAt DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    fun observeById(id: Long): Flow<LoyaltyCardEntity?>

    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    suspend fun findById(id: Long): LoyaltyCardEntity?

    @Query("SELECT COUNT(*) FROM loyalty_cards")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: LoyaltyCardEntity): Long

    @Update
    suspend fun update(card: LoyaltyCardEntity)

    @Delete
    suspend fun delete(card: LoyaltyCardEntity)

    @Query("SELECT * FROM loyalty_cards ORDER BY id ASC")
    suspend fun listAll(): List<LoyaltyCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<LoyaltyCardEntity>)

    @Query("DELETE FROM loyalty_cards")
    suspend fun deleteAll()

    @Query("DELETE FROM loyalty_cards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
