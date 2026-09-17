package com.cargenome.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinCheckDigitTest {

    @Test
    fun `computes the worked example from 49 CFR 565`() {
        assertEquals('X', VinCheckDigit.compute("1M8GDM9AXKP042788"))
    }

    @Test
    fun `seventeen ones check out to one`() {
        assertEquals('1', VinCheckDigit.compute("11111111111111111"))
    }

    @Test
    fun `computes the check digit of a real north american vin`() {
        assertEquals('3', VinCheckDigit.compute("1HGCM82633A004352"))
    }

    @Test
    fun `refuses vins of the wrong length`() {
        assertNull(VinCheckDigit.compute("1HGCM82633A00435"))
        assertNull(VinCheckDigit.compute(""))
    }

    @Test
    fun `refuses characters outside the transliteration table`() {
        assertNull(VinCheckDigit.compute("1HGCM82633A00435I"))
    }

    @Test
    fun `transliteration follows the ebcdic derived table`() {
        assertEquals(1, VinCheckDigit.valueOf('A'))
        assertEquals(1, VinCheckDigit.valueOf('J'))
        assertEquals(2, VinCheckDigit.valueOf('S'))
        assertEquals(9, VinCheckDigit.valueOf('R'))
        assertEquals(9, VinCheckDigit.valueOf('Z'))
        assertEquals(7, VinCheckDigit.valueOf('7'))
        assertNull(VinCheckDigit.valueOf('I'))
        assertNull(VinCheckDigit.valueOf('O'))
        assertNull(VinCheckDigit.valueOf('Q'))
    }

    @Test
    fun `mismatch is an error where the digit is mandatory`() {
        val result = VinCheckDigit.verify("SGZCZ43D13S812715", mandatory = true)

        assertFalse(result.matches)
        assertTrue(result.isError)
        assertFalse(result.isWarning)
        assertEquals('X', result.expected)
        assertEquals('1', result.actual)
    }

    @Test
    fun `mismatch is only a warning outside north america`() {
        // A genuine Porsche VIN that deliberately fails the North American rule.
        val result = VinCheckDigit.verify("WP0ZZZ99ZTS392124", mandatory = false)

        assertFalse(result.matches)
        assertFalse(result.isError)
        assertTrue(result.isWarning)
    }

    @Test
    fun `matching digit is neither error nor warning`() {
        val result = VinCheckDigit.verify("5GZCZ43D13S812715", mandatory = true)

        assertTrue(result.matches)
        assertFalse(result.isError)
        assertFalse(result.isWarning)
    }
}
