package com.cargenome.app.data.vin

import com.cargenome.app.domain.model.FuelType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianVdsVinDecoderTest {

    private val decoder = RussianVdsVinDecoder(dispatcher = Dispatchers.Unconfined)

    @Test
    fun `decodes LADA Vesta correctly`() = runTest {
        // XTAGFL110... -> Vesta Sedan
        val vin = "XTAGFL110JY123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Lada", data.make)
        assertEquals("Vesta (Седан)", data.model)
        assertEquals(FuelType.Petrol, data.fuelType)
        assertEquals("1.6L 16V (106 л.с.)", data.engineSummary)
    }

    @Test
    fun `decodes LADA Granta correctly`() = runTest {
        // XTA2190... -> Granta
        val vin = "XTA219010E0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Lada", data.make)
        assertEquals("Granta (Седан)", data.model)
    }

    @Test
    fun `decodes Renault Duster correctly`() = runTest {
        val vin = "X7LHSRBC0E0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Renault", data.make)
        assertEquals("Duster", data.model)
    }

    @Test
    fun `decodes Kia Rio correctly`() = runTest {
        // Z94 + "FB" in VDS
        val vin = "Z94FB41DBER123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Kia", data.make)
        assertEquals("Rio", data.model)
    }

    @Test
    fun `decodes UAZ Patriot correctly`() = runTest {
        val vin = "XTT316300E0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("УАЗ", data.make)
        assertEquals("Patriot", data.model)
    }

    @Test
    fun `decodes Haval Jolion correctly`() = runTest {
        val vin = "X9WJOL110P0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Haval", data.make)
        assertEquals("Jolion", data.model)
        assertEquals(FuelType.Petrol, data.fuelType)
    }

    @Test
    fun `decodes BelGee X50 correctly`() = runTest {
        val vin = "Y39SX1100P0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("BelGee", data.make)
        assertEquals("X50 (Coolray)", data.model)
    }

    @Test
    fun `decodes Geely Monjaro correctly`() = runTest {
        val vin = "LB3KX1100P0123456"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Hit)
        val data = (result as OnlineVinLookup.Hit).data
        assertEquals("Geely", data.make)
        assertEquals("Monjaro", data.model)
        assertEquals("4WD", data.driveType)
    }

    @Test
    fun `returns Empty for unsupported foreign VIN`() = runTest {
        val vin = "WVWZZZ1JZ3W386752"
        val result = decoder.lookup(vin, Instant.now())

        assertTrue(result is OnlineVinLookup.Empty)
    }
}
