package com.cargenome.app.ui.common

import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

import java.util.concurrent.ConcurrentHashMap

/**
 * Formatting for the values the app stores canonically.
 *
 * Distances live in kilometres and volumes in litres no matter what the car is
 * set to, so every display of them has to convert first. Keeping that in one
 * place is what stops a screen from quietly showing kilometres to someone who
 * asked for miles.
 */
object Format {

    private val currencyCache = ConcurrentHashMap<String, Currency?>()
    private val moneyFormatCache = ConcurrentHashMap<String, NumberFormat>()
    private val integerFormatCache = ConcurrentHashMap<Locale, NumberFormat>()
    private val decimalFormatCache = ConcurrentHashMap<Pair<Locale, Int>, NumberFormat>()
    private val dateFormatCache = ConcurrentHashMap<Locale, DateTimeFormatter>()

    fun distance(kilometres: Double, unit: DistanceUnit, locale: Locale): String {
        val value = unit.fromKilometres(kilometres)
        val format = integerFormatCache.computeIfAbsent(locale) { loc ->
            NumberFormat.getIntegerInstance(loc).apply { isGroupingUsed = true }
        }
        return synchronized(format) { format.format(value) }
    }

    fun volume(litres: Double, unit: VolumeUnit, locale: Locale): String {
        val format = decimalFormatCache.computeIfAbsent(locale to 2) { (loc, digits) ->
            NumberFormat.getNumberInstance(loc).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
        }
        return synchronized(format) { format.format(unit.fromLitres(litres)) }
    }

    fun consumption(value: Double, locale: Locale): String {
        val format = decimalFormatCache.computeIfAbsent(locale to 1) { (loc, digits) ->
            NumberFormat.getNumberInstance(loc).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
        }
        return synchronized(format) { format.format(value) }
    }

    /**
     * Money is stored in minor units, and how many of those make a major one
     * depends on the currency: 100 kopecks to the rouble, but the yen has none
     * at all.
     */
    fun money(minorUnits: Long, currencyCode: String, locale: Locale): String {
        val currency = currencyCache.computeIfAbsent(currencyCode) { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()
        } ?: return distance(minorUnits / 100.0, DistanceUnit.Kilometres, locale)

        val key = "${locale.toLanguageTag()}_$currencyCode"
        val format = moneyFormatCache.computeIfAbsent(key) {
            NumberFormat.getCurrencyInstance(locale).apply {
                this.currency = currency
                maximumFractionDigits = 0.coerceAtLeast(currency.defaultFractionDigits)
                minimumFractionDigits = maximumFractionDigits
            }
        }
        val value = minorUnits.toDouble() / minorScale(currency)
        return synchronized(format) { format.format(value) }
    }

    fun minorScale(currency: Currency): Double {
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        return Math.pow(10.0, digits.toDouble())
    }

    fun minorScale(currencyCode: String): Double {
        val currency = currencyCache.computeIfAbsent(currencyCode) { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()
        }
        return currency?.let(::minorScale) ?: 100.0
    }

    fun date(date: LocalDate, locale: Locale): String {
        val formatter = dateFormatCache.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc)
        }
        return formatter.format(date)
    }

    fun date(instant: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        date(instant.atZone(zone).toLocalDate(), locale)
}

fun com.cargenome.app.data.db.entity.VehicleEntity.displayName(): String =
    nickname?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }
        ?: vin?.take(8)
        ?: "Car"

