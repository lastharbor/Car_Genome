package com.cargenome.vin

sealed interface ModelYearResult {

    /** Position 10 carries no year: outside North America it is often a filler. */
    data object NotEncoded : ModelYearResult

    /**
     * A single year the standard pins down. Only North American passenger
     * vehicles get here, because position 7 resolves the 30-year cycle.
     */
    data class Resolved(val year: Int) : ModelYearResult

    /**
     * The code maps onto a 30-year cycle and nothing in the VIN says which turn
     * of the cycle it is. [preferred] is the most recent plausible year.
     */
    data class Ambiguous(val preferred: Int, val candidates: List<Int>) : ModelYearResult
}

/**
 * Model year from position 10.
 *
 * The code repeats every 30 years (A is 1980 and also 2010). North America
 * resolves this with position 7 per 49 CFR 565.15: a digit there means the car
 * is from 1980-2009, a letter means 2010 or later. Everywhere else the cycle
 * stays ambiguous, and plenty of manufacturers do not encode a year at all.
 */
object VinModelYear {

    /** Year codes in order. U, Z and 0 are never used for the model year. */
    private const val CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"

    private const val FIRST_YEAR = 1980
    private const val CYCLE = 30

    fun decode(vin: String, currentYear: Int): ModelYearResult {
        val code = vin.getOrNull(VinFormat.MODEL_YEAR_INDEX) ?: return ModelYearResult.NotEncoded
        val index = CODES.indexOf(code)
        if (index < 0) return ModelYearResult.NotEncoded

        // Model years run ahead of the calendar: a 2027 car can be sold in 2026.
        val latestPlausible = currentYear + 1

        val candidates = generateSequence(FIRST_YEAR + index) { it + CYCLE }
            .takeWhile { it <= latestPlausible }
            .toList()
        if (candidates.isEmpty()) return ModelYearResult.NotEncoded

        if (VinCountries.regionOf(vin[0]) == VinRegion.NorthAmerica) {
            when (vin.getOrNull(6)) {
                null -> Unit
                in '0'..'9' -> return ModelYearResult.Resolved(candidates.first())
                else -> candidates.getOrNull(1)?.let { return ModelYearResult.Resolved(it) }
            }
        }

        val preferred = candidates.last()
        return if (candidates.size == 1) {
            ModelYearResult.Resolved(preferred)
        } else {
            ModelYearResult.Ambiguous(preferred = preferred, candidates = candidates)
        }
    }
}
