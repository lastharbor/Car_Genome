package com.cargenome.app.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSmokeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `garage route serializes and deserializes`() {
        val encoded = json.encodeToString(GarageRoute)
        val decoded = json.decodeFromString<GarageRoute>(encoded)
        assertEquals(GarageRoute, decoded)
    }

    @Test
    fun `vehicle detail route preserves vehicle id`() {
        val route = VehicleDetailRoute(vehicleId = 123L)
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<VehicleDetailRoute>(encoded)
        assertEquals(123L, decoded.vehicleId)
    }

    @Test
    fun `vehicle editor route preserves optional parameters`() {
        val newCarRoute = VehicleEditorRoute()
        assertNull(newCarRoute.vehicleId)
        assertNull(newCarRoute.vin)

        val editCarRoute = VehicleEditorRoute(vehicleId = 10L, vin = "WVWZZZ1JZ3W386752")
        val encoded = json.encodeToString(editCarRoute)
        val decoded = json.decodeFromString<VehicleEditorRoute>(encoded)
        assertEquals(10L, decoded.vehicleId)
        assertEquals("WVWZZZ1JZ3W386752", decoded.vin)
    }

    @Test
    fun `fuel routes serialize correctly`() {
        val logRoute = FuelLogRoute(vehicleId = 5L)
        assertEquals(5L, json.decodeFromString<FuelLogRoute>(json.encodeToString(logRoute)).vehicleId)

        val editorRoute = FuelEditorRoute(vehicleId = 5L, recordId = 99L)
        val decodedEditor = json.decodeFromString<FuelEditorRoute>(json.encodeToString(editorRoute))
        assertEquals(5L, decodedEditor.vehicleId)
        assertEquals(99L, decodedEditor.recordId)
    }

    @Test
    fun `service and schedule routes serialize correctly`() {
        val logRoute = ServiceLogRoute(vehicleId = 7L)
        assertEquals(7L, json.decodeFromString<ServiceLogRoute>(json.encodeToString(logRoute)).vehicleId)

        val serviceEdit = ServiceEditorRoute(vehicleId = 7L, recordId = 11L, scheduleId = 22L)
        val decodedService = json.decodeFromString<ServiceEditorRoute>(json.encodeToString(serviceEdit))
        assertEquals(7L, decodedService.vehicleId)
        assertEquals(11L, decodedService.recordId)
        assertEquals(22L, decodedService.scheduleId)

        val scheduleEdit = ScheduleEditorRoute(vehicleId = 7L, scheduleId = 33L)
        val decodedSchedule = json.decodeFromString<ScheduleEditorRoute>(json.encodeToString(scheduleEdit))
        assertEquals(7L, decodedSchedule.vehicleId)
        assertEquals(33L, decodedSchedule.scheduleId)
    }

    @Test
    fun `odometer and analytics routes serialize correctly`() {
        val analytics = AnalyticsRoute(vehicleId = 9L)
        assertEquals(9L, json.decodeFromString<AnalyticsRoute>(json.encodeToString(analytics)).vehicleId)

        val analyticsRoute = AnalyticsRoute(vehicleId = 15L)
        assertEquals(15L, json.decodeFromString<AnalyticsRoute>(json.encodeToString(analyticsRoute)).vehicleId)
    }

    @Test
    fun `expense routes serialize correctly`() {
        val logRoute = ExpenseLogRoute(vehicleId = 20L)
        assertEquals(20L, json.decodeFromString<ExpenseLogRoute>(json.encodeToString(logRoute)).vehicleId)

        val editRoute = ExpenseEditorRoute(vehicleId = 20L, expenseId = 45L)
        val decodedEdit = json.decodeFromString<ExpenseEditorRoute>(json.encodeToString(editRoute))
        assertEquals(20L, decodedEdit.vehicleId)
        assertEquals(45L, decodedEdit.expenseId)
    }

    @Test
    fun `utility screen routes serialize correctly`() {
        val vinDecoder = json.decodeFromString<VinDecoderRoute>(json.encodeToString(VinDecoderRoute))
        assertEquals(VinDecoderRoute, vinDecoder)

        val settings = json.decodeFromString<SettingsRoute>(json.encodeToString(SettingsRoute()))
        assertEquals(SettingsRoute(), settings)

        val settingsPremium = json.decodeFromString<SettingsRoute>(json.encodeToString(SettingsRoute(openPremium = true)))
        assertTrue(settingsPremium.openPremium)

        val vinScan = json.decodeFromString<VinScanRoute>(json.encodeToString(VinScanRoute))
        assertEquals(VinScanRoute, vinScan)
    }
}
