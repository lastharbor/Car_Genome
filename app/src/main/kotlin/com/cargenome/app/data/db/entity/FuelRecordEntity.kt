package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One visit to the pump.
 *
 * Cost is stored as a total in minor currency units rather than as a price per
 * litre, because the total is what the receipt says and deriving the price back
 * from it always agrees with the arithmetic. Volume is in litres regardless of
 * what the owner types.
 *
 * [isFullTank] and [missedPreviousFillUp] are what make consumption
 * calculable. The tank-to-tank method needs two consecutive full tanks with
 * nothing unrecorded in between; these two flags are how the user says so.
 */
@Entity(
    tableName = "fuel_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["vehicleId", "filledAt"])],
)
@androidx.compose.runtime.Immutable
data class FuelRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,

    val filledAt: Instant,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double,
    @ColumnInfo(name = "volume_litres") val volumeLitres: Double,
    @ColumnInfo(name = "total_cost_minor") val totalCostMinor: Long,

    val station: String? = null,
    val isFullTank: Boolean = true,
    /** Set when the owner knows a fill-up between this one and the last went unrecorded. */
    val missedPreviousFillUp: Boolean = false,
    val notes: String? = null,
)
