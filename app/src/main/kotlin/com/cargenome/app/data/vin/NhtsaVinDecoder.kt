package com.cargenome.app.data.vin

import com.cargenome.app.data.db.entity.VinCacheEntryEntity
import com.cargenome.app.data.network.NetworkMonitor
import com.cargenome.app.data.network.NhtsaVpicApi
import com.cargenome.app.data.network.VpicModel
import com.cargenome.app.data.network.VpicVehicle
import com.cargenome.app.data.repository.VinCacheRepository
import com.cargenome.app.di.IoDispatcher
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

/** What came back from an online decoder, or why nothing did. */
sealed interface OnlineVinLookup {

    data class Hit(val data: ExternalVehicleData, val fromCache: Boolean) : OnlineVinLookup

    /** The decoder answered and knows nothing about this VIN. */
    data object Empty : OnlineVinLookup

    /** No usable connection, so the lookup was not attempted. */
    data object Offline : OnlineVinLookup

    data class Failed(val cause: Throwable) : OnlineVinLookup
}

/**
 * An online source of VIN details. vPIC is one implementation; a
 * paid decoder with a key from settings would slot in here without the
 * repository or the UI noticing.
 */
interface OnlineVinDecoder {

    suspend fun lookup(vin: String, now: Instant = Instant.now()): OnlineVinLookup

    suspend fun modelsFor(make: String, year: Int): List<String>
}

/**
 * Online enrichment from NHTSA vPIC, with the answer kept in the database.
 *
 * A VIN describes a car that left the factory years ago, so a cached answer is
 * as good as a fresh one and is preferred: it costs nothing and works on a
 * train. The network is only touched for a VIN that has never been looked up.
 */
@Singleton
class NhtsaVinDecoder @Inject constructor(
    private val api: NhtsaVpicApi,
    private val cache: VinCacheRepository,
    private val network: NetworkMonitor,
    private val json: Json,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : OnlineVinDecoder {

    override suspend fun lookup(vin: String, now: Instant): OnlineVinLookup =
        withContext(dispatcher) {
            val normalized = vin.uppercase()

            cache.find(normalized, now)?.let { return@withContext it.toLookup() }

            if (!network.isOnline()) return@withContext OnlineVinLookup.Offline

            val response = try {
                withTimeout(6_000L) {
                    api.decodeVin(normalized)
                }
            } catch (timeout: TimeoutCancellationException) {
                return@withContext OnlineVinLookup.Failed(timeout)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IOException) {
                // A dropped connection is not the app's problem to solve; the
                // offline answer already stands.
                return@withContext OnlineVinLookup.Failed(error)
            } catch (error: RuntimeException) {
                // Malformed JSON or an HTTP error surfaces here.
                return@withContext OnlineVinLookup.Failed(error)
            }

            val result = response.results.firstOrNull().orEmpty()
            val vehicle = VpicVehicle.fromResult(result)
            cache.put(
                VinCacheEntryEntity(
                    vin = normalized,
                    payloadJson = json.encodeToString(result),
                    fetchedAt = now,
                    isEmptyResult = vehicle.isEmpty,
                ),
            )
            if (vehicle.isEmpty) OnlineVinLookup.Empty else OnlineVinLookup.Hit(vehicle.toExternalData(), fromCache = false)
        }

    /**
     * Models NHTSA knows for a make and year, used when the VIN alone does not
     * pin the model down. Returns nothing rather than failing: this is a
     * convenience list, and free text entry is always available.
     */
    override suspend fun modelsFor(make: String, year: Int): List<String> = withContext(dispatcher) {
        if (make.isBlank() || !network.isOnline()) return@withContext emptyList()
        val response = try {
            api.modelsForMakeYear(make, year)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            return@withContext emptyList()
        } catch (error: RuntimeException) {
            return@withContext emptyList()
        }
        response.results
            .map(VpicModel::modelName)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private fun VinCacheEntryEntity.toLookup(): OnlineVinLookup {
        if (isEmptyResult) return OnlineVinLookup.Empty
        val result = runCatching {
            json.decodeFromString<Map<String, String?>>(payloadJson)
        }.getOrNull() ?: return OnlineVinLookup.Empty
        val vehicle = VpicVehicle.fromResult(result)
        return if (vehicle.isEmpty) OnlineVinLookup.Empty else OnlineVinLookup.Hit(vehicle.toExternalData(), fromCache = true)
    }
}
