package com.cargenome.app.domain.model

import com.cargenome.vin.CheckDigitResult
import com.cargenome.vin.VinCountry
import com.cargenome.vin.VinProblem
import com.cargenome.vin.VinRegion

/**
 * Everything known about a car from its VIN, after the offline table and vPIC
 * have both had their say. This is what prefills the vehicle form, and every
 * field of it stays editable by hand.
 */
data class VehicleProfile(
    val vin: String,
    /** Positions 1-3, 4-9 and 10-17, kept for the structure view. */
    val wmi: String,
    val vds: String,
    val vis: String,
    val serialNumber: String,
    val checkDigit: CheckDigitResult? = null,

    val country: VinCountry? = null,
    val region: VinRegion? = null,

    val manufacturer: String? = null,
    val make: String? = null,
    val model: String? = null,
    val modelYear: Int? = null,
    /** Position 10 repeats every 30 years, so the year may be a guess. */
    val modelYearIsAmbiguous: Boolean = false,
    val alternativeModelYear: Int? = null,

    val trim: String? = null,
    val series: String? = null,
    val bodyClass: String? = null,
    val vehicleType: String? = null,

    val engine: String? = null,
    val displacementLitres: Double? = null,
    val cylinders: Int? = null,
    val horsepower: Int? = null,
    val fuelType: FuelType? = null,
    val fuelTypeLabel: String? = null,
    val transmission: String? = null,
    val driveType: String? = null,
    val doors: Int? = null,

    val plantCode: Char? = null,
    val plant: String? = null,

    val problems: List<VinProblem> = emptyList(),
)
