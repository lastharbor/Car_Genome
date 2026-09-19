package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Where a mileage figure came from, which decides whether it can be edited on its own. */
enum class OdometerSource {
    Manual,
    FuelRecord,
    ServiceRecord,
    Expense,
}

@Entity(
    tableName = "odometer_readings",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["vehicleId", "recordedAt"])],
)
@androidx.compose.runtime.Immutable
data class OdometerReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,

    val recordedAt: Instant,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double,
    val source: OdometerSource = OdometerSource.Manual,
    /** Row id of the fuel, service or expense record this reading came in with. */
    val sourceRecordId: Long? = null,
    val note: String? = null,
)
