package com.cargenome.app.data.vin

import com.cargenome.app.domain.model.VehicleProfile
import com.cargenome.vin.VinDecodeResult
import com.cargenome.vin.VinProblem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Where the details on screen came from, so the UI can be honest about it. */
enum class VinSource {
    /** Offline WMI table only, either by choice or because nothing else answered. */
    Offline,

    /** Enriched from a vPIC answer stored earlier. */
    Cached,

    /** Enriched from a fresh vPIC lookup. */
    Network,
}

sealed interface VinLookupState {

    /** The VIN itself is wrong, so there is nothing to look up. */
    data class Invalid(val input: String, val problems: List<VinProblem>) : VinLookupState

    /**
     * A usable reading. [isEnriching] is true while a vPIC lookup is still in
     * flight, so the screen can show the offline answer straight away and fill
     * in the rest when it arrives.
     */
    data class Ready(
        val profile: VehicleProfile,
        val source: VinSource,
        val isEnriching: Boolean = false,
        val onlineFailure: Throwable? = null,
    ) : VinLookupState
}

/**
 * The single entry point for decoding a VIN.
 *
 * Offline first, always: the WMI table answers immediately and covers cars vPIC
 * has never heard of, which is most of what is on the road outside the United
 * States. The online lookup only ever adds to that answer, and a failure leaves
 * the offline reading standing rather than replacing it with an error.
 */
@Singleton
class VinRepository @Inject constructor(
    private val offline: VinDecodingRepository,
    private val decoders: Set<@JvmSuppressWildcards OnlineVinDecoder>,
) {
    fun lookup(vin: String, allowNetwork: Boolean = true): Flow<VinLookupState> = flow {
        when (val decoded = offline.decode(vin)) {
            is VinDecodeResult.Malformed -> emit(VinLookupState.Invalid(decoded.input, decoded.problems))

            is VinDecodeResult.Decoded -> {
                val base = decoded.value.toProfile()
                if (!allowNetwork) {
                    emit(VinLookupState.Ready(base, VinSource.Offline))
                    return@flow
                }

                emit(VinLookupState.Ready(base, VinSource.Offline, isEnriching = true))
                emit(enrich(base))
            }
        }
    }

    /** One-shot decode for callers that cannot collect a flow, such as a worker. */
    suspend fun lookupOnce(vin: String, allowNetwork: Boolean = true): VinLookupState =
        when (val decoded = offline.decode(vin)) {
            is VinDecodeResult.Malformed -> VinLookupState.Invalid(decoded.input, decoded.problems)
            is VinDecodeResult.Decoded -> {
                val base = decoded.value.toProfile()
                if (allowNetwork) enrich(base) else VinLookupState.Ready(base, VinSource.Offline)
            }
        }

    suspend fun suggestModels(make: String, year: Int): List<String> {
        return decoders.firstNotNullOfOrNull {
            val models = it.modelsFor(make, year)
            if (models.isNotEmpty()) models else null
        }.orEmpty()
    }

    private suspend fun enrich(base: VehicleProfile): VinLookupState.Ready {
        var currentProfile = base
        var isNetworkSource = false
        var isCachedSource = false
        var onlineFailure: Throwable? = null

        // Try all decoders concurrently or sequentially. 
        // For simplicity, sequentially enrich the profile.
        for (decoder in decoders) {
            when (val lookup = decoder.lookup(base.vin)) {
                is OnlineVinLookup.Hit -> {
                    currentProfile = currentProfile.mergedWith(lookup.data)
                    if (lookup.fromCache) isCachedSource = true else isNetworkSource = true
                }
                is OnlineVinLookup.Failed -> {
                    onlineFailure = lookup.cause
                }
                else -> {}
            }
        }
        
        val source = when {
            isNetworkSource -> VinSource.Network
            isCachedSource -> VinSource.Cached
            else -> VinSource.Offline
        }

        return VinLookupState.Ready(
            profile = currentProfile,
            source = source,
            onlineFailure = if (source == VinSource.Offline) onlineFailure else null,
        )
    }
}
