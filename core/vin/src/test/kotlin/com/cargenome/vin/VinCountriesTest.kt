package com.cargenome.vin

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VinCountriesTest {

    private fun code(vin: String) = VinCountries.resolve(vin)?.code

    private fun region(vin: String) = VinCountries.resolve(vin)?.region

    @Test
    fun `resolves the countries behind common vin prefixes`() {
        assertEquals("DE", code("WVWZZZ1JZ3W386752"))
        assertEquals("JP", code("JTDKB20U887746531"))
        assertEquals("US", code("1HGCM82633A004352"))
        assertEquals("KR", code("KLATF08Y1VB363636"))
        assertEquals("IT", code("ZAR93900001234567"))
        assertEquals("SE", code("YS3DD78N4X7055320"))
        assertEquals("FR", code("VF7XXXXXXXXXXXXXX"))
        assertEquals("GB", code("SAJXXXXXXXXXXXXXX"))
    }

    @Test
    fun `resolves russian prefixes across the allocated ranges`() {
        assertEquals("RU", code("XTA21140053912345"))
        assertEquals("RU", code("XW8ZZZ5NZJG123456"))
        assertEquals("RU", code("Z8NTBNJ12ES123456"))
        assertEquals("RU", code("XDAXXXXXXXXXXXXXX"))
        assertEquals("RU", code("EAAXXXXXXXXXXXXXX"))
    }

    @Test
    fun `prefixes ISO never allocated stay unresolved here`() {
        // X7L is Renault Russia and X96 is GAZ, but the ISO chart stops at X1:
        // XZ-X1 is Russia and X2 through X0 are unassigned. The WMI dataset
        // supplies the country for these, see OfflineVinDecoderTest.
        assertNull(VinCountries.resolve("X7LLSRB2HDH123456"))
        assertNull(VinCountries.resolve("X96XXXXXXXXXXXXXX"))
    }

    @Test
    fun `a narrow allocation wins over the broad one it sits inside`() {
        // 6 is Australia, but 6Y-61 is carved out for New Zealand.
        assertEquals("AU", code("6AAXXXXXXXXXXXXXX"))
        assertEquals("NZ", code("6YAXXXXXXXXXXXXXX"))
        assertEquals("NZ", code("61AXXXXXXXXXXXXXX"))

        // 3A-3X is Mexico, with single-code carve-outs for Central America.
        assertEquals("MX", code("3AAXXXXXXXXXXXXXX"))
        assertEquals("NI", code("34AXXXXXXXXXXXXXX"))
        assertEquals("PR", code("38AXXXXXXXXXXXXXX"))
    }

    @Test
    fun `ranges treat zero as the last character, not as part of the digits run`() {
        // Z6-Z0 is Russia and spans Z6, Z7, Z8, Z9, Z0.
        assertEquals("RU", code("Z6AXXXXXXXXXXXXXX"))
        assertEquals("RU", code("Z0AXXXXXXXXXXXXXX"))
        // Z3-Z5 stops before Z6.
        assertEquals("LT", code("Z3AXXXXXXXXXXXXXX"))
    }

    @Test
    fun `maps first characters to regions`() {
        assertEquals(VinRegion.Europe, region("WVWZZZ1JZ3W386752"))
        assertEquals(VinRegion.Asia, region("JTDKB20U887746531"))
        assertEquals(VinRegion.NorthAmerica, region("1HGCM82633A004352"))
        assertEquals(VinRegion.Africa, region("AAAXXXXXXXXXXXXXX"))
        assertEquals(VinRegion.Oceania, region("6AAXXXXXXXXXXXXXX"))
        assertEquals(VinRegion.SouthAmerica, region("9BWXXXXXXXXXXXXXX"))
    }

    @Test
    fun `returns nothing for unassigned prefixes`() {
        assertNull(VinCountries.resolve("0AAXXXXXXXXXXXXXX"))
        assertNull(VinCountries.resolve("W"))
        assertNull(VinCountries.resolve(""))
    }

    @Test
    fun `country names come from the platform, localised`() {
        val russia = VinCountries.resolve("XTA21140053912345")!!

        assertEquals("Russia", russia.displayName(Locale.ENGLISH))
        assertEquals("Россия", russia.displayName(Locale.forLanguageTag("ru")))
    }
}
