package com.cargenome.app.domain.loyalty

import android.graphics.Bitmap
import android.graphics.Color
import com.cargenome.app.data.db.entity.BarcodeType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.EnumMap

object BarcodeGenerator {

    fun toZxingFormat(type: BarcodeType): BarcodeFormat = when (type) {
        BarcodeType.Ean13 -> BarcodeFormat.EAN_13
        BarcodeType.Code128 -> BarcodeFormat.CODE_128
        BarcodeType.QrCode -> BarcodeFormat.QR_CODE
        BarcodeType.Code39 -> BarcodeFormat.CODE_39
        BarcodeType.Pdf417 -> BarcodeFormat.PDF_417
        BarcodeType.Other -> BarcodeFormat.CODE_128
    }

    fun fromMlKitFormat(format: Int): BarcodeType = when (format) {
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> BarcodeType.Ean13
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> BarcodeType.Code128
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> BarcodeType.QrCode
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> BarcodeType.Code39
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> BarcodeType.Pdf417
        else -> BarcodeType.Other
    }

    fun generateBitmap(
        content: String,
        type: BarcodeType,
        width: Int = 800,
        height: Int = if (type == BarcodeType.QrCode) 800 else 300,
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val format = toZxingFormat(type)
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            val bitMatrix = MultiFormatWriter().encode(content, format, width, height, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)
            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (e: Exception) {
            // Fallback for EAN-13 length mismatches or invalid characters in specific barcode formats:
            // try fallback to CODE_128 or return null
            if (type != BarcodeType.Code128 && type != BarcodeType.QrCode) {
                generateBitmap(content, BarcodeType.Code128, width, height)
            } else {
                null
            }
        }
    }
}
