package com.cargenome.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A stored vPIC answer, so a VIN that was decoded once keeps decoding with no
 * network. The raw JSON is kept verbatim rather than parsed into columns: vPIC
 * adds fields over time and re-reading an old payload should not lose them.
 */
@Entity(tableName = "vin_cache")
data class VinCacheEntryEntity(
    @PrimaryKey val vin: String,

    val payloadJson: String,
    val fetchedAt: Instant,
    /** Which decoder produced this, so a later paid source can coexist with vPIC. */
    val source: String = SOURCE_VPIC,
    /** Set when vPIC answered but had nothing useful, to avoid asking again at once. */
    val isEmptyResult: Boolean = false,
) {
    companion object {
        const val SOURCE_VPIC = "nhtsa-vpic"
    }
}
