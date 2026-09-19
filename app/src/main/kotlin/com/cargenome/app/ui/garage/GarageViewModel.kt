package com.cargenome.app.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cargenome.app.data.db.dao.VehicleSummary
import com.cargenome.app.data.demo.DemoDataSeeder
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch

@Immutable
data class GarageUiState(
    val vehicles: List<VehicleSummary> = emptyList(),
    val selectedVehicleId: Long? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class GarageViewModel @Inject constructor(
    repository: VehicleRepository,
    private val demoSeeder: DemoDataSeeder,
    private val settingsRepo: AppSettingsRepository,
) : ViewModel() {

    val state: StateFlow<GarageUiState> = combine(
        repository.observeSummaries(),
        settingsRepo.settings,
    ) { summaries, settings ->
        GarageUiState(
            vehicles = summaries,
            selectedVehicleId = settings.selectedVehicleId,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = GarageUiState(),
    )

    fun seedDemoData(onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = demoSeeder.seedDemoVehicle()
            onComplete(id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
