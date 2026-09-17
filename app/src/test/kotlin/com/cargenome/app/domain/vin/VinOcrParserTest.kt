package com.cargenome.app.domain.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinOcrParserTest {

    @Test
    fun `extracts plain vin from clean text`() {
        val text = "Vehicle identification number: 1HGCM82633A004352"
        val vins = VinOcrParser.parseVins(text)
        assertEquals(listOf("1HGCM82633A004352"), vins)
    }

    @Test
    fun `extracts vin with asterisks and spaces`() {
        val text = "* WVW ZZZ 1JZ 3W 386752 *"
        val vins = VinOcrParser.parseVins(text)
        assertTrue(vins.contains("WVWZZZ1JZ3W386752"))
    }

    @Test
    fun `extracts russian vin with colons and hyphens`() {
        val text = "VIN: XTA-21140053912345\nMODEL: 2114"
        val vins = VinOcrParser.parseVins(text)
        assertEquals(listOf("XTA21140053912345"), vins)
    }

    @Test
    fun `corrects O to 0 in OCR recognition`() {
        // Here 1HGCM82633AOO4352 contains letter O instead of 0
        val text = "1HGCM82633AOO4352"
        val vins = VinOcrParser.parseVins(text)
        assertEquals(listOf("1HGCM82633A004352"), vins)
    }

    @Test
    fun `ignores random text that is not a vin`() {
        val text = "Hello world this is a random document with no vin numbers whatsoever"
        val vins = VinOcrParser.parseVins(text)
        assertTrue(vins.isEmpty())
    }

    @Test
    fun `prioritizes valid check digit vin when multiple candidates present`() {
        val text = "Candidates: WVWZZZ1JZ3W386752 and 1HGCM82633A004352"
        val vins = VinOcrParser.parseVins(text)
        assertTrue(vins.size >= 2)
        // 1HGCM82633A004352 has valid check digit (3 at position 9)
        assertEquals("1HGCM82633A004352", vins.first())
    }
}
