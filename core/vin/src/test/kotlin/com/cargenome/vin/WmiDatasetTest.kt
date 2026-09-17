package com.cargenome.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the dataset that ships in the APK, built by tools/generate_wmi_dataset.py.
 *
 * The point of these assertions is that a regeneration must not quietly drop
 * whole regions. Offline decoding is the only thing that works for post-Soviet
 * cars, so their codes are checked one by one.
 */
class WmiDatasetTest {

    private val registry: JsonWmiRegistry = JsonWmiRegistry.parse(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("wmi.json")) {
            "wmi.json is missing from app/src/main/assets"
        }.bufferedReader().use { it.readText() },
    )

    private val decoder = OfflineVinDecoder(registry) { 2026 }

    private fun nameFor(wmi: String): String? {
        val padded = wmi.padEnd(VinFormat.LENGTH, '1')
        val entry = registry.lookup(padded) ?: return null
        return entry.make ?: entry.manufacturer
    }

    @Test
    fun `the dataset is large enough to be the real one`() {
        assertTrue("only ${registry.size} entries", registry.size > 3_000)
    }

    @Test
    fun `covers post soviet manufacturers, which no online decoder returns`() {
        val expected = mapOf(
            "XTA" to "LADA",
            "XTT" to "УАЗ",
            "XTC" to "КамАЗ",
            "XTH" to "ГАЗ",
            "X96" to "ГАЗ",
            "X7L" to "Renault",
            "XW8" to "Volkswagen",
            "Z94" to "Hyundai",
            "Z8N" to "Nissan",
            "X9L" to "Chevrolet",
            "XTM" to "МАЗ",
            "XTE" to "ЗАЗ",
        )
        expected.forEach { (wmi, make) -> assertEquals(wmi, make, nameFor(wmi)) }
    }

    @Test
    fun `covers european manufacturers missing from the american registry`() {
        listOf("WVW", "WAU", "WBA", "WDD", "WP0", "TMB", "VSS", "VF1", "VF3", "ZFA", "ZAR",
               "YV1", "SAL", "SJN", "WMW", "TRU", "W0L", "ZFF", "UU1")
            .forEach { assertNotNull("$it is missing", nameFor(it)) }
    }

    @Test
    fun `covers japanese, korean, chinese and indian manufacturers`() {
        listOf("JHM", "JTD", "JN1", "JF1", "JMB", "KMH", "KNA", "LSG", "LVS", "MA1", "LFV")
            .forEach { assertNotNull("$it is missing", nameFor(it)) }
    }

    @Test
    fun `covers north american manufacturers`() {
        listOf("1HG", "1FA", "2T1", "3VW", "5YJ", "1G1")
            .forEach { assertNotNull("$it is missing", nameFor(it)) }
    }

    @Test
    fun `decodes a lada end to end`() {
        val decoded = (decoder.decode("XTA21140053912345") as VinDecodeResult.Decoded).value

        assertEquals("LADA", decoded.make)
        assertEquals("RU", decoded.country?.code)
        assertEquals(2005, decoded.bestGuessYear)
    }

    @Test
    fun `decodes a german car end to end`() {
        val decoded = (decoder.decode("WVWZZZ1JZ3W386752") as VinDecodeResult.Decoded).value

        assertEquals("Volkswagen", decoded.make)
        assertEquals("DE", decoded.country?.code)
        assertEquals(2003, decoded.bestGuessYear)
    }
}
