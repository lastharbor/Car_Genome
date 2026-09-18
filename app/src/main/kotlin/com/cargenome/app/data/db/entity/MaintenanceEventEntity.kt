package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A scheduled maintenance event on the calendar (e.g. oil change booked for next Friday,
 * winter tyre swap, brake pad replacement).
 *
 * Can trigger local notification alarms and be converted into a historical [ServiceRecordEntity]
 * once completed.
 */
@Entity(
    tableName = "maintenance_events",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ServiceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceRecordId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MaintenanceScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["scheduledDate"]),
        Index(value = ["serviceRecordId"]),
        Index(value = ["scheduleId"]),
    ],
)
data class MaintenanceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val scheduleId: Long? = null,

    val title: String,
    val category: ServiceCategory = ServiceCategory.RoutineService,

    /** Date when the maintenance is scheduled. */
    val scheduledDate: LocalDate,

    /** Time of day in minutes (e.g. 10:30 -> 10*60 + 30 = 630). Null if whole day / flexible. */
    val scheduledTimeMinutes: Int? = null,

    /** Target mileage at which this maintenance should be done (optional). */
    val targetOdometerKm: Double? = null,

    /** Estimated cost in minor units (e.g. 5000 RUB -> 500000). */
    val estimatedCostMinor: Long? = null,

    /** Workshop / mechanic name or address. */
    val shop: String? = null,

    /** Spare parts, checklist, or other notes. */
    val notes: String? = null,

    /**
     * Days in advance to trigger notification:
     *  0 = in the morning of the day (09:00),
     *  1 = 1 day before at 09:00,
     *  3 = 3 days before at 09:00,
     *  7 = 7 days before at 09:00,
     * -1 = notification disabled.
     */
    val remindAdvanceDays: Int = 0,

    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,

    /** ID of the created ServiceRecordEntity once completed and logged to history. */
    val serviceRecordId: Long? = null,

    val createdAt: Instant = Instant.now(),
)
