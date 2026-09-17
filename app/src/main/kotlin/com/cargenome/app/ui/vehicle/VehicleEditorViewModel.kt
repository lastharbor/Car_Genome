package com.cargenome.app.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.data.vin.VinLookupState
import com.cargenome.app.data.vin.VinRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VehicleProfile
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.navigation.VehicleEditorRoute
import com.cargenome.vin.VinFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields a decode can fill in, tracked so a manual edit is never undone. */
enum class VehicleField {
    Make,
    Model,
    ModelYear,
    Trim,
    Engine,
    FuelType,
}

data class VehicleEditorUiState(
    val vehicleId: Long? = null,
    val isLoading: Boolean = false,

    val vin: String = "",
    val vinLookup: VinLookupState? = null,

    val make: String = "",
    val model: String = "",
    val modelYear: String = "",
    val trim: String = "",
    val engine: String = "",
    val fuelType: FuelType = FuelType.Petrol,

    val plateNumber: String = "",
    val nickname: String = "",
    val purchasedOn: LocalDate? = null,
    val initialOdometer: String = "",

    val insuranceProvider: String = "",
    val insurancePolicyNumber: String = "",
    val insuranceExpiresOn: LocalDate? = null,

    val distanceUnit: DistanceUnit = DistanceUnit.Kilometres,
    val volumeUnit: VolumeUnit = VolumeUnit.Litres,
    val currencyCode: String = "RUB",

    val editedByHand: Set<VehicleField> = emptySet(),
    val isSaving: Boolean = false,
    val savedVehicleId: Long? = null,
    val duplicateVin: Boolean = false,
) {
    val isEditing: Boolean get() = vehicleId != null

    /** A car needs a make to be worth listing; everything else can wait. */
    val canSave: Boolean get() = make.isNotBlank() && !isSaving

    /**
     * A blank make is only wrong once the owner has been there. An empty form
     * should look empty, not broken.
     */
    val showMakeError: Boolean get() = make.isBlank() && VehicleField.Make in editedByHand

    val vinIsComplete: Boolean get() = vin.length == VinFormat.LENGTH
}

