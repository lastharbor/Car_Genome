package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class ServiceCategory {
    RoutineService,
    Engine,
    Transmission,
    Brakes,
    Suspension,
    Electrical,
    Tyres,
    Body,
    Diagnostics,
    Other,
}

/**
 * Work done on the car.
 *
 * Labour and parts are kept apart because that is how invoices are written and
 * how owners think about whether a job was worth it.
 */
@Entity(
    tableName = "service_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MaintenanceScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["vehicleId", "performedAt"]),
        Index(value = ["scheduleId"]),
    ],
)
@androidx.compose.runtime.Immutable
data class ServiceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,

    val performedAt: Instant,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double? = null,

    val category: ServiceCategory = ServiceCategory.RoutineService,
    val title: String,
    @ColumnInfo(name = "labour_cost_minor") val labourCostMinor: Long = 0,
    @ColumnInfo(name = "parts_cost_minor") val partsCostMinor: Long = 0,

    val shop: String? = null,
    val notes: String? = null,

    /** The scheduled item this job resets, when it was done against the plan. */
    val scheduleId: Long? = null,
)

val ServiceRecordEntity.totalCostMinor: Long get() = labourCostMinor + partsCostMinor
