package com.cargenome.app.data.network

import com.cargenome.app.domain.model.FuelType
import java.util.Locale

/**
 * The parts of a vPIC answer the app actually uses, lifted out of the flat map
 * of strings. [raw] keeps the whole thing so nothing is lost on the way to the
 * cache or to a future version that wants another field.
 */
data class VpicVehicle(
    val make: String? = null,
    val model: String? = null,
    val modelYear: Int? = null,
    val trim: String? = null,
    val series: String? = null,
    val manufacturer: String? = null,
    val bodyClass: String? = null,
    val vehicleType: String? = null,
    val displacementLitres: Double? = null,
    val cylinders: Int? = null,
    val horsepower: Int? = null,
    val engineModel: String? = null,
    val fuelTypeLabel: String? = null,
    val transmission: String? = null,
    val driveType: String? = null,
    val doors: Int? = null,
    val plant: String? = null,
    val errorCode: String? = null,
    val errorText: String? = null,
    val raw: Map<String, String> = emptyMap(),
) {
    /** True when vPIC answered but had nothing to say, which is normal outside the US. */
    val isEmpty: Boolean
        get() = make.isNullOrBlank() && model.isNullOrBlank() && manufacturer.isNullOrBlank()

    val fuelType: FuelType? get() = fuelTypeOf(fuelTypeLabel)

    /** A one-line engine description for the vehicle form, when there is enough to say. */
    val engineSummary: String?
        get() {
            val parts = buildList {
                displacementLitres?.let { add("%.1f L".format(Locale.US, it)) }
                cylinders?.let { add("${it}-cyl") }
                horsepower?.takeIf { it > 0 }?.let { add("$it hp") }
                engineModel?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

    companion object {
        /**
         * vPIC writes "not applicable" as an empty string, and occasionally as
         * the literal "Not Applicable". Both mean the same thing here: leave
         * whatever the offline decoder worked out alone.
         */
        private fun String?.orNull(): String? = this
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("Not Applicable", ignoreCase = true) }

        fun fromResult(result: Map<String, String?>): VpicVehicle {
            val raw = result.mapNotNull { (key, value) ->
                value.orNull()?.let { key to it }
            }.toMap()

            fun field(name: String): String? = raw[name]

            return VpicVehicle(
                make = field("Make"),
                model = field("Model"),
                modelYear = field("ModelYear")?.toIntOrNull(),
                trim = field("Trim") ?: field("Trim2"),
                series = field("Series") ?: field("Series2"),
                manufacturer = field("Manufacturer"),
                bodyClass = field("BodyClass"),
                vehicleType = field("VehicleType"),
                displacementLitres = field("DisplacementL")?.toDoubleOrNull(),
                cylinders = field("EngineCylinders")?.toIntOrNull(),
                horsepower = field("EngineHP")?.toDoubleOrNull()?.toInt(),
                engineModel = field("EngineModel"),
                fuelTypeLabel = field("FuelTypePrimary"),
                transmission = listOfNotNull(
                    field("TransmissionStyle"),
                    field("TransmissionSpeeds")?.let { "$it-speed" },
                ).takeIf { it.isNotEmpty() }?.joinToString(" "),
                driveType = field("DriveType"),
                doors = field("Doors")?.toIntOrNull(),
                plant = listOfNotNull(field("PlantCity"), field("PlantCountry"))
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", "),
                errorCode = field("ErrorCode"),
                errorText = field("ErrorText"),
                raw = raw,
            )
        }

        private fun fuelTypeOf(label: String?): FuelType? = when {
            label.isNullOrBlank() -> null
            label.contains("Diesel", ignoreCase = true) -> FuelType.Diesel
            label.contains("Electric", ignoreCase = true) -> FuelType.Electric
            label.contains("Compressed Natural Gas", ignoreCase = true) ||
                label.contains("CNG", ignoreCase = true) -> FuelType.Cng
            label.contains("Propane", ignoreCase = true) ||
                label.contains("LPG", ignoreCase = true) -> FuelType.Lpg
            label.contains("Gasoline", ignoreCase = true) ||
                label.contains("Petrol", ignoreCase = true) ||
                label.contains("Flexible Fuel", ignoreCase = true) -> FuelType.Petrol
            else -> FuelType.Other
        }
    }

    fun toExternalData(): com.cargenome.app.data.vin.ExternalVehicleData = com.cargenome.app.data.vin.ExternalVehicleData(
        manufacturer = manufacturer,
        make = make,
        model = model,
        modelYear = modelYear,
        trim = trim,
        series = series,
        bodyClass = bodyClass,
        vehicleType = vehicleType,
        engineSummary = engineSummary,
        displacementLitres = displacementLitres,
        cylinders = cylinders,
        horsepower = horsepower,
        fuelType = fuelType,
        fuelTypeLabel = fuelTypeLabel,
        transmission = transmission,
        driveType = driveType,
        doors = doors,
        plant = plant,
    )
}
