package com.cargenome.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVinDecoderTest {

    private val registry = JsonWmiRegistry.of(
        listOf(
            WmiEntry(code = "1HG", manufacturer = "Honda of America Mfg.", make = "Honda", country = "US"),
            WmiEntry(code = "XTA", manufacturer = "AvtoVAZ", make = "LADA", country = "RU"),
            WmiEntry(code = "X7L", manufacturer = "Renault Russia", make = "Renault", country = "RU"),
            WmiEntry(code = "SA9", manufacturer = "Low volume manufacturer, United Kingdom", make = "Generic"),
            WmiEntry(code = "SA9ABC", manufacturer = "Morgan Motor Company", make = "Morgan", country = "GB"),
        ),
    )

    private val decoder = OfflineVinDecoder(registry) { 2026 }

    private fun decode(raw: String): DecodedVin =
        (decoder.decode(raw) as VinDecodeResult.Decoded).value

    @Test
    fun `splits a vin into its three standard sections`() {
        val decoded = decode("1HGCM82633A004352")

        assertEquals("1HG", decoded.wmi)
        assertEquals("CM8263", decoded.vds)
        assertEquals("3A004352", decoded.vis)
        assertEquals('A', decoded.plantCode)
        assertEquals("004352", decoded.serialNumber)
    }

    @Test
    fun `accepts a vin typed with spaces, dashes and lower case`() {
        val decoded = decode(" wvw-zzz1jz 3w386752 ")

        assertEquals("WVWZZZ1JZ3W386752", decoded.vin)
        assertEquals("DE", decoded.country?.code)
    }

    @Test
    fun `a vin of the wrong length cannot be decoded at all`() {
        val result = decoder.decode("1HGCM82633A00435")

        assertEquals(
            VinDecodeResult.Malformed("1HGCM82633A00435", listOf(VinProblem.WrongLength(16))),
            result,
        )
    }

    @Test
    fun `illegal characters are reported by their one based position`() {
        val result = decoder.decode("1HGCM82633A0O4352") as VinDecodeResult.Malformed

        assertEquals(listOf(VinProblem.IllegalCharacters(listOf(13))), result.problems)
    }

    @Test
    fun `a clean north american vin has no problems`() {
        val decoded = decode("1HGCM82633A004352")

        assertEquals(emptyList<VinProblem>(), decoded.problems)
        assertTrue(decoded.checkDigit.matches)
        assertTrue(decoded.checkDigit.isMandatory)
        assertEquals(ModelYearResult.Resolved(2003), decoded.modelYear)
        assertEquals("Honda", decoded.make)
        assertEquals("US", decoded.country?.code)
    }

    @Test
    fun `a broken check digit on a north american vin is a hard error`() {
        val decoded = decode("1HGCM82643A004352")

        assertEquals(
            listOf(VinProblem.CheckDigitMismatch(expected = '3', actual = '4', mandatory = true)),
            decoded.problems,
        )
        assertTrue(decoded.checkDigit.isError)
    }

    @Test
    fun `a russian vin decodes offline even though no online decoder knows it`() {
        val decoded = decode("XTA21140053912345")

        assertEquals("RU", decoded.country?.code)
        assertEquals(VinRegion.Europe, decoded.region)
        assertEquals("LADA", decoded.make)
        // Position 9 is not a check digit outside North America and China.
        assertTrue(decoded.checkDigit.isWarning)
        assertEquals(
            listOf(VinProblem.CheckDigitMismatch(expected = '4', actual = '0', mandatory = false)),
            decoded.problems,
        )
    }

    @Test
    fun `the wmi dataset fills in countries the ISO chart leaves unassigned`() {
        assertNull(VinCountries.resolve("X7LLSRB2HDH123456"))

        val decoded = decode("X7LLSRB2HDH123456")

        assertEquals("RU", decoded.country?.code)
        assertEquals(VinRegion.Europe, decoded.region)
    }

    @Test
    fun `small volume manufacturers are identified by positions twelve to fourteen`() {
        // A WMI ending in 9 means under 500 vehicles a year, and positions
        // 12-14 finish the identifier.
        val morgan = decode("SA911111111ABC123")
        assertEquals("Morgan", morgan.make)

        val other = decode("SA911111111XYZ123")
        assertEquals("Generic", other.make)
    }

    @Test
    fun `without a wmi dataset the structural decoding still works`() {
        val bare = OfflineVinDecoder(currentYear = { 2026 })

        val decoded = (bare.decode("WVWZZZ1JZ3W386752") as VinDecodeResult.Decoded).value

        assertNull(decoded.manufacturer)
        assertEquals("DE", decoded.country?.code)
        assertEquals(ModelYearResult.Resolved(2003), decoded.modelYear)
    }
}
