package com.cargenome.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class ReceiptScanResult(
    val totalCost: Double? = null,
    val volumeLitres: Double? = null,
    val unitPrice: Double? = null,
    val station: String? = null,
    val date: LocalDate? = null,
    val rawText: String = "",
) {
    val hasData: Boolean
        get() = totalCost != null || volumeLitres != null || station != null || date != null
}

@Singleton
class ReceiptOcrScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scanReceipt(uri: Uri): ReceiptScanResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        val result = parseReceiptText(visionText.text)
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resume(ReceiptScanResult(rawText = "Ошибка OCR: ${error.message}"))
                    }
                }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(ReceiptScanResult(rawText = "Ошибка загрузки: ${e.message}"))
            }
        }
    }

    private fun parseReceiptText(text: String): ReceiptScanResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var total: Double? = null
        var volume: Double? = null
        var unitPrice: Double? = null
        var station: String? = null
        var date: LocalDate? = null

        // 1. Detect gas station or service brand
        val upperText = text.uppercase()
        when {
            upperText.contains("ЛУКОЙЛ") || upperText.contains("LUKOIL") -> station = "Лукойл"
            upperText.contains("ГАЗПРОМНЕФТЬ") || upperText.contains("G-DRIVE") || upperText.contains("ГПН") -> station = "Газпромнефть"
            upperText.contains("РОСНЕФТЬ") || upperText.contains("PULSAR") || upperText.contains("РН-МОСКВА") -> station = "Роснефть"
            upperText.contains("ТАТНЕФТЬ") || upperText.contains("ТАНЕКО") -> station = "Татнефть"
            upperText.contains("ТЕБОЙЛ") || upperText.contains("TEBOIL") -> station = "Teboil"
            upperText.contains("БАШНЕФТЬ") -> station = "Башнефть"
            upperText.contains("ШЕЛЛ") || upperText.contains("SHELL") -> station = "Шелл"
            upperText.contains("НЕФТЬМАГИСТРАЛЬ") -> station = "Нефтьмагистраль"
            upperText.contains("FIT SERVICE") -> station = "FIT Service"
            upperText.contains("EUROAUTO") -> station = "EuroAuto"
        }

        // 2. Detect dates: dd.MM.yyyy or dd/MM/yyyy or yyyy-MM-dd
        val dateRegex = Regex("""\b(\d{2})[./](\d{2})[./](20\d{2})\b""")
        for (line in lines) {
            val match = dateRegex.find(line)
            if (match != null) {
                val (d, m, y) = match.destructured
                runCatching {
                    date = LocalDate.of(y.toInt(), m.toInt(), d.toInt())
                }
                if (date != null) break
            }
        }

        // 3. Detect volume and price lines like "52.30 * 62.00 = 3242.60" or "Количество: 50.00"
        val multiplyRegex = Regex("""(\d{1,3}[.,]\d{1,3})\s*(?:л|L|литр)?\s*[*xX]\s*(\d{1,3}[.,]\d{2})""")
        for (line in lines) {
            val multMatch = multiplyRegex.find(line)
            if (multMatch != null) {
                val volVal = multMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                val priceVal = multMatch.groupValues[2].replace(',', '.').toDoubleOrNull()
                if (volVal != null && volVal in 1.0..250.0) {
                    volume = volVal
                }
                if (priceVal != null && priceVal in 20.0..150.0) {
                    unitPrice = priceVal
                }
            }
        }

        if (volume == null) {
            // Check standalone volume: "52.30 л" or "50 л"
            val volRegex = Regex("""\b(\d{1,3}[.,]\d{1,3})\s*(?:л\b|литр|L\b)""", RegexOption.IGNORE_CASE)
            for (line in lines) {
                val match = volRegex.find(line)
                if (match != null) {
                    val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null && v in 2.0..200.0) {
                        volume = v
                        break
                    }
                }
            }
        }

        // 4. Detect total amount (ИТОГ, ИТОГО, СУММА, ВСЕГО, TOTAL, К ОПЛАТЕ)
        val totalKeywords = listOf("ИТОГ", "ИТОГО", "К ОПЛАТЕ", "СУММА", "ВСЕГО", "TOTAL", "ПОЛНЫЙ РАСЧЕТ", "РАСЧЕТ")
        val moneyRegex = Regex("""(\d{1,6}[.,]\d{2})""")

        for (i in lines.indices) {
            val lineUpper = lines[i].uppercase()
            val hasKeyword = totalKeywords.any { lineUpper.contains(it) }
            if (hasKeyword) {
                // Find money in current line or next line
                val matchInLine = moneyRegex.findAll(lines[i]).lastOrNull()
                if (matchInLine != null) {
                    val amount = matchInLine.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        total = amount
                        break
                    }
                } else if (i + 1 < lines.size) {
                    val matchInNext = moneyRegex.findAll(lines[i + 1]).firstOrNull()
                    if (matchInNext != null) {
                        val amount = matchInNext.groupValues[1].replace(',', '.').toDoubleOrNull()
                        if (amount != null && amount > 0) {
                            total = amount
                            break
                        }
                    }
                }
            }
        }

        // If total wasn't found by keyword, but volume and unitPrice exist, compute total
        if (total == null && volume != null && unitPrice != null) {
            total = kotlin.math.round(volume * unitPrice * 100.0) / 100.0
        }

        // Fallback: look for the highest sensible sum in receipt
        if (total == null) {
            val candidates = lines.mapNotNull { line ->
                moneyRegex.findAll(line).mapNotNull {
                    it.groupValues[1].replace(',', '.').toDoubleOrNull()
                }.filter { it in 50.0..500000.0 }.maxOrNull()
            }
            total = candidates.maxOrNull()
        }

        return ReceiptScanResult(
            totalCost = total,
            volumeLitres = volume,
            unitPrice = unitPrice,
            station = station,
            date = date,
            rawText = text,
        )
    }
}
