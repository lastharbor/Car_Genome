package com.cargenome.app.data.repository

import androidx.room.withTransaction
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.dao.FuelRecordDao
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Fill-ups, plus the odometer readings they imply.
 *
 * Every fuel record carries a mileage, and that mileage is worth having in the
 * odometer log too, otherwise the mileage graph would only show the handful of
 * readings entered by hand. The two rows are written together so they cannot
 * drift apart.
 */
@Singleton
class FuelRepository @Inject constructor(
    private val database: CarGenomeDatabase,
    private val fuelDao: FuelRecordDao,
    private val odometerDao: OdometerReadingDao,
    private val attachments: AttachmentRepository,
) {
    fun observe(vehicleId: Long): Flow<List<FuelRecordEntity>> = fuelDao.observeForVehicle(vehicleId)

    fun observeChronological(vehicleId: Long): Flow<List<FuelRecordEntity>> =
        fuelDao.observeChronological(vehicleId)

    fun observeLatest(vehicleId: Long): Flow<FuelRecordEntity?> = fuelDao.observeLatest(vehicleId)

    fun observeSpendBetween(vehicleId: Long, fromEpochMilli: Long, toEpochMilli: Long): Flow<Long> =
        fuelDao.observeSpendBetween(vehicleId, fromEpochMilli, toEpochMilli)

    suspend fun find(id: Long): FuelRecordEntity? = fuelDao.findById(id)

    suspend fun add(record: FuelRecordEntity): Long = database.withTransaction {
        val id = fuelDao.insert(record)
        odometerDao.insert(
            OdometerReadingEntity(
                vehicleId = record.vehicleId,
                recordedAt = record.filledAt,
                odometerKm = record.odometerKm,
                source = OdometerSource.FuelRecord,
                sourceRecordId = id,
            ),
        )
        id
    }

    suspend fun update(record: FuelRecordEntity) = database.withTransaction {
        fuelDao.update(record)
        odometerDao.deleteBySource(OdometerSource.FuelRecord, record.id)
        odometerDao.insert(
            OdometerReadingEntity(
                vehicleId = record.vehicleId,
                recordedAt = record.filledAt,
                odometerKm = record.odometerKm,
                source = OdometerSource.FuelRecord,
                sourceRecordId = record.id,
            ),
        )
    }

    suspend fun delete(id: Long) = database.withTransaction {
        attachments.deleteForOwner(AttachmentOwner.FuelRecord, id)
        odometerDao.deleteBySource(OdometerSource.FuelRecord, id)
        fuelDao.deleteById(id)
    }
}
