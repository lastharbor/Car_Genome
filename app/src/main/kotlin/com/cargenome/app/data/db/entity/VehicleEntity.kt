package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import java.time.Instant
import java.time.LocalDate

/**
 * A car in the garage.
 *
 * The VIN is optional on purpose: an old car may have no readable plate left,
 * and the app has to be usable before the number is found. It is uniquely
 * indexed so the same car cannot be added twice, and SQLite treats NULLs as
 * distinct, which leaves room for several cars without a VIN.
 */
@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["vin"], unique = true)],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val vin: String? = null,
    val make: String,
    val model: String,
    val modelYear: Int? = null,
    val trim: String? = null,
    val engine: String? = null,
    val fuelType: FuelType = FuelType.Petrol,

    val plateNumber: String? = null,
    val nickname: String? = null,
    val photoUri: String? = null,

    val distanceUnit: DistanceUnit = DistanceUnit.Kilometres,
    val volumeUnit: VolumeUnit = VolumeUnit.Litres,
    /** ISO 4217, for example RUB or EUR. */
    val currencyCode: String = "RUB",

    val purchasedOn: LocalDate? = null,
    @ColumnInfo(name = "initial_odometer_km") val initialOdometerKm: Double = 0.0,

    val insuranceProvider: String? = null,
    val insurancePolicyNumber: String? = null,
    val insuranceExpiresOn: LocalDate? = null,
    val insurancePdfUri: String? = null,

    /** Merged offline and vPIC decode result, kept so the form can be re-filled. */
    val vinDecodeJson: String? = null,

    val createdAt: Instant = Instant.EPOCH,
    val isArchived: Boolean = false,
)
