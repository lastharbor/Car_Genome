package com.cargenome.app.data.vin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.network.NetworkMonitor
import com.cargenome.app.data.network.NhtsaVpicApi
import com.cargenome.app.data.repository.VinCacheRepository
import com.cargenome.app.domain.model.FuelType
import java.net.HttpURLConnection
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NhtsaVinDecoderTest {

    private lateinit var server: MockWebServer
    private lateinit var database: CarGenomeDatabase
    private lateinit var cache: VinCacheRepository
    private lateinit var api: NhtsaVpicApi

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val now: Instant = Instant.parse("2025-06-01T12:00:00Z")
    private var online = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarGenomeDatabase::class.java,
        ).build()
        cache = VinCacheRepository(database.vinCacheDao())

        api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NhtsaVpicApi::class.java)
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private fun decoder() = NhtsaVinDecoder(
        api = api,
        cache = cache,
        network = NetworkMonitor { online },
        json = json,
        dispatcher = Dispatchers.Unconfined,
    )

    private fun enqueueJson(body: String, code: Int = HttpURLConnection.HTTP_OK) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private val golfResponse = """
        {
          "Count": 1,
          "Message": "Results returned successfully",
          "Results": [
            {
              "Make": "VOLKSWAGEN",
              "Model": "Golf",
              "ModelYear": "2003",
              "Trim": "GLS",
              "Manufacturer": "VOLKSWAGEN AG",
              "BodyClass": "Hatchback/Liftback/Notchback",
              "VehicleType": "PASSENGER CAR",
              "DisplacementL": "2.0",
              "EngineCylinders": "4",
              "EngineHP": "115.0",
              "FuelTypePrimary": "Gasoline",
              "TransmissionStyle": "Manual",
              "TransmissionSpeeds": "5",
              "DriveType": "FWD",
              "Doors": "3",
              "PlantCity": "WOLFSBURG",
              "PlantCountry": "GERMANY",
              "ErrorCode": "0",
              "ErrorText": "0 - VIN decoded clean. Check Digit (9th position) is correct",
              "ABS": "",
              "AirBagLocCurtain": null
            }
          ]
        }
    """.trimIndent()

    private val emptyResponse = """
        {
          "Count": 1,
          "Message": "Results returned successfully",
          "Results": [
            { "Make": "", "Model": "", "Manufacturer": null, "ErrorCode": "11" }
          ]
        }
    """.trimIndent()

    @Test
    fun `a vPIC answer is read into the fields the app uses`() = runTest {
        enqueueJson(golfResponse)

        val lookup = decoder().lookup("WVWZZZ1JZ3W386752", now)

        val hit = lookup as OnlineVinLookup.Hit
        assertEquals("VOLKSWAGEN", hit.data.make)
        assertEquals("Golf", hit.data.model)
        assertEquals(2003, hit.data.modelYear)
        assertEquals(2.0, hit.data.displacementLitres!!, 0.001)
        assertEquals(115, hit.data.horsepower)
        assertEquals(FuelType.Petrol, hit.data.fuelType)
        assertEquals("Manual 5-speed", hit.data.transmission)
        assertEquals("WOLFSBURG, GERMANY", hit.data.plant)
        assertEquals("2.0 L 4-cyl 115 hp", hit.data.engineSummary)
    }

    @Test
    fun `blank vPIC fields are dropped rather than stored as empty strings`() = runTest {
        enqueueJson(golfResponse)

        val hit = decoder().lookup("WVWZZZ1JZ3W386752", now) as OnlineVinLookup.Hit

        assertNull(hit.data.series)
    }

    @Test
    fun `a second lookup of the same VIN is served from the database`() = runTest {
        enqueueJson(golfResponse)
        val decoder = decoder()

        val first = decoder.lookup("WVWZZZ1JZ3W386752", now) as OnlineVinLookup.Hit
        val second = decoder.lookup("wvwzzz1jz3w386752", now.plus(Duration.ofDays(30))) as OnlineVinLookup.Hit

        assertEquals(false, first.fromCache)
        assertEquals(true, second.fromCache)
        assertEquals("Golf", second.data.model)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a VIN vPIC knows nothing about is remembered as empty`() = runTest {
        enqueueJson(emptyResponse)

        val lookup = decoder().lookup("XTA21099062546789", now)

        assertEquals(OnlineVinLookup.Empty, lookup)
        assertEquals(1, cache.size())
    }

    @Test
    fun `an empty answer is asked again after a week`() = runTest {
        enqueueJson(emptyResponse)
        enqueueJson(golfResponse)
        val decoder = decoder()

        decoder.lookup("XTA21099062546789", now)
        val retried = decoder.lookup("XTA21099062546789", now.plus(Duration.ofDays(8)))

        assertTrue(retried is OnlineVinLookup.Hit)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an empty answer is not asked again the next day`() = runTest {
        enqueueJson(emptyResponse)
        val decoder = decoder()

        decoder.lookup("XTA21099062546789", now)
        val retried = decoder.lookup("XTA21099062546789", now.plus(Duration.ofDays(1)))

        assertEquals(OnlineVinLookup.Empty, retried)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `no connection means no request`() = runTest {
        online = false

        val lookup = decoder().lookup("WVWZZZ1JZ3W386752", now)

        assertEquals(OnlineVinLookup.Offline, lookup)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a server error is reported without being cached`() = runTest {
        enqueueJson("""{"error":"boom"}""", code = HttpURLConnection.HTTP_INTERNAL_ERROR)

        val lookup = decoder().lookup("WVWZZZ1JZ3W386752", now)

        assertTrue("expected a failure, got $lookup", lookup is OnlineVinLookup.Failed)
        assertEquals(0, cache.size())
    }

    @Test
    fun `a truncated response is reported without being cached`() = runTest {
        enqueueJson("""{"Count": 1, "Results": [{"Make":""".trimIndent())

        val lookup = decoder().lookup("WVWZZZ1JZ3W386752", now)

        assertTrue("expected a failure, got $lookup", lookup is OnlineVinLookup.Failed)
        assertEquals(0, cache.size())
    }

    @Test
    fun `the model list is sorted and free of duplicates`() = runTest {
        enqueueJson(
            """
            {
              "Count": 4,
              "Results": [
                {"Make_ID": 482, "Make_Name": "VOLKSWAGEN", "Model_ID": 1, "Model_Name": "Passat"},
                {"Make_ID": 482, "Make_Name": "VOLKSWAGEN", "Model_ID": 2, "Model_Name": "Golf"},
                {"Make_ID": 482, "Make_Name": "VOLKSWAGEN", "Model_ID": 3, "Model_Name": "Golf"},
                {"Make_ID": 482, "Make_Name": "VOLKSWAGEN", "Model_ID": 4, "Model_Name": ""}
              ]
            }
            """.trimIndent(),
        )

        val models = decoder().modelsFor("Volkswagen", 2003)

        assertEquals(listOf("Golf", "Passat"), models)
    }

    @Test
    fun `the model list is empty offline instead of throwing`() = runTest {
        online = false

        assertEquals(emptyList<String>(), decoder().modelsFor("Volkswagen", 2003))
        assertEquals(0, server.requestCount)
    }
}
