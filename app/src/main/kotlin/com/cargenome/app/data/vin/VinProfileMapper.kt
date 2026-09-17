package com.cargenome.app.data.vin

import com.cargenome.app.data.network.VpicVehicle
import com.cargenome.app.domain.model.VehicleProfile
import com.cargenome.vin.DecodedVin
import com.cargenome.vin.ModelYearResult

/** The offline reading of a VIN, before anything online is folded in. */
fun DecodedVin.toProfile(): VehicleProfile {
    val ambiguous = modelYear as? ModelYearResult.Ambiguous
    return VehicleProfile(
        vin = vin,
        wmi = wmi,
        vds = vds,
        vis = vis,
        serialNumber = serialNumber,
        checkDigit = checkDigit,
        country = country,
        region = region,
        manufacturer = manufacturer?.manufacturer,
        make = make,
        modelYear = bestGuessYear,
        modelYearIsAmbiguous = ambiguous != null,
        // One cycle back from the preferred year: for a car that is not new,
        // that is the next guess worth offering.
        alternativeModelYear = ambiguous?.candidates?.filter { it != ambiguous.preferred }?.maxOrNull(),
        vehicleType = manufacturer?.vehicleType,
        plantCode = plantCode,
        problems = problems,
    )
}

/**
 * Folds an external answer over the offline reading.
 *
 * Online values win where they exist, because external APIs read the full VIN pattern
 * rather than just the first three characters. Blank ones are ignored, so a
 * sparse answer for a European car cannot wipe out what the WMI table already
 * knew. The year is the exception: the offline decoder only guesses at the
 * decade, so a year from online API settles the ambiguity outright.
 */
fun VehicleProfile.mergedWith(online: com.cargenome.app.data.vin.ExternalVehicleData): VehicleProfile = copy(
    manufacturer = online.manufacturer ?: manufacturer,
    make = online.make ?: make,
    model = online.model ?: model,
    modelYear = online.modelYear ?: modelYear,
    modelYearIsAmbiguous = if (online.modelYear != null) false else modelYearIsAmbiguous,
    alternativeModelYear = if (online.modelYear != null) null else alternativeModelYear,
    trim = online.trim ?: trim,
    series = online.series ?: series,
    bodyClass = online.bodyClass ?: bodyClass,
    vehicleType = online.vehicleType ?: vehicleType,
    engine = online.engineSummary ?: engine,
    displacementLitres = online.displacementLitres ?: displacementLitres,
    cylinders = online.cylinders ?: cylinders,
    horsepower = online.horsepower ?: horsepower,
    fuelType = online.fuelType ?: fuelType,
    fuelTypeLabel = online.fuelTypeLabel ?: fuelTypeLabel,
    transmission = online.transmission ?: transmission,
    driveType = online.driveType ?: driveType,
    doors = online.doors ?: doors,
    plant = online.plant ?: plant,
)
