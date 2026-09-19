package com.cargenome.app.ui.odometer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.ui.navigation.OdometerLogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch

@Immutable
data class OdometerLogUiState(
    val vehicle: VehicleEntity? = null,
    val readings: List<OdometerReadingEntity> = emptyList(),
    val chronologicalReadings: List<OdometerReadingEntity> = emptyList(),
    val currentKm: Double? = null,
    val isAddDialogOpen: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class OdometerLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vehicles: VehicleRepository,
    private val odometer: OdometerRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<OdometerLogRoute>().vehicleId

    private val _isAddDialogOpen = MutableStateFlow(false)

    val state: StateFlow<OdometerLogUiState> = combine(
        vehicles.observe(vehicleId),
        odometer.observe(vehicleId),
        odometer.observeChronological(vehicleId),
        odometer.observeCurrentKm(vehicleId),
        _isAddDialogOpen,
    ) { vehicle, readings, chronological, currentKm, dialogOpen ->
        OdometerLogUiState(
            vehicle = vehicle,
            readings = readings,
            chronologicalReadings = chronological,
            currentKm = currentKm ?: vehicle?.initialOdometerKm?.takeIf { it > 0.0 },
            isAddDialogOpen = dialogOpen,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = OdometerLogUiState(),
    )

    fun openAddDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
    }

    fun addManualReading(date: LocalDate, distanceValue: Double, note: String?) {
        val vehicle = state.value.vehicle ?: return
        viewModelScope.launch {
            val km = vehicle.distanceUnit.toKilometres(distanceValue.coerceAtLeast(0.0))
            val instant = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
            odometer.add(
                OdometerReadingEntity(
                    vehicleId = vehicle.id,
                    recordedAt = instant,
                    odometerKm = km,
                    source = OdometerSource.Manual,
                    note = note?.trim()?.ifBlank { null },
                ),
            )
            _isAddDialogOpen.value = false
        }
    }

    fun deleteReading(reading: OdometerReadingEntity) {
        if (reading.source != OdometerSource.Manual) return
        viewModelScope.launch {
            odometer.delete(reading)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
