package com.cargenome.app.domain.model

/**
 * Units the user reads and writes in.
 *
 * Everything in the database is stored in kilometres, litres and minor currency
 * units. Conversion happens at the edges, so fuel economy and service intervals
 * never have to care which units a particular car is set to.
 */
enum class DistanceUnit(val kilometresPerUnit: Double) {
    Kilometres(1.0),
    Miles(1.609344),
    ;

    fun toKilometres(value: Double): Double = value * kilometresPerUnit

    fun fromKilometres(kilometres: Double): Double = kilometres / kilometresPerUnit
}

enum class VolumeUnit(val litresPerUnit: Double) {
    Litres(1.0),
    UsGallons(3.785411784),
    ImperialGallons(4.54609),
    ;

    fun toLitres(value: Double): Double = value * litresPerUnit

    fun fromLitres(litres: Double): Double = litres / litresPerUnit
}

/**
 * How fuel economy is spoken about, which is not the same everywhere: Europe
 * counts litres per 100 km, North America counts miles per gallon, and the two
 * run in opposite directions.
 */
enum class ConsumptionUnit {
    LitresPer100Km,
    KilometresPerLitre,
    MilesPerUsGallon,
    MilesPerImperialGallon,
    ;

    /**
     * Converts a canonical litres-per-100-km figure into this unit. Returns null
     * for a non-positive input, which means there was nothing to measure.
     */
    fun fromLitresPer100Km(value: Double): Double? {
        if (value <= 0.0) return null
        return when (this) {
            LitresPer100Km -> value
            KilometresPerLitre -> 100.0 / value
            // Miles per gallon runs the other way round, so the conversion is a
            // division: 100 km is 62.137 miles, and one litre is 0.264 US gallons.
            MilesPerUsGallon -> milesPer100Km * VolumeUnit.UsGallons.litresPerUnit / value
            MilesPerImperialGallon ->
                milesPer100Km * VolumeUnit.ImperialGallons.litresPerUnit / value
        }
    }

    private companion object {
        val milesPer100Km = 100.0 / DistanceUnit.Miles.kilometresPerUnit
    }
}

enum class FuelType {
    Petrol,
    Diesel,
    Lpg,
    Cng,
    Hybrid,
    PlugInHybrid,
    Electric,
    Other,
}
