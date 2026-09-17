package com.cargenome.vin

/**
 * Position 9 check digit as defined by 49 CFR 565.
 *
 * The algorithm is universal, but the *requirement* is not: it is mandatory in
 * North America and China only. European and Japanese manufacturers routinely
 * put a filler character there, so a mismatch on such a VIN says nothing about
 * whether the VIN is correct.
 */
object VinCheckDigit {

    private val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    private val transliteration: Map<Char, Int> = buildMap {
        "ABCDEFGH".forEachIndexed { i, c -> put(c, i + 1) }
        put('J', 1); put('K', 2); put('L', 3); put('M', 4); put('N', 5)
        put('P', 7); put('R', 9)
        put('S', 2); put('T', 3); put('U', 4); put('V', 5)
        put('W', 6); put('X', 7); put('Y', 8); put('Z', 9)
        ('0'..'9').forEach { put(it, it - '0') }
    }

    fun valueOf(c: Char): Int? = transliteration[c]

    /**
     * Returns the expected character for position 9, or null if [vin] contains
     * anything the transliteration table does not cover.
     */
    fun compute(vin: String): Char? {
        if (vin.length != VinFormat.LENGTH) return null
        var sum = 0
        for (i in vin.indices) {
            val value = transliteration[vin[i]] ?: return null
            sum += value * weights[i]
        }
        val remainder = sum % 11
        return if (remainder == 10) 'X' else '0' + remainder
    }

    fun verify(vin: String, mandatory: Boolean): CheckDigitResult {
        val expected = compute(vin)
        val actual = vin.getOrNull(VinFormat.CHECK_DIGIT_INDEX)
        return CheckDigitResult(
            expected = expected,
            actual = actual,
            matches = expected != null && expected == actual,
            isMandatory = mandatory,
        )
    }
}

data class CheckDigitResult(
    val expected: Char?,
    val actual: Char?,
    val matches: Boolean,
    val isMandatory: Boolean,
) {
    /** A mismatch is only a hard error where the standard requires the digit. */
    val isError: Boolean get() = isMandatory && !matches

    /** Elsewhere a mismatch is worth showing, but must not block saving the car. */
    val isWarning: Boolean get() = !isMandatory && !matches
}
