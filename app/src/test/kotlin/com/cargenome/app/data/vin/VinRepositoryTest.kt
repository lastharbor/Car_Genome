package com.cargenome.app.data.vin

import com.cargenome.app.domain.model.FuelType
import com.cargenome.vin.JsonWmiRegistry
import com.cargenome.vin.OfflineVinDecoder
import com.cargenome.vin.VinProblem
import com.cargenome.vin.VinRegion
import com.cargenome.vin.WmiEntry
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge rules, checked without a server: a stubbed online decoder is enough
 * to say what should survive an online answer and what should not.
 */
class VinRepositoryTest {

    private val registry = JsonWmiRegistry.of(
        listOf(
            WmiEntry(code = "WVW", manufacturer = "Volkswagen AG", make = "Volkswagen", country = "DE"),
            WmiEntry(code = "XTA", manufacturer = "AvtoVAZ", make = "LADA", country = "RU"),
        ),
    )

    private val offline = VinDecodingRepository(
        decoder = OfflineVinDecoder(registry) { 2025 },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun repository(lookup: OnlineVinLookup) = VinRepository(
        offline = offline,
        decoders = setOf(FakeOnlineDecoder(lookup)),
    )

    private val golf = ExternalVehicleData(
        make = "VOLKSWAGEN",
        model = "Golf",
        modelYear = 2003,
        trim = "GLS",
        displacementLitres = 2.0,
        cylinders = 4,
        fuelType = FuelType.Petrol,
        fuelTypeLabel = "Gasoline",
        engineSummary = "2.0 L 4-cyl",
    )

    @Test
    fun `a malformed VIN never reaches the network`() = runTest {
        val states = repository(OnlineVinLookup.Hit(golf, fromCache = false)).lookup("WVWZZZ1JZ").toList()

        val invalid = states.single() as VinLookupState.Invalid
        assertTrue(invalid.problems.any { it is VinProblem.WrongLength })
    }

    @Test
    fun `the offline reading arrives first and the enriched one replaces it`() = runTest {
        val states = repository(OnlineVinLookup.Hit(golf, fromCache = false))
            .lookup("WVWZZZ1JZ3W386752")
            .toList()

        assertEquals(2, states.size)
        val first = states.first() as VinLookupState.Ready
        assertEquals(VinSource.Offline, first.source)
        assertTrue(first.isEnriching)
        assertNull(first.profile.model)

        val second = states.last() as VinLookupState.Ready
        assertEquals(VinSource.Network, second.source)
        assertFalse(second.isEnriching)
        assertEquals("Golf", second.profile.model)
    }

    @Test
    fun `asking for offline only skips the lookup entirely`() = runTest {
        val states = repository(OnlineVinLookup.Hit(golf, fromCache = false))
            .lookup("WVWZZZ1JZ3W386752", allowNetwork = false)
            .toList()

        val only = states.single() as VinLookupState.Ready
        assertEquals(VinSource.Offline, only.source)
        assertEquals("Volkswagen", only.profile.make)
        assertNull(only.profile.model)
    }

    @Test
    fun `a vPIC year settles the thirty-year ambiguity`() = runTest {
        // Position 10 is A, which is both 1980 and 2010, and a European VIN has
        // nothing else in it to say which.
        val ambiguousVin = "WVWZZZ1JZAW386752"

        val offlineOnly = repository(OnlineVinLookup.Offline)
            .lookupOnce(ambiguousVin) as VinLookupState.Ready
        assertTrue(offlineOnly.profile.modelYearIsAmbiguous)
        assertEquals(2010, offlineOnly.profile.modelYear)
        assertEquals(1980, offlineOnly.profile.alternativeModelYear)

        val enriched = repository(OnlineVinLookup.Hit(golf.copy(modelYear = 1980), fromCache = false))
            .lookupOnce(ambiguousVin) as VinLookupState.Ready

        assertEquals(1980, enriched.profile.modelYear)
        assertFalse(enriched.profile.modelYearIsAmbiguous)
        assertNull(enriched.profile.alternativeModelYear)
    }

    @Test
    fun `a sparse vPIC answer cannot wipe out what the WMI table knew`() = runTest {
        val sparse = ExternalVehicleData(model = "Golf")

        val ready = repository(OnlineVinLookup.Hit(sparse, fromCache = false))
            .lookupOnce("WVWZZZ1JZ3W386752") as VinLookupState.Ready

        assertEquals("Volkswagen", ready.profile.make)
        assertEquals("Volkswagen AG", ready.profile.manufacturer)
        assertEquals("DE", ready.profile.country?.code)
        assertEquals("Golf", ready.profile.model)
    }

    @Test
    fun `a car vPIC has never heard of keeps its offline reading`() = runTest {
        val ready = repository(OnlineVinLookup.Empty)
            .lookupOnce("XTA21099062546789") as VinLookupState.Ready

        assertEquals(VinSource.Offline, ready.source)
        assertEquals("LADA", ready.profile.make)
        assertEquals("RU", ready.profile.country?.code)
        assertEquals(VinRegion.Europe, ready.profile.region)
        assertNull(ready.onlineFailure)
    }

    @Test
    fun `a failed lookup is reported alongside the offline reading, not instead of it`() = runTest {
        val boom = IOException("connection reset")

        val ready = repository(OnlineVinLookup.Failed(boom))
            .lookupOnce("WVWZZZ1JZ3W386752") as VinLookupState.Ready

        assertEquals(VinSource.Offline, ready.source)
        assertEquals("Volkswagen", ready.profile.make)
        assertEquals(boom, ready.onlineFailure)
    }

    @Test
    fun `a cached answer is marked as such`() = runTest {
        val ready = repository(OnlineVinLookup.Hit(golf, fromCache = true))
            .lookupOnce("WVWZZZ1JZ3W386752") as VinLookupState.Ready

        assertEquals(VinSource.Cached, ready.source)
        assertEquals(FuelType.Petrol, ready.profile.fuelType)
        assertEquals("2.0 L 4-cyl", ready.profile.engine)
    }

    private class FakeOnlineDecoder(private val result: OnlineVinLookup) : OnlineVinDecoder {
        override suspend fun lookup(vin: String, now: Instant): OnlineVinLookup = result

        override suspend fun modelsFor(make: String, year: Int): List<String> = emptyList()
    }
}
