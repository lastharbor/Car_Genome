package com.cargenome.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class LoyaltyCategory {
    Fuel,
    Wash,
    Service,
    Parts,
    Insurance,
    Other,
}

enum class BarcodeType {
    Ean13,
    Code128,
    QrCode,
    Code39,
    Pdf417,
    Other,
}

@Entity(
    tableName = "loyalty_cards",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["category"]),
    ],
)
@Immutable
data class LoyaltyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val cardNumber: String,
    val barcodeType: BarcodeType = BarcodeType.Code128,
    val barcodeRawValue: String = cardNumber,
    val category: LoyaltyCategory = LoyaltyCategory.Fuel,
    val colorHex: Long = 0xFF1976D2,
    val note: String? = null,
    val vehicleId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
