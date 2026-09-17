package com.cargenome.app.data.vin

import com.cargenome.app.domain.model.FuelType

/**
 * Standardized vehicle information retrieved from an external API (like NHTSA, Auto.ria, CarMD, etc).
 * Used to enrich the base VehicleProfile.
 */
data class ExternalVehicleData(
    val manufacturer: String? = null,
    val make: String? = null,
    val model: String? = null,
    val modelYear: Int? = null,
    val trim: String? = null,
    val series: String? = null,
    val bodyClass: String? = null,
    val vehicleType: String? = null,
    val engineSummary: String? = null,
    val displacementLitres: Double? = null,
    val cylinders: Int? = null,
    val horsepower: Int? = null,
    val fuelType: FuelType? = null,
    val fuelTypeLabel: String? = null,
    val transmission: String? = null,
    val driveType: String? = null,
    val doors: Int? = null,
    val plant: String? = null,
) {
    val isEmpty: Boolean
        get() = make.isNullOrBlank() && model.isNullOrBlank() && manufacturer.isNullOrBlank()
}
