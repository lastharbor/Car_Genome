package com.cargenome.app.data.repository

import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class OdometerRepository @Inject constructor(
    private val dao: OdometerReadingDao,
) {
    fun observe(vehicleId: Long): Flow<List<OdometerReadingEntity>> = dao.observeForVehicle(vehicleId)

    fun observeChronological(vehicleId: Long): Flow<List<OdometerReadingEntity>> =
        dao.observeChronological(vehicleId)

    fun observeCurrentKm(vehicleId: Long): Flow<Double?> = dao.observeCurrentKm(vehicleId)

    suspend fun currentKm(vehicleId: Long): Double? = dao.currentKm(vehicleId)

    suspend fun add(reading: OdometerReadingEntity): Long = dao.insert(reading)

    suspend fun update(reading: OdometerReadingEntity) = dao.update(reading)

    /**
     * Only hand-entered readings can be removed on their own. The rest belong to
     * a fuel, service or expense record and go when that record goes.
     */
    suspend fun delete(reading: OdometerReadingEntity) {
        require(reading.source == OdometerSource.Manual) {
            "Reading ${reading.id} belongs to ${reading.source}; delete that record instead"
        }
        dao.delete(reading)
    }
}
