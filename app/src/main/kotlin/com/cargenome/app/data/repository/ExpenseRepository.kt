package com.cargenome.app.data.repository

import androidx.room.withTransaction
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.dao.CategoryTotal
import com.cargenome.app.data.db.dao.ExpenseDao
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ExpenseRepository @Inject constructor(
    private val database: CarGenomeDatabase,
    private val expenseDao: ExpenseDao,
    private val odometerDao: OdometerReadingDao,
    private val attachments: AttachmentRepository,
) {
    fun observe(vehicleId: Long): Flow<List<ExpenseEntity>> = expenseDao.observeForVehicle(vehicleId)

    fun observeTotalsByCategory(
        vehicleId: Long,
        fromEpochMilli: Long,
        toEpochMilli: Long,
    ): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategory(vehicleId, fromEpochMilli, toEpochMilli)

    fun observeSpendBetween(vehicleId: Long, fromEpochMilli: Long, toEpochMilli: Long): Flow<Long> =
        expenseDao.observeSpendBetween(vehicleId, fromEpochMilli, toEpochMilli)

    suspend fun find(id: Long): ExpenseEntity? = expenseDao.findById(id)

    suspend fun add(expense: ExpenseEntity): Long = database.withTransaction {
        val id = expenseDao.insert(expense)
        expense.odometerKm?.let { km ->
            odometerDao.insert(
                OdometerReadingEntity(
                    vehicleId = expense.vehicleId,
                    recordedAt = expense.incurredAt,
                    odometerKm = km,
                    source = OdometerSource.Expense,
                    sourceRecordId = id,
                ),
            )
        }
        id
    }

    suspend fun update(expense: ExpenseEntity) = database.withTransaction {
        expenseDao.update(expense)
        odometerDao.deleteBySource(OdometerSource.Expense, expense.id)
        expense.odometerKm?.let { km ->
            odometerDao.insert(
                OdometerReadingEntity(
                    vehicleId = expense.vehicleId,
                    recordedAt = expense.incurredAt,
                    odometerKm = km,
                    source = OdometerSource.Expense,
                    sourceRecordId = expense.id,
                ),
            )
        }
    }

    suspend fun delete(id: Long) = database.withTransaction {
        attachments.deleteForOwner(AttachmentOwner.Expense, id)
        odometerDao.deleteBySource(OdometerSource.Expense, id)
        expenseDao.deleteById(id)
    }
}
