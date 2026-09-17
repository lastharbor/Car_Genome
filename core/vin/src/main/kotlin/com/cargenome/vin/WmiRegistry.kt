package com.cargenome.vin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WmiEntry(
    /** Three characters, or six when positions 12-14 are part of the identifier. */
    val code: String,
    val manufacturer: String,
    val make: String? = null,
    /** ISO 3166-1 alpha-2 of the plant country when it differs from the VIN prefix. */
    val country: String? = null,
    val vehicleType: String? = null,
)

@Serializable
data class WmiDataset(
    val source: String = "",
    val updated: String = "",
    val entries: List<WmiEntry> = emptyList(),
)

fun interface WmiRegistry {

    fun lookup(vin: String): WmiEntry?

    companion object {
        val Empty = WmiRegistry { null }
    }
}

/**
 * WMI lookup backed by the bundled dataset.
 *
 * Manufacturers that build fewer than 500 vehicles a year get a WMI whose third
 * character is 9; for those, positions 12-14 complete the identifier, so the
 * six-character key is tried first.
 */
class JsonWmiRegistry private constructor(
    private val byCode: Map<String, WmiEntry>,
) : WmiRegistry {

    val size: Int get() = byCode.size

    override fun lookup(vin: String): WmiEntry? {
        if (vin.length < 3) return null
        val wmi = vin.substring(0, 3)
        if (wmi[2] == '9' && vin.length >= 14) {
            byCode[wmi + vin.substring(11, 14)]?.let { return it }
        }
        return byCode[wmi]
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): JsonWmiRegistry {
            val dataset = json.decodeFromString(WmiDataset.serializer(), text)
            return of(dataset.entries)
        }

        fun of(entries: List<WmiEntry>): JsonWmiRegistry =
            JsonWmiRegistry(entries.associateBy { it.code.uppercase() })
    }
}
