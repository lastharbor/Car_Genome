package com.cargenome.app.data.vin

import com.cargenome.app.di.DefaultDispatcher
import com.cargenome.vin.OfflineVinDecoder
import com.cargenome.vin.VinDecodeResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Offline VIN decoding, moved off the main thread.
 *
 * Online enrichment from NHTSA vPIC is layered on top of this later; the
 * offline answer is always produced first so the app stays useful with no
 * connection and for cars vPIC has never heard of.
 */
@Singleton
class VinDecodingRepository @Inject constructor(
    private val decoder: OfflineVinDecoder,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun decode(vin: String): VinDecodeResult = withContext(dispatcher) { decoder.decode(vin) }
}