@HiltViewModel
class VehicleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val vinRepository: VinRepository,
) : ViewModel() {

    private val route: VehicleEditorRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(
        VehicleEditorUiState(
            vehicleId = route.vehicleId,
            isLoading = route.vehicleId != null,
        ),
    )
    val state: StateFlow<VehicleEditorUiState> = _state.asStateFlow()

    private var decoding: Job? = null

    init {
        val id = route.vehicleId
        if (id != null) {
            viewModelScope.launch { load(id) }
        } else {
            route.vin?.let(::onVinChanged)
        }
    }

    private suspend fun load(id: Long) {
        val vehicle = vehicles.find(id)
        if (vehicle == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update {
            it.copy(
                isLoading = false,
                vin = vehicle.vin.orEmpty(),
                make = vehicle.make,
                model = vehicle.model,
                modelYear = vehicle.modelYear?.toString().orEmpty(),
                trim = vehicle.trim.orEmpty(),
                engine = vehicle.engine.orEmpty(),
                fuelType = vehicle.fuelType,
                plateNumber = vehicle.plateNumber.orEmpty(),
                nickname = vehicle.nickname.orEmpty(),
                purchasedOn = vehicle.purchasedOn,
                initialOdometer = vehicle.initialOdometerKm
                    .takeIf { km -> km > 0.0 }
                    ?.let { km -> formatOdometer(vehicle.distanceUnit.fromKilometres(km)) }
                    .orEmpty(),
                insuranceProvider = vehicle.insuranceProvider.orEmpty(),
                insurancePolicyNumber = vehicle.insurancePolicyNumber.orEmpty(),
                insuranceExpiresOn = vehicle.insuranceExpiresOn,
                distanceUnit = vehicle.distanceUnit,
                volumeUnit = vehicle.volumeUnit,
                currencyCode = vehicle.currencyCode,
                // A saved car's fields are the owner's, not the decoder's.
                editedByHand = VehicleField.entries.toSet(),
            )
        }
    }

    fun onVinChanged(raw: String) {
        val normalized = VinFormat.normalize(raw).take(VinFormat.LENGTH)
        if (normalized == _state.value.vin) return
        _state.update { it.copy(vin = normalized, vinLookup = null, duplicateVin = false) }

        decoding?.cancel()
        if (normalized.length < VinFormat.LENGTH) return
        decoding = viewModelScope.launch {
            vinRepository.lookup(normalized).collect { lookup ->
                _state.update { current ->
                    if (current.vin != normalized) return@update current
                    val withLookup = current.copy(vinLookup = lookup)
                    if (lookup is VinLookupState.Ready) {
                        withLookup.prefilledFrom(lookup.profile)
                    } else {
                        withLookup
                    }
                }
            }
        }
    }

    fun onMakeChanged(value: String) = edit(VehicleField.Make) { copy(make = value) }

    fun onModelChanged(value: String) = edit(VehicleField.Model) { copy(model = value) }

    fun onModelYearChanged(value: String) = edit(VehicleField.ModelYear) {
        copy(modelYear = value.filter(Char::isDigit).take(4))
    }

    fun onTrimChanged(value: String) = edit(VehicleField.Trim) { copy(trim = value) }

    fun onEngineChanged(value: String) = edit(VehicleField.Engine) { copy(engine = value) }

    fun onFuelTypeChanged(value: FuelType) = edit(VehicleField.FuelType) { copy(fuelType = value) }

    fun onPlateChanged(value: String) = _state.update { it.copy(plateNumber = value.uppercase()) }

    fun onNicknameChanged(value: String) = _state.update { it.copy(nickname = value) }

    fun onPurchaseDateChanged(value: LocalDate?) = _state.update { it.copy(purchasedOn = value) }

    fun onInitialOdometerChanged(value: String) = _state.update {
        it.copy(initialOdometer = value.filter { c -> c.isDigit() || c == '.' || c == ',' })
    }

    fun onInsuranceProviderChanged(value: String) = _state.update { it.copy(insuranceProvider = value) }

    fun onInsurancePolicyNumberChanged(value: String) = _state.update { it.copy(insurancePolicyNumber = value) }

    fun onInsuranceExpiryChanged(value: LocalDate?) = _state.update { it.copy(insuranceExpiresOn = value) }

    fun onDistanceUnitChanged(value: DistanceUnit) = _state.update { it.copy(distanceUnit = value) }

    fun onVolumeUnitChanged(value: VolumeUnit) = _state.update { it.copy(volumeUnit = value) }

    fun onCurrencyChanged(value: String) = _state.update {
        it.copy(currencyCode = value.uppercase().filter(Char::isLetter).take(3))
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true, duplicateVin = false) }

        viewModelScope.launch {
            val vin = current.vin.takeIf { it.length == VinFormat.LENGTH }
            val clash = vin?.let { vehicles.findByVin(it) }
            if (clash != null && clash.id != current.vehicleId) {
                _state.update { it.copy(isSaving = false, duplicateVin = true) }
                return@launch
            }

            val odometerKm = current.initialOdometer
                .replace(',', '.')
                .toDoubleOrNull()
                ?.let(current.distanceUnit::toKilometres)
                ?.coerceAtLeast(0.0)
                ?: 0.0

            val existing = current.vehicleId?.let { vehicles.find(it) }
            val entity = VehicleEntity(
                id = current.vehicleId ?: 0,
                vin = vin,
                make = current.make.trim(),
                model = current.model.trim(),
                modelYear = current.modelYear.toIntOrNull(),
                trim = current.trim.trim().ifBlank { null },
                engine = current.engine.trim().ifBlank { null },
                fuelType = current.fuelType,
                plateNumber = current.plateNumber.trim().ifBlank { null },
                nickname = current.nickname.trim().ifBlank { null },
                photoUri = existing?.photoUri,
                distanceUnit = current.distanceUnit,
                volumeUnit = current.volumeUnit,
                currencyCode = current.currencyCode.ifBlank { "RUB" },
                purchasedOn = current.purchasedOn,
                initialOdometerKm = odometerKm,
                insuranceProvider = current.insuranceProvider.trim().ifBlank { null },
                insurancePolicyNumber = current.insurancePolicyNumber.trim().ifBlank { null },
                insuranceExpiresOn = current.insuranceExpiresOn,
                insurancePdfUri = existing?.insurancePdfUri,
                vinDecodeJson = existing?.vinDecodeJson,
                createdAt = existing?.createdAt ?: Instant.now(),
                isArchived = existing?.isArchived ?: false,
            )

            val id = if (existing != null) {
                vehicles.update(entity)
                entity.id
            } else {
                vehicles.add(entity)
            }
            _state.update { it.copy(isSaving = false, savedVehicleId = id) }
        }
    }

    private fun edit(field: VehicleField, change: VehicleEditorUiState.() -> VehicleEditorUiState) {
        _state.update { it.change().copy(editedByHand = it.editedByHand + field) }
    }

    private fun formatOdometer(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

/**
 * Fills in what the VIN revealed, skipping anything the owner has already
 * typed. Decoding runs again on every VIN change, and a correction made by hand
 * has to survive that.
 */
private fun VehicleEditorUiState.prefilledFrom(profile: VehicleProfile): VehicleEditorUiState {
    fun keep(field: VehicleField, current: String, incoming: String?): String =
        if (field in editedByHand || incoming.isNullOrBlank()) current else incoming

    return copy(
        make = keep(VehicleField.Make, make, profile.make),
        model = keep(VehicleField.Model, model, profile.model),
        modelYear = keep(VehicleField.ModelYear, modelYear, profile.modelYear?.toString()),
        trim = keep(VehicleField.Trim, trim, profile.trim ?: profile.series),
        engine = keep(VehicleField.Engine, engine, profile.engine),
        fuelType = if (VehicleField.FuelType in editedByHand) fuelType else profile.fuelType ?: fuelType,
    )
}
