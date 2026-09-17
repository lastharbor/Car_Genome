package com.cargenome.app.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.core.net.toUri
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.ocr.ReceiptOcrScanner
import com.cargenome.app.data.ocr.ReceiptScanResult
import com.cargenome.app.data.repository.AttachmentRepository
import com.cargenome.app.data.repository.ExpenseRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.navigation.ExpenseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class ExpenseEditorUiState(
    val expenseId: Long? = null,
    val isLoading: Boolean = true,
    val vehicle: VehicleEntity? = null,

    val date: LocalDate = LocalDate.now(),
    val category: ExpenseCategory = ExpenseCategory.Other,
    val title: String = "",
    val amount: String = "",
    val odometer: String = "",
    val notes: String = "",
    val attachmentUris: List<String> = emptyList(),

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = expenseId != null

    val distanceUnit: DistanceUnit get() = vehicle?.distanceUnit ?: DistanceUnit.Kilometres

    val currencyCode: String get() = vehicle?.currencyCode ?: "RUB"

    val amountValue: Double? get() = amount.toDecimalOrNull()

    val canSave: Boolean
        get() = !isSaving && title.isNotBlank() && (amountValue ?: 0.0) > 0.0
}

@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val expenses: ExpenseRepository,
    val attachmentManager: AttachmentManager,
    val ocrScanner: ReceiptOcrScanner,
    private val attachments: AttachmentRepository,
) : ViewModel() {

    private val route: ExpenseEditorRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(ExpenseEditorUiState(expenseId = route.expenseId))
    val state: StateFlow<ExpenseEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.find(route.vehicleId)
            val expense = route.expenseId?.let { expenses.find(it) }
            val existingAttachments = route.expenseId?.let {
                attachments.observe(AttachmentOwner.Expense, it).firstOrNull()
            } ?: emptyList()
            val zone = ZoneId.systemDefault()

            _state.update { current ->
                if (expense == null || vehicle == null) {
                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        attachmentUris = existingAttachments.map { it.uri },
                    )
                } else {
                    val scale = Format.minorScale(vehicle.currencyCode)
                    val amountStr = (expense.amountMinor / scale).trimmed()
                    val odoStr = expense.odometerKm?.let { vehicle.distanceUnit.fromKilometres(it).trimmed() }.orEmpty()

                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        date = expense.incurredAt.atZone(zone).toLocalDate(),
                        category = expense.category,
                        title = expense.title,
                        amount = amountStr,
                        odometer = odoStr,
                        notes = expense.notes.orEmpty(),
                        attachmentUris = existingAttachments.map { it.uri },
                    )
                }
            }
        }
    }

    fun onDateChanged(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onCategoryChanged(value: ExpenseCategory) = _state.update { it.copy(category = value) }

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value) }

    fun onAmountChanged(value: String) = _state.update { it.copy(amount = value.asDecimalInput()) }

    fun onOdometerChanged(value: String) = _state.update { it.copy(odometer = value.asDecimalInput()) }

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
            if (ocr.station != null && updated.title.isBlank()) {
                updated = updated.copy(title = ocr.station)
            }
            if (ocr.totalCost != null) {
                updated = updated.copy(amount = ocr.totalCost.toString())
            }
            updated
        }
    }

    fun save() {
        val current = _state.value
        val vehicle = current.vehicle ?: return
        val amount = current.amountValue ?: return
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val scale = Format.minorScale(vehicle.currencyCode)
            val amountMinor = (amount * scale).roundToLong().coerceAtLeast(0L)
            val odoKm = current.odometer.toDecimalOrNull()?.coerceAtLeast(0.0)?.let { vehicle.distanceUnit.toKilometres(it) }

            val entity = ExpenseEntity(
                id = current.expenseId ?: 0,
                vehicleId = vehicle.id,
                incurredAt = current.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                category = current.category,
                title = current.title.trim(),
                amountMinor = amountMinor,
                odometerKm = odoKm,
                notes = current.notes.trim().ifBlank { null },
            )

            val expenseId = if (current.expenseId != null) {
                expenses.update(entity)
                current.expenseId
            } else {
                expenses.add(entity)
            }

            val existing = if (current.expenseId != null) {
                attachments.listForOwner(AttachmentOwner.Expense, current.expenseId)
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
                            ownerType = AttachmentOwner.Expense,
                            ownerId = expenseId,
                        )
                        attachments.add(att)
                    }
                }
            }

            _state.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun delete() {
        val id = _state.value.expenseId ?: return
        viewModelScope.launch {
            attachments.deleteForOwner(AttachmentOwner.Expense, id)
            expenses.delete(id)
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
