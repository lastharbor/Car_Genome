package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A line in the maintenance plan, such as "oil every 10 000 km or 12 months".
 *
 * Both intervals are optional and either one can fire first, which is exactly
 * how manufacturers write the schedule: a car that barely moves still needs its
 * oil changed on time.
 */
@Entity(
    tableName = "maintenance_schedules",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["vehicleId"])],
)
data class MaintenanceScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,

    val title: String,
    val category: ServiceCategory = ServiceCategory.RoutineService,

    @ColumnInfo(name = "interval_km") val intervalKm: Double? = null,
    val intervalMonths: Int? = null,

    @ColumnInfo(name = "last_performed_at") val lastPerformedAt: Instant? = null,
    @ColumnInfo(name = "last_performed_odometer_km") val lastPerformedOdometerKm: Double? = null,

    /** How early the item should start showing as due. */
    @ColumnInfo(name = "warn_before_km") val warnBeforeKm: Double = 500.0,
    val warnBeforeDays: Int = 14,

    val isEnabled: Boolean = true,
    val notes: String? = null,
)
