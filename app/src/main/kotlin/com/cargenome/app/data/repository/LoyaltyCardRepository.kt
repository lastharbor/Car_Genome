package com.cargenome.app.data.repository

import com.cargenome.app.data.db.dao.LoyaltyCardDao
import com.cargenome.app.data.db.entity.LoyaltyCardEntity
import com.cargenome.app.data.db.entity.LoyaltyCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class LoyaltyCardRepository @Inject constructor(
    private val loyaltyCardDao: LoyaltyCardDao,
) {
    fun observeAll(): Flow<List<LoyaltyCardEntity>> = loyaltyCardDao.observeAll()

    fun observeByCategory(category: LoyaltyCategory): Flow<List<LoyaltyCardEntity>> =
        loyaltyCardDao.observeByCategory(category)

    fun observeForVehicle(vehicleId: Long): Flow<List<LoyaltyCardEntity>> =
        loyaltyCardDao.observeForVehicle(vehicleId)

    fun observeById(id: Long): Flow<LoyaltyCardEntity?> = loyaltyCardDao.observeById(id)

    fun observeCount(): Flow<Int> = loyaltyCardDao.observeCount()

    suspend fun getById(id: Long): LoyaltyCardEntity? = loyaltyCardDao.findById(id)

    suspend fun add(card: LoyaltyCardEntity): Long = loyaltyCardDao.insert(card)

    suspend fun update(card: LoyaltyCardEntity) = loyaltyCardDao.update(card)

    suspend fun delete(card: LoyaltyCardEntity) = loyaltyCardDao.delete(card)

    suspend fun deleteById(id: Long) = loyaltyCardDao.deleteById(id)
}
