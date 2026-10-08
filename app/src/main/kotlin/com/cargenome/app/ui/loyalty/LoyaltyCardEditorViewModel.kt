package com.cargenome.app.ui.loyalty

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cargenome.app.data.db.entity.BarcodeType
import com.cargenome.app.data.db.entity.LoyaltyCardEntity
import com.cargenome.app.data.db.entity.LoyaltyCategory
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.LoyaltyCardRepository
import com.cargenome.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val PRESET_CARD_COLORS = listOf(
    0xFF1976D2, // Blue
    0xFFD32F2F, // Red
    0xFF388E3C, // Green
    0xFFF57C00, // Orange
    0xFF7B1FA2, // Purple
    0xFF00796B, // Teal
    0xFF455A64, // Blue Grey
    0xFF212121, // Dark
)

data class LoyaltyCardEditorUiState(
    val id: Long = 0,
    val title: String = "",
    val cardNumber: String = "",
    val barcodeType: BarcodeType = BarcodeType.Code128,
    val category: LoyaltyCategory = LoyaltyCategory.Fuel,
    val colorHex: Long = 0xFF1976D2,
    val note: String = "",
    val vehicleId: Long? = null,
    val vehicles: List<VehicleEntity> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val numberError: Boolean = false,
)

@HiltViewModel
class LoyaltyCardEditorViewModel @Inject constructor(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val vehicleRepository: VehicleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cardId: Long? = savedStateHandle.get<Long>("cardId")?.takeIf { it > 0 }
    private val initialVehicleId: Long? = savedStateHandle.get<Long>("initialVehicleId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(
        LoyaltyCardEditorUiState(
            id = cardId ?: 0,
            vehicleId = initialVehicleId,
            isEditing = cardId != null,
        )
    )
    val uiState: StateFlow<LoyaltyCardEditorUiState> = _uiState.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    init {
        viewModelScope.launch {
            vehicleRepository.observeActive().collect { activeVehicles ->
                _uiState.value = _uiState.value.copy(vehicles = activeVehicles)
            }
        }

        if (cardId != null && cardId > 0) {
            viewModelScope.launch {
                val existing = loyaltyCardRepository.getById(cardId)
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(
                        id = existing.id,
                        title = existing.title,
                        cardNumber = existing.cardNumber,
                        barcodeType = existing.barcodeType,
                        category = existing.category,
                        colorHex = existing.colorHex,
                        note = existing.note.orEmpty(),
                        vehicleId = existing.vehicleId,
                    )
                }
            }
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title, titleError = false)
    }

    fun onCardNumberChanged(cardNumber: String) {
        _uiState.value = _uiState.value.copy(cardNumber = cardNumber, numberError = false)
    }

    fun onCategoryChanged(category: LoyaltyCategory) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onBarcodeTypeChanged(barcodeType: BarcodeType) {
        _uiState.value = _uiState.value.copy(barcodeType = barcodeType)
    }

    fun onColorChanged(colorHex: Long) {
        _uiState.value = _uiState.value.copy(colorHex = colorHex)
    }

    fun onNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun onVehicleIdChanged(vehicleId: Long?) {
        _uiState.value = _uiState.value.copy(vehicleId = vehicleId)
    }

    fun onBarcodeScanned(rawValue: String, detectedType: BarcodeType) {
        _uiState.value = _uiState.value.copy(
            cardNumber = rawValue,
            barcodeType = detectedType,
            numberError = false,
        )
    }

    fun save() {
        val current = _uiState.value
        val hasTitleError = current.title.isBlank()
        val hasNumberError = current.cardNumber.isBlank()

        if (hasTitleError || hasNumberError) {
            _uiState.value = current.copy(
                titleError = hasTitleError,
                numberError = hasNumberError,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true)
            val entity = LoyaltyCardEntity(
                id = current.id,
                title = current.title.trim(),
                cardNumber = current.cardNumber.trim(),
                barcodeType = current.barcodeType,
                barcodeRawValue = current.cardNumber.trim(),
                category = current.category,
                colorHex = current.colorHex,
                note = current.note.trim().takeIf { it.isNotBlank() },
                vehicleId = current.vehicleId,
                updatedAt = Instant.now(),
            )
            if (current.isEditing) {
                loyaltyCardRepository.update(entity)
            } else {
                loyaltyCardRepository.add(entity)
            }
            _saveSuccess.emit(Unit)
        }
    }
}
