package com.cargenome.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object GarageRoute

@Serializable
data class VehicleDetailRoute(val vehicleId: Long)

/**
 * Add or edit a car. A null [vehicleId] means a new one; [vin] prefills the
 * number when the user arrives from the standalone decoder.
 */
@Serializable
data class VehicleEditorRoute(val vehicleId: Long? = null, val vin: String? = null)

@Serializable
data object VinDecoderRoute

@Serializable
data class FuelLogRoute(val vehicleId: Long)

/** Add or edit a fill-up. A null [recordId] means a new one. */
@Serializable
data class FuelEditorRoute(val vehicleId: Long, val recordId: Long? = null)

@Serializable
data class ServiceLogRoute(val vehicleId: Long)

/** Add or edit a service record. [scheduleId] prefills the linked maintenance schedule item. */
@Serializable
data class ServiceEditorRoute(
    val vehicleId: Long,
    val recordId: Long? = null,
    val scheduleId: Long? = null,
)

/** Add or edit a maintenance schedule item. */
@Serializable
data class ScheduleEditorRoute(
    val vehicleId: Long,
    val scheduleId: Long? = null,
)

@Serializable
data class OdometerLogRoute(val vehicleId: Long)

@Serializable
data class AnalyticsRoute(val vehicleId: Long)

@Serializable
data class ExpenseLogRoute(val vehicleId: Long)

@Serializable
data class ExpenseEditorRoute(
    val vehicleId: Long,
    val expenseId: Long? = null,
)

@Serializable
data class SettingsRoute(val openPremium: Boolean = false)

@Serializable
data object VinScanRoute




