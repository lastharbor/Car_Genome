package com.cargenome.app.ui.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.core.net.toUri
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.ocr.ReceiptOcrScanner
import com.cargenome.app.data.ocr.ReceiptScanResult
import com.cargenome.app.data.repository.AttachmentRepository
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.navigation.ServiceEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceEditorUiState(
    val recordId: Long? = null,
    val isLoading: Boolean = true,
    val vehicle: VehicleEntity? = null,

    val date: LocalDate = LocalDate.now(),
    val odometer: String = "",
    val category: ServiceCategory = ServiceCategory.RoutineService,
    val title: String = "",
    val labourCost: String = "",
    val partsCost: String = "",
    val shop: String = "",
    val selectedScheduleId: Long? = null,
    val availableSchedules: List<MaintenanceScheduleEntity> = emptyList(),
    val notes: String = "",
    val attachmentUris: List<String> = emptyList(),

    val lastOdometerKm: Double? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = recordId != null

    val distanceUnit: DistanceUnit get() = vehicle?.distanceUnit ?: DistanceUnit.Kilometres

    val currencyCode: String get() = vehicle?.currencyCode ?: "RUB"

    val odometerValue: Double? get() = odometer.toDecimalOrNull()

    val labourCostValue: Double get() = (labourCost.toDecimalOrNull() ?: 0.0).coerceAtLeast(0.0)

    val partsCostValue: Double get() = (partsCost.toDecimalOrNull() ?: 0.0).coerceAtLeast(0.0)

    val totalCostValue: Double get() = labourCostValue + partsCostValue

    val canSave: Boolean
        get() = !isSaving && title.isNotBlank()
}

@HiltViewModel
class ServiceEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val service: ServiceRepository,
    private val odometer: OdometerRepository,
    val attachmentManager: AttachmentManager,
    val ocrScanner: ReceiptOcrScanner,
    private val attachments: AttachmentRepository,
) : ViewModel() {

    private val route: ServiceEditorRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(
        ServiceEditorUiState(
            recordId = route.recordId,
            selectedScheduleId = route.scheduleId,
        ),
    )
    val state: StateFlow<ServiceEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.find(route.vehicleId)
            val record = route.recordId?.let { service.findRecord(it) }
            val existingAttachments = route.recordId?.let {
                attachments.observe(AttachmentOwner.ServiceRecord, it).firstOrNull()
            } ?: emptyList()
            val schedules = service.observeSchedules(route.vehicleId).first()
            val lastKm = odometer.currentKm(route.vehicleId)
            val zone = ZoneId.systemDefault()

            _state.update { current ->
                if (record == null || vehicle == null) {
                    val prefilledSchedule = route.scheduleId?.let { sId -> schedules.find { it.id == sId } }
                    val currentKmStr = lastKm?.let { vehicle?.distanceUnit?.fromKilometres(it)?.trimmed() }.orEmpty()
                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        lastOdometerKm = lastKm,
                        odometer = currentKmStr,
                        availableSchedules = schedules,
                        title = prefilledSchedule?.title.orEmpty(),
                        category = prefilledSchedule?.category ?: ServiceCategory.RoutineService,
                        selectedScheduleId = route.scheduleId,
                        attachmentUris = existingAttachments.map { it.uri },
                    )
                } else {
                    val scale = Format.minorScale(vehicle.currencyCode)
                    val labourStr = if (record.labourCostMinor > 0) (record.labourCostMinor / scale).trimmed() else ""
                    val partsStr = if (record.partsCostMinor > 0) (record.partsCostMinor / scale).trimmed() else ""
                    val odoStr = record.odometerKm?.let { vehicle.distanceUnit.fromKilometres(it).trimmed() }.orEmpty()

                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        lastOdometerKm = lastKm,
                        availableSchedules = schedules,
                        date = record.performedAt.atZone(zone).toLocalDate(),
                        odometer = odoStr,
                        category = record.category,
                        title = record.title,
                        labourCost = labourStr,
                        partsCost = partsStr,
                        shop = record.shop.orEmpty(),
                        selectedScheduleId = record.scheduleId,
                        notes = record.notes.orEmpty(),
                        attachmentUris = existingAttachments.map { it.uri },
                    )
                }
            }
        }
    }

    fun onDateChanged(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onOdometerChanged(value: String) = _state.update { it.copy(odometer = value.asDecimalInput()) }

    fun onCategoryChanged(value: ServiceCategory) = _state.update { it.copy(category = value) }

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value) }

    fun onLabourCostChanged(value: String) = _state.update { it.copy(labourCost = value.asDecimalInput()) }

    fun onPartsCostChanged(value: String) = _state.update { it.copy(partsCost = value.asDecimalInput()) }

    fun onShopChanged(value: String) = _state.update { it.copy(shop = value) }

    fun onScheduleSelected(scheduleId: Long?) = _state.update { it.copy(selectedScheduleId = scheduleId) }

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
            if (ocr.station != null && updated.shop.isBlank()) {
                updated = updated.copy(shop = ocr.station)
            }
            if (ocr.totalCost != null) {
                if (updated.partsCost.isBlank() && updated.labourCost.isBlank()) {
                    updated = updated.copy(partsCost = ocr.totalCost.toString())
                }
            }
            updated
        }
    }

    fun save() {
        val current = _state.value
        val vehicle = current.vehicle ?: return
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val scale = Format.minorScale(vehicle.currencyCode)
            val labourMinor = (current.labourCostValue * scale).roundToLong().coerceAtLeast(0L)
            val partsMinor = (current.partsCostValue * scale).roundToLong().coerceAtLeast(0L)
            val odoKm = current.odometerValue?.coerceAtLeast(0.0)?.let { vehicle.distanceUnit.toKilometres(it) }

            val record = ServiceRecordEntity(
                id = current.recordId ?: 0,
                vehicleId = vehicle.id,
                performedAt = current.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                odometerKm = odoKm,
                category = current.category,
                title = current.title.trim(),
                labourCostMinor = labourMinor,
                partsCostMinor = partsMinor,
                shop = current.shop.trim().ifBlank { null },
                scheduleId = current.selectedScheduleId,
                notes = current.notes.trim().ifBlank { null },
            )

            val recordId = if (current.recordId != null) {
                service.updateRecord(record)
                current.recordId
            } else {
                service.addRecord(record)
            }

            val existing = if (current.recordId != null) {
                attachments.listForOwner(AttachmentOwner.ServiceRecord, current.recordId)
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
                            ownerType = AttachmentOwner.ServiceRecord,
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
            attachments.deleteForOwner(AttachmentOwner.ServiceRecord, id)
            service.deleteRecord(id)
            _state.update { it.copy(isSaved = true) }
        }
    }
}

private fun String.asDecimalInput(): String = filter { it.isDigit() || it == '.' || it == ',' }

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun Double.trimmed(): String {
    if (!isFinite()) return ""
    val rounded = Math.round(this * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
