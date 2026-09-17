package com.cargenome.app.ui.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.core.net.toUri
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.ocr.ReceiptOcrScanner
import com.cargenome.app.data.ocr.ReceiptScanResult
import com.cargenome.app.data.repository.AttachmentRepository
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.navigation.FuelEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Volume, unit price and total: any two of them give the third. */
enum class CostField {
    Volume,
    UnitPrice,
    Total,
}

data class FuelEditorUiState(
    val recordId: Long? = null,
    val isLoading: Boolean = true,
    val vehicle: VehicleEntity? = null,

    val date: LocalDate = LocalDate.now(),
    val odometer: String = "",
    val volume: String = "",
    val unitPrice: String = "",
    val total: String = "",
    val station: String = "",
    val isFullTank: Boolean = true,
    val missedPreviousFillUp: Boolean = false,
    val notes: String = "",
    val attachmentUris: List<String> = emptyList(),

    /** Most recently edited first, so the third value knows which two to follow. */
    val costEdits: List<CostField> = emptyList(),

    val lastOdometerKm: Double? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = recordId != null

    val distanceUnit: DistanceUnit get() = vehicle?.distanceUnit ?: DistanceUnit.Kilometres

    val volumeUnit: VolumeUnit get() = vehicle?.volumeUnit ?: VolumeUnit.Litres

    val currencyCode: String get() = vehicle?.currencyCode ?: "RUB"

    val odometerValue: Double? get() = odometer.toDecimalOrNull()

    val volumeValue: Double? get() = volume.toDecimalOrNull()

    val canSave: Boolean
        get() = !isSaving && (odometerValue ?: -1.0) >= 0.0 && (volumeValue ?: 0.0) > 0.0

    /**
     * A mileage below the highest reading already recorded is usually a typo,
     * but it can also be a corrected entry, so it warns rather than blocks.
     */
    val odometerGoesBackwards: Boolean
        get() {
            val entered = odometerValue ?: return false
            val last = lastOdometerKm ?: return false
            return distanceUnit.toKilometres(entered) < last
        }
}

