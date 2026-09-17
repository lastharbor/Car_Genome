package com.cargenome.vin

import java.util.Locale

enum class VinRegion {
    Africa,
    Asia,
    Europe,
    NorthAmerica,
    Oceania,
    SouthAmerica,
}

/**
 * Country the VIN was assigned to, identified by ISO 3166-1 alpha-2 code.
 *
 * Only the code is stored: the display name comes from the platform, which
 * already knows how to say "Germany" in every locale the app ships in.
 */
data class VinCountry(
    val code: String,
    val region: VinRegion,
) {
    fun displayName(locale: Locale = Locale.getDefault()): String =
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
}

/**
 * Country allocations for VIN positions 1-2, per the ISO 3780 world codes chart.
 *
 * Ranges follow the VIN alphabet, where the digit 0 sorts last: the published
 * range 8X-82 means 8X, 8Y, 8Z, 81, 82 and deliberately excludes 80.
 */
object VinCountries {

    /** VIN alphabet in collation order. I, O and Q are not valid VIN characters. */
    private const val ORDER = "ABCDEFGHJKLMNPRSTUVWXYZ1234567890"

    private class Allocation(
        val first: Char,
        val fromIndex: Int,
        val toIndex: Int,
        val country: String,
    ) {
        val width: Int get() = toIndex - fromIndex
    }

    private val allocations: List<Allocation> = buildList {
        fun range(spec: String, country: String) {
            val first = spec[0]
            val from: Char
            val to: Char
            if (spec.length == 1) {
                from = ORDER.first()
                to = ORDER.last()
            } else if (spec.length == 2) {
                from = spec[1]
                to = spec[1]
            } else {
                from = spec[1]
                to = spec[4]
            }
            add(Allocation(first, ORDER.indexOf(from), ORDER.indexOf(to), country))
        }

        // Africa
        range("AA-AH", "ZA"); range("AJ-AK", "CI"); range("AL-AM", "LS")
        range("AN-AP", "BW"); range("AR-AS", "NA"); range("AT-AU", "MG")
        range("AV-AW", "MU"); range("AX-AY", "TN"); range("AZ-A1", "CY")
        range("A2-A3", "ZW"); range("A4-A5", "MZ")
        range("BA-BB", "AO"); range("BC", "ET"); range("BF-BG", "KE")
        range("BH", "RW"); range("BL", "NG"); range("BR", "DZ")
        range("BT", "SZ"); range("BU", "UG"); range("B3-B4", "LY")
        range("CA-CB", "EG"); range("CF-CG", "MA"); range("CL-CM", "ZM")

        // Asia
        range("H", "CN"); range("J", "JP")
        range("KF-KH", "IL"); range("KL-KR", "KR"); range("KS-KT", "JO")
        range("K1-K3", "KR"); range("K5", "KG")
        range("L", "CN")
        range("MA-ME", "IN"); range("MF-MK", "ID"); range("ML-MR", "TH")
        range("MS", "MM"); range("MU", "MN"); range("MX", "KZ"); range("MY-M0", "IN")
        range("NA-NE", "IR"); range("NF-NG", "PK"); range("NJ", "IQ")
        range("NL-NR", "TR"); range("NS-NT", "UZ"); range("NV", "AZ")
        range("NX", "TJ"); range("NY", "AM"); range("N1-N5", "IR"); range("N7-N8", "TR")
        range("PA-PC", "PH"); range("PF-PG", "SG"); range("PL-PR", "MY")
        range("PS-PT", "BD"); range("PV", "KH"); range("P5-P0", "IN")
        range("RA-RB", "AE"); range("RF-RK", "TW"); range("RL-RN", "VN")
        range("RP", "LA"); range("RS-RT", "SA"); range("R1-R7", "HK")

        // Europe
        range("E", "RU")
        range("SA-SM", "GB"); range("SN-ST", "DE"); range("SU-SZ", "PL")
        range("S1-S2", "LV"); range("S3", "GE"); range("S4", "IS")
        range("TA-TH", "CH"); range("TJ-TP", "CZ"); range("TR-TV", "HU")
        range("TW-T2", "PT"); range("T3-T5", "RS"); range("T6", "AD"); range("T7-T8", "NL")
        range("UA-UC", "ES"); range("UH-UM", "DK"); range("UN-UR", "IE")
        range("UU-UX", "RO"); range("U1-U2", "MK"); range("U5-U7", "SK"); range("U8-U0", "BA")
        range("VA-VE", "AT"); range("VF-VR", "FR"); range("VS-VW", "ES")
        range("VX-V2", "FR"); range("V3-V5", "HR"); range("V6-V8", "EE")
        range("W", "DE")
        range("XA-XC", "BG"); range("XD-XE", "RU"); range("XF-XH", "GR")
        range("XJ-XK", "RU"); range("XL-XR", "NL"); range("XS-XW", "RU")
        range("XX-XY", "LU"); range("XZ-X1", "RU")
        range("YA-YE", "BE"); range("YF-YK", "FI"); range("YN", "MT")
        range("YS-YW", "SE"); range("YX-Y2", "NO"); range("Y3-Y5", "BY"); range("Y6-Y9", "UA")
        range("ZA-ZU", "IT"); range("ZX-ZZ", "SI"); range("Z1", "SM")
        range("Z3-Z5", "LT"); range("Z6-Z0", "RU")

        // North America
        range("1", "US"); range("2", "CA")
        range("3A-3X", "MX"); range("34", "NI"); range("35", "DO")
        range("36", "HN"); range("37", "PA"); range("38-39", "PR")
        range("4", "US"); range("5", "US"); range("7", "US")

        // Oceania
        range("6", "AU"); range("6Y-61", "NZ")

        // South America
        range("8A-8E", "AR"); range("8F-8G", "CL"); range("8L-8N", "EC")
        range("8S-8W", "PE"); range("8X-8Z", "VE"); range("82", "BO"); range("84", "CR")
        range("9A-9E", "BR"); range("9F-9G", "CO"); range("9S-9V", "UY"); range("91-90", "BR")
    }

    fun regionOf(first: Char): VinRegion? = when (first) {
        'A', 'B', 'C' -> VinRegion.Africa
        'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R' -> VinRegion.Asia
        'E', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' -> VinRegion.Europe
        '1', '2', '3', '4', '5', '7' -> VinRegion.NorthAmerica
        '6' -> VinRegion.Oceania
        '8', '9' -> VinRegion.SouthAmerica
        else -> null
    }

    /**
     * Resolves positions 1-2 to a country. Where a broad allocation such as
     * "6 = Australia" overlaps a narrow one such as "6Y-61 = New Zealand", the
     * narrower allocation wins.
     */
    fun resolve(vin: String): VinCountry? {
        if (vin.length < 2) return null
        val first = vin[0]
        val second = vin[1]
        val region = regionOf(first) ?: return null
        val secondIndex = ORDER.indexOf(second)
        if (secondIndex < 0) return null

        val match = allocations
            .filter { it.first == first && secondIndex >= it.fromIndex && secondIndex <= it.toIndex }
            .minByOrNull { it.width }
            ?: return null

        return VinCountry(code = match.country, region = region)
    }
}
