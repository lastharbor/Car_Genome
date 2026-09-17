package com.cargenome.vin

sealed interface VinProblem {

    data class WrongLength(val actual: Int) : VinProblem

    /** 1-based positions, so they can be pointed at directly in the UI. */
    data class IllegalCharacters(val positions: List<Int>) : VinProblem

    data class CheckDigitMismatch(
        val expected: Char,
        val actual: Char,
        val mandatory: Boolean,
    ) : VinProblem
}

data class DecodedVin(
    val vin: String,
    /** Positions 1-3, the World Manufacturer Identifier. */
    val wmi: String,
    /** Positions 4-9, the Vehicle Descriptor Section. */
    val vds: String,
    /** Positions 10-17, the Vehicle Identifier Section. */
    val vis: String,
    val country: VinCountry?,
    val manufacturer: WmiEntry?,
    val modelYear: ModelYearResult,
    val plantCode: Char?,
    val serialNumber: String,
    val checkDigit: CheckDigitResult,
    val problems: List<VinProblem>,
) {
    val region: VinRegion? get() = country?.region

    val make: String? get() = manufacturer?.make ?: manufacturer?.manufacturer

    /** Best single year to prefill the form with, if the VIN suggests one at all. */
    val bestGuessYear: Int?
        get() = when (val year = modelYear) {
            is ModelYearResult.Resolved -> year.year
            is ModelYearResult.Ambiguous -> year.preferred
            ModelYearResult.NotEncoded -> null
        }
}

sealed interface VinDecodeResult {

    /** Length or character set is wrong, so nothing can be read out of the VIN. */
    data class Malformed(val input: String, val problems: List<VinProblem>) : VinDecodeResult

    /**
     * The VIN is structurally sound. [DecodedVin.problems] may still hold a
     * check digit mismatch, which outside North America is only a warning.
     */
    data class Decoded(val value: DecodedVin) : VinDecodeResult
}
