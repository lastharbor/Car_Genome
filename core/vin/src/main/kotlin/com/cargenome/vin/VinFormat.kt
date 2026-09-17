package com.cargenome.vin

/**
 * Structural facts about a VIN that are fixed by ISO 3779 and do not depend on
 * any manufacturer data.
 */
object VinFormat {

    const val LENGTH = 17

    /** I, O and Q are excluded so they cannot be confused with 1 and 0. */
    const val ALLOWED_CHARS = "0123456789ABCDEFGHJKLMNPRSTUVWXYZ"

    /** World Manufacturer Identifier: positions 1..3. */
    val WMI_RANGE = 0..2

    /** Vehicle Descriptor Section: positions 4..9. */
    val VDS_RANGE = 3..8

    /** Vehicle Identifier Section: positions 10..17. */
    val VIS_RANGE = 9..16

    /** Check digit position (1-based position 9). */
    const val CHECK_DIGIT_INDEX = 8

    /** Model year position (1-based position 10). */
    const val MODEL_YEAR_INDEX = 9

    /** Assembly plant position (1-based position 11). */
    const val PLANT_INDEX = 10

    fun isAllowedChar(c: Char): Boolean = c in ALLOWED_CHARS

    /**
     * Uppercases and strips separators people type when copying a VIN from a
     * registration document. Does not validate.
     */
    fun normalize(raw: String): String =
        raw.uppercase().filter { !it.isWhitespace() && it != '-' && it != '_' }
}
