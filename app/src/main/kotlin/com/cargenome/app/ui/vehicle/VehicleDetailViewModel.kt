package com.cargenome.app.ui.vehicle

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.ui.navigation.VehicleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VehicleDetailUiState(
    val vehicle: VehicleEntity? = null,
    val currentOdometerKm: Double? = null,
    val nextMaintenanceEvent: MaintenanceEventEntity? = null,
    val isLoading: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val odometer: OdometerRepository,
    private val service: ServiceRepository,
    private val attachmentManager: AttachmentManager,
    private val settingsRepo: AppSettingsRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<VehicleDetailRoute>().vehicleId

    init {
        viewModelScope.launch {
            settingsRepo.setSelectedVehicleId(vehicleId)
        }
    }

    val state: StateFlow<VehicleDetailUiState> = vehicles.observe(vehicleId)
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(VehicleDetailUiState(isLoading = false))
            } else {
                combine(
                    odometer.observeCurrentKm(vehicle.id),
                    service.observeUpcomingEvents(vehicle.id, LocalDate.now().toEpochDay()),
                ) { km, upcomingEvents ->
                    VehicleDetailUiState(
                        vehicle = vehicle,
                        currentOdometerKm = km ?: vehicle.initialOdometerKm.takeIf { it > 0.0 },
                        nextMaintenanceEvent = upcomingEvents.firstOrNull(),
                        isLoading = false,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = VehicleDetailUiState(),
        )

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun delete() {
        viewModelScope.launch {
            if (settingsRepo.settings.first().selectedVehicleId == vehicleId) {
                settingsRepo.setSelectedVehicleId(null)
            }
            vehicles.delete(vehicleId)
            _deleted.value = true
        }
    }

    fun archive(archived: Boolean) {
        viewModelScope.launch { vehicles.setArchived(vehicleId, archived) }
    }

    fun attachInsurancePdf(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            val currentVehicle = state.value.vehicle ?: return@launch
            currentVehicle.insurancePdfUri?.let { oldUri ->
                attachmentManager.deleteAttachmentFile(oldUri)
            }
            val attachment = attachmentManager.savePdfAttachment(
                sourceUri = uri,
                ownerType = AttachmentOwner.Vehicle,
                ownerId = currentVehicle.id,
                displayName = displayName,
            )
            val updated = currentVehicle.copy(insurancePdfUri = attachment.uri)
            vehicles.update(updated)
        }
    }

    fun removeInsurancePdf() {
        viewModelScope.launch {
            val currentVehicle = state.value.vehicle ?: return@launch
            currentVehicle.insurancePdfUri?.let { oldUri ->
                attachmentManager.deleteAttachmentFile(oldUri)
            }
            val updated = currentVehicle.copy(insurancePdfUri = null)
            vehicles.update(updated)
        }
    }

    fun updateInsurance(
        provider: String?,
        policyNumber: String?,
        expiresOn: LocalDate?,
        clearPdf: Boolean = false,
    ) {
        viewModelScope.launch {
            val currentVehicle = state.value.vehicle ?: return@launch
            if (clearPdf) {
                currentVehicle.insurancePdfUri?.let { oldUri ->
                    attachmentManager.deleteAttachmentFile(oldUri)
                }
            }
            val updated = currentVehicle.copy(
                insuranceProvider = provider?.trim()?.ifBlank { null },
                insurancePolicyNumber = policyNumber?.trim()?.ifBlank { null },
                insuranceExpiresOn = expiresOn,
                insurancePdfUri = if (clearPdf) null else currentVehicle.insurancePdfUri,
            )
            vehicles.update(updated)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
