package com.cargenome.app.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.ExpenseRepository
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.analytics.VehicleAnalyticsCalculator
import com.cargenome.app.domain.analytics.VehicleAnalyticsData
import com.cargenome.app.ui.navigation.AnalyticsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import com.cargenome.app.domain.analytics.AnalyticsTimeRange

data class AnalyticsUiState(
    val vehicle: VehicleEntity? = null,
    val data: VehicleAnalyticsData = VehicleAnalyticsData(),
    val selectedTimeRange: AnalyticsTimeRange = AnalyticsTimeRange.ALL_TIME,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val service: ServiceRepository,
    private val expenses: ExpenseRepository,
    private val odometer: OdometerRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<AnalyticsRoute>().vehicleId

    private val selectedTimeRange = MutableStateFlow(AnalyticsTimeRange.ALL_TIME)

    val state: StateFlow<AnalyticsUiState> = vehicles.observe(vehicleId)
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(
                    AnalyticsUiState(
                        vehicle = null,
                        isLoading = false,
                    ),
                )
            } else {
                combine(
                    fuel.observe(vehicle.id),
                    service.observeRecords(vehicle.id),
                    expenses.observe(vehicle.id),
                    odometer.observeCurrentKm(vehicle.id),
                    selectedTimeRange,
                ) { fuels, services, exps, currentKm, timeRange ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val analyticsData = VehicleAnalyticsCalculator.calculate(
                            vehicle = vehicle,
                            fuelRecords = fuels,
                            serviceRecords = services,
                            expenses = exps,
                            currentOdometerKm = currentKm,
                            timeRange = timeRange,
                        )
                        AnalyticsUiState(
                            vehicle = vehicle,
                            data = analyticsData,
                            selectedTimeRange = timeRange,
                            isLoading = false,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AnalyticsUiState(),
        )

    fun setTimeRange(range: AnalyticsTimeRange) {
        selectedTimeRange.value = range
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
