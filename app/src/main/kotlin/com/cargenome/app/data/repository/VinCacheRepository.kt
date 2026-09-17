package com.cargenome.app.data.repository

import com.cargenome.app.data.db.dao.VinCacheDao
import com.cargenome.app.data.db.entity.VinCacheEntryEntity
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stored vPIC answers.
 *
 * A decoded VIN describes a car that left the factory years ago and will not
 * change, so a hit never really goes stale. The expiry only exists to let a
 * fruitless lookup be retried later, in case vPIC has learned something since.
 */
@Singleton
class VinCacheRepository @Inject constructor(
    private val dao: VinCacheDao,
) {
    suspend fun find(vin: String, now: Instant = Instant.now()): VinCacheEntryEntity? {
        val entry = dao.find(vin.uppercase()) ?: return null
        val expired = entry.isEmptyResult &&
            Duration.between(entry.fetchedAt, now) > EMPTY_RESULT_RETRY_AFTER
        return entry.takeUnless { expired }
    }

    suspend fun put(entry: VinCacheEntryEntity) = dao.put(entry.copy(vin = entry.vin.uppercase()))

    suspend fun evict(vin: String) = dao.delete(vin.uppercase())

    suspend fun clear() = dao.clear()

    suspend fun size(): Int = dao.count()

    private companion object {
        val EMPTY_RESULT_RETRY_AFTER: Duration = Duration.ofDays(7)
    }
}
