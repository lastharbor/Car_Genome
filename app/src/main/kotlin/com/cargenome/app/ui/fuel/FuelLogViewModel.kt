package com.cargenome.app.ui.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.fuel.FuelConsumption
import com.cargenome.app.domain.fuel.FuelSegment
import com.cargenome.app.domain.fuel.FuelStatistics
import com.cargenome.app.ui.navigation.FuelLogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable

@Immutable
data class FuelLogUiState(
    val vehicle: VehicleEntity? = null,
    /** Newest first, the order the log is read in. */
    val records: List<FuelRecordEntity> = emptyList(),
    val statistics: FuelStatistics = FuelStatistics(),
    val consumptionByRecord: Map<Long, FuelSegment> = emptyMap(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FuelLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val fuel: FuelRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<FuelLogRoute>().vehicleId

    val state: StateFlow<FuelLogUiState> = vehicles.observe(vehicleId)
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(
                    FuelLogUiState(
                        vehicle = null,
                        records = emptyList(),
                        statistics = FuelStatistics(),
                        consumptionByRecord = emptyMap(),
                        isLoading = false,
                    ),
                )
            } else {
                fuel.observe(vehicle.id).map { records ->
                    withContext(Dispatchers.Default) {
                        val statistics = FuelConsumption.analyse(records)
                        FuelLogUiState(
                            vehicle = vehicle,
                            records = records,
                            statistics = statistics,
                            consumptionByRecord = FuelConsumption.byClosingRecord(statistics),
                            isLoading = false,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = FuelLogUiState(),
        )

    fun delete(recordId: Long) {
        viewModelScope.launch { fuel.delete(recordId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
