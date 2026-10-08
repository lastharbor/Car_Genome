package com.cargenome.app.domain.loyalty

import com.cargenome.app.data.db.entity.BarcodeType
import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarcodeGeneratorTest {

    @Test
    fun toZxingFormat_mapsCorrectly() {
        assertEquals(BarcodeFormat.EAN_13, BarcodeGenerator.toZxingFormat(BarcodeType.Ean13))
        assertEquals(BarcodeFormat.CODE_128, BarcodeGenerator.toZxingFormat(BarcodeType.Code128))
        assertEquals(BarcodeFormat.QR_CODE, BarcodeGenerator.toZxingFormat(BarcodeType.QrCode))
        assertEquals(BarcodeFormat.CODE_39, BarcodeGenerator.toZxingFormat(BarcodeType.Code39))
        assertEquals(BarcodeFormat.PDF_417, BarcodeGenerator.toZxingFormat(BarcodeType.Pdf417))
        assertEquals(BarcodeFormat.CODE_128, BarcodeGenerator.toZxingFormat(BarcodeType.Other))
    }

    @Test
    fun fromMlKitFormat_mapsCorrectly() {
        assertEquals(BarcodeType.Ean13, BarcodeGenerator.fromMlKitFormat(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13))
        assertEquals(BarcodeType.Code128, BarcodeGenerator.fromMlKitFormat(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128))
        assertEquals(BarcodeType.QrCode, BarcodeGenerator.fromMlKitFormat(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE))
        assertEquals(BarcodeType.Code39, BarcodeGenerator.fromMlKitFormat(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39))
        assertEquals(BarcodeType.Pdf417, BarcodeGenerator.fromMlKitFormat(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417))
        assertEquals(BarcodeType.Other, BarcodeGenerator.fromMlKitFormat(9999))
    }

    @Test
    fun generateBitmap_returnsNullForBlank() {
        assertNull(BarcodeGenerator.generateBitmap("", BarcodeType.Code128))
        assertNull(BarcodeGenerator.generateBitmap("   ", BarcodeType.QrCode))
    }

    @Test
    fun generateBitmap_generatesValidBitmaps() {
        val code128 = BarcodeGenerator.generateBitmap("CARD12345678", BarcodeType.Code128)
        assertNotNull(code128)
        assertEquals(800, code128!!.width)

        val qr = BarcodeGenerator.generateBitmap("https://cargenome.app", BarcodeType.QrCode)
        assertNotNull(qr)
        assertEquals(800, qr!!.width)
        assertEquals(800, qr.height)
    }
}
