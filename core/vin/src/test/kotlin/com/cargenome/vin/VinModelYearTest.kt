package com.cargenome.vin

import org.junit.Assert.assertEquals
import org.junit.Test

class VinModelYearTest {

    private val today = 2026

    private fun vin(
        first: Char = 'W',
        positionSeven: Char = 'Z',
        positionTen: Char = 'A',
    ): String {
        val chars = CharArray(VinFormat.LENGTH) { 'X' }
        chars[0] = first
        chars[6] = positionSeven
        chars[9] = positionTen
        return String(chars)
    }

    @Test
    fun `reads the year of real vins`() {
        assertEquals(
            ModelYearResult.Resolved(2003),
            VinModelYear.decode("1HGCM82633A004352", today),
        )
        assertEquals(
            ModelYearResult.Resolved(2008),
            VinModelYear.decode("JTDKB20U887746531", today),
        )
    }

    @Test
    fun `north america resolves the cycle with position seven`() {
        assertEquals(
            ModelYearResult.Resolved(1980),
            VinModelYear.decode(vin(first = '1', positionSeven = '4', positionTen = 'A'), today),
        )
        assertEquals(
            ModelYearResult.Resolved(2010),
            VinModelYear.decode(vin(first = '1', positionSeven = 'B', positionTen = 'A'), today),
        )
    }

    @Test
    fun `elsewhere the thirty year cycle stays ambiguous`() {
        assertEquals(
            ModelYearResult.Ambiguous(preferred = 2010, candidates = listOf(1980, 2010)),
            VinModelYear.decode(vin(first = 'W', positionTen = 'A'), today),
        )
    }

    @Test
    fun `a code whose next cycle has not arrived yet is unambiguous`() {
        // W is 1998; 2028 is more than a model year away, so only 1998 fits.
        assertEquals(
            ModelYearResult.Resolved(1998),
            VinModelYear.decode(vin(positionTen = 'W'), today),
        )
    }

    @Test
    fun `model years are allowed to run one year ahead of the calendar`() {
        // V is 1997 and 2027. A 2027 model can be on sale during 2026.
        assertEquals(
            ModelYearResult.Ambiguous(preferred = 2027, candidates = listOf(1997, 2027)),
            VinModelYear.decode(vin(positionTen = 'V'), today),
        )
    }

    @Test
    fun `characters that are not year codes mean the year is not encoded`() {
        listOf('U', 'Z', '0', 'I', 'O', 'Q').forEach { code ->
            assertEquals(
                "position ten '$code' should not decode to a year",
                ModelYearResult.NotEncoded,
                VinModelYear.decode(vin(positionTen = code), today),
            )
        }
    }

    @Test
    fun `digits one to nine cover 2001 to 2009`() {
        assertEquals(ModelYearResult.Resolved(2001), VinModelYear.decode(vin(positionTen = '1'), today))
        assertEquals(ModelYearResult.Resolved(2009), VinModelYear.decode(vin(positionTen = '9'), today))
    }
}
