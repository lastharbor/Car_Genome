package com.cargenome.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class ExpenseCategory {
    Insurance,
    Tax,
    Tyres,
    Fine,
    Wash,
    Parking,
    Toll,
    Registration,
    Accessories,
    Credit,
    Other,
}

/** Money the car costs that is neither fuel nor a repair. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["vehicleId", "incurredAt"])],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,

    val incurredAt: Instant,
    val category: ExpenseCategory = ExpenseCategory.Other,
    val title: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double? = null,
    val notes: String? = null,
)