@HiltViewModel
class FuelEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val odometer: OdometerRepository,
    val attachmentManager: AttachmentManager,
    val ocrScanner: ReceiptOcrScanner,
    private val attachments: AttachmentRepository,
) : ViewModel() {

    private val route: FuelEditorRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(FuelEditorUiState(recordId = route.recordId))
    val state: StateFlow<FuelEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.find(route.vehicleId)
            val record = route.recordId?.let { fuel.find(it) }
            val existingAttachments = route.recordId?.let {
                attachments.observe(AttachmentOwner.FuelRecord, it).firstOrNull()
            } ?: emptyList()
            val lastKm = odometer.currentKm(route.vehicleId)
            val zone = ZoneId.systemDefault()

            _state.update { current ->
                if (record == null || vehicle == null) {
                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        lastOdometerKm = lastKm,
                        attachmentUris = existingAttachments.map { it.uri },
                    )
                } else {
                    val volume = vehicle.volumeUnit.fromLitres(record.volumeLitres)
                    val scale = Format.minorScale(vehicle.currencyCode)
                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        lastOdometerKm = lastKm,
                        date = record.filledAt.atZone(zone).toLocalDate(),
                        odometer = vehicle.distanceUnit.fromKilometres(record.odometerKm).trimmed(),
                        volume = volume.trimmed(),
                        unitPrice = (record.totalCostMinor / scale / volume).trimmed(),
                        total = (record.totalCostMinor / scale).trimmed(),
                        station = record.station.orEmpty(),
                        isFullTank = record.isFullTank,
                        missedPreviousFillUp = record.missedPreviousFillUp,
                        notes = record.notes.orEmpty(),
                        attachmentUris = existingAttachments.map { it.uri },
                        costEdits = listOf(CostField.Volume, CostField.Total),
                    )
                }
            }
        }
    }

    fun onDateChanged(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onOdometerChanged(value: String) = _state.update { it.copy(odometer = value.asDecimalInput()) }

    fun onVolumeChanged(value: String) = _state.update {
        it.copy(volume = value.asDecimalInput()).recalculated(CostField.Volume)
    }

    fun addVolume(delta: Double) {
        val current = _state.value.volume.toDecimalOrNull() ?: 0.0
        val newVol = (current + delta).coerceAtLeast(0.0)
        onVolumeChanged(newVol.trimmed())
    }

    fun onUnitPriceChanged(value: String) = _state.update {
        it.copy(unitPrice = value.asDecimalInput()).recalculated(CostField.UnitPrice)
    }

    fun onTotalChanged(value: String) = _state.update {
        it.copy(total = value.asDecimalInput()).recalculated(CostField.Total)
    }

    fun onStationChanged(value: String) = _state.update { it.copy(station = value) }

    fun onFullTankChanged(value: Boolean) = _state.update { it.copy(isFullTank = value) }

    fun onMissedFillUpChanged(value: Boolean) = _state.update {
        it.copy(missedPreviousFillUp = value)
    }

    fun onNotesChanged(value: String) = _state.update { it.copy(notes = value) }

    fun onAddAttachment(uri: String) = _state.update {
        it.copy(attachmentUris = it.attachmentUris + uri)
    }

    fun onRemoveAttachment(uri: String) = _state.update {
        it.copy(attachmentUris = it.attachmentUris - uri)
    }

    fun applyOcrResult(ocr: ReceiptScanResult) {
        _state.update { current ->
            var updated = current
            if (ocr.date != null) {
                updated = updated.copy(date = ocr.date)
            }
            if (ocr.station != null) {
                updated = updated.copy(station = ocr.station)
            }
            if (ocr.volumeLitres != null) {
                updated = updated.copy(volume = ocr.volumeLitres.toString()).recalculated(CostField.Volume)
            }
            if (ocr.totalCost != null) {
                updated = updated.copy(total = ocr.totalCost.toString()).recalculated(CostField.Total)
            }
            if (ocr.unitPrice != null) {
                updated = updated.copy(unitPrice = ocr.unitPrice.toString()).recalculated(CostField.UnitPrice)
            }
            updated
        }
    }

    fun save() {
        val current = _state.value
        val vehicle = current.vehicle ?: return
        val odometerValue = current.odometerValue ?: return
        val volumeValue = current.volumeValue ?: return
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val scale = Format.minorScale(vehicle.currencyCode)
            val record = FuelRecordEntity(
                id = current.recordId ?: 0,
                vehicleId = vehicle.id,
                // Fill-ups are logged by day, so noon keeps a record clear of
                // the boundary when it is read back in another time zone.
                filledAt = current.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                odometerKm = vehicle.distanceUnit.toKilometres(odometerValue.coerceAtLeast(0.0)),
                volumeLitres = vehicle.volumeUnit.toLitres(volumeValue.coerceAtLeast(0.0)),
                totalCostMinor = ((current.total.toDecimalOrNull() ?: 0.0) * scale).roundToLong().coerceAtLeast(0L),
                station = current.station.trim().ifBlank { null },
                isFullTank = current.isFullTank,
                missedPreviousFillUp = current.missedPreviousFillUp,
                notes = current.notes.trim().ifBlank { null },
            )

            val recordId = if (current.recordId != null) {
                fuel.update(record)
                current.recordId
            } else {
                fuel.add(record)
            }

            val existing = if (current.recordId != null) {
                attachments.listForOwner(AttachmentOwner.FuelRecord, current.recordId)
            } else {
                emptyList()
            }
            val existingUris = existing.map { it.uri }.toSet()
            for (att in existing) {
                if (att.uri !in current.attachmentUris) {
                    attachments.delete(att)
                }
            }

            for (uriStr in current.attachmentUris) {
                if (uriStr !in existingUris) {
                    runCatching {
                        val att = attachmentManager.saveAttachment(
                            sourceUri = uriStr.toUri(),
                            ownerType = AttachmentOwner.FuelRecord,
                            ownerId = recordId,
                        )
                        attachments.add(att)
                    }
                }
            }

            _state.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun delete() {
        val id = _state.value.recordId ?: return
        viewModelScope.launch {
            attachments.deleteForOwner(AttachmentOwner.FuelRecord, id)
            fuel.delete(id)
            _state.update { it.copy(isSaved = true) }
        }
    }
}

/**
 * Keeps volume, unit price and total consistent by recomputing whichever one
 * the user has not touched most recently. A pump receipt shows all three and
 * people copy whichever two are easiest to read.
 */
private fun FuelEditorUiState.recalculated(edited: CostField): FuelEditorUiState {
    val order = (listOf(edited) + costEdits.filterNot { it == edited }).take(2)
    val volume = volume.toDecimalOrNull()
    val unitPrice = unitPrice.toDecimalOrNull()
    val total = total.toDecimalOrNull()

    val derived = CostField.entries.firstOrNull { it !in order } ?: return copy(costEdits = order)

    return when (derived) {
        CostField.Total ->
            if (volume != null && unitPrice != null) {
                copy(total = (volume * unitPrice).trimmed(), costEdits = order)
            } else {
                copy(costEdits = order)
            }

        CostField.UnitPrice ->
            if (total != null && volume != null && volume > 0.0) {
                copy(unitPrice = (total / volume).trimmed(), costEdits = order)
            } else {
                copy(costEdits = order)
            }

        CostField.Volume ->
            if (total != null && unitPrice != null && unitPrice > 0.0) {
                copy(volume = (total / unitPrice).trimmed(), costEdits = order)
            } else {
                copy(costEdits = order)
            }
    }
}

/** Accepts either decimal separator, since keyboards disagree on which one to offer. */
private fun String.asDecimalInput(): String = filter { it.isDigit() || it == '.' || it == ',' }

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun Double.trimmed(): String {
    if (!isFinite()) return ""
    val rounded = Math.round(this * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
