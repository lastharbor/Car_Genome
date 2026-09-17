package com.cargenome.app.ui.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.service.MaintenanceReminderScheduler
import com.cargenome.app.ui.navigation.ScheduleEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleEditorUiState(
    val scheduleId: Long? = null,
    val isLoading: Boolean = true,
    val vehicle: VehicleEntity? = null,

    val title: String = "",
    val category: ServiceCategory = ServiceCategory.RoutineService,
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val warnBeforeKm: String = "500",
    val warnBeforeDays: String = "14",
    val isEnabled: Boolean = true,
    val lastPerformedDate: LocalDate? = null,
    val lastPerformedOdometer: String = "",
    val notes: String = "",

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = scheduleId != null

    val distanceUnit: DistanceUnit get() = vehicle?.distanceUnit ?: DistanceUnit.Kilometres

    val canSave: Boolean
        get() = !isSaving && title.isNotBlank() &&
            ((intervalKm.toDecimalOrNull() ?: 0.0) > 0.0 || (intervalMonths.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val service: ServiceRepository,
    private val reminderScheduler: MaintenanceReminderScheduler,
) : ViewModel() {

    private val route: ScheduleEditorRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(ScheduleEditorUiState(scheduleId = route.scheduleId))
    val state: StateFlow<ScheduleEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.find(route.vehicleId)
            val schedule = route.scheduleId?.let { service.findSchedule(it) }
            val zone = ZoneId.systemDefault()

            _state.update { current ->
                if (schedule == null || vehicle == null) {
                    val defaultWarnKm = vehicle?.distanceUnit?.fromKilometres(500.0)?.trimmed() ?: "500"
                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        warnBeforeKm = defaultWarnKm,
                    )
                } else {
                    val distUnit = vehicle.distanceUnit
                    val intervalDistStr = schedule.intervalKm?.let { distUnit.fromKilometres(it).trimmed() }.orEmpty()
                    val warnDistStr = distUnit.fromKilometres(schedule.warnBeforeKm).trimmed()
                    val lastOdoStr = schedule.lastPerformedOdometerKm?.let { distUnit.fromKilometres(it).trimmed() }.orEmpty()
                    val lastDate = schedule.lastPerformedAt?.atZone(zone)?.toLocalDate()

                    current.copy(
                        isLoading = false,
                        vehicle = vehicle,
                        title = schedule.title,
                        category = schedule.category,
                        intervalKm = intervalDistStr,
                        intervalMonths = schedule.intervalMonths?.toString().orEmpty(),
                        warnBeforeKm = warnDistStr,
                        warnBeforeDays = schedule.warnBeforeDays.toString(),
                        isEnabled = schedule.isEnabled,
                        lastPerformedDate = lastDate,
                        lastPerformedOdometer = lastOdoStr,
                        notes = schedule.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value) }

    fun onCategoryChanged(value: ServiceCategory) = _state.update { it.copy(category = value) }

    fun onIntervalKmChanged(value: String) = _state.update { it.copy(intervalKm = value.asDecimalInput()) }

    fun onIntervalMonthsChanged(value: String) = _state.update { it.copy(intervalMonths = value.filter { it.isDigit() }) }

    fun onWarnBeforeKmChanged(value: String) = _state.update { it.copy(warnBeforeKm = value.asDecimalInput()) }

    fun onWarnBeforeDaysChanged(value: String) = _state.update { it.copy(warnBeforeDays = value.filter { it.isDigit() }) }

    fun onIsEnabledChanged(value: Boolean) = _state.update { it.copy(isEnabled = value) }

    fun onLastPerformedDateChanged(value: LocalDate?) = _state.update { it.copy(lastPerformedDate = value) }

    fun onLastPerformedOdometerChanged(value: String) = _state.update { it.copy(lastPerformedOdometer = value.asDecimalInput()) }

    fun onNotesChanged(value: String) = _state.update { it.copy(notes = value) }

    fun save() {
        val current = _state.value
        val vehicle = current.vehicle ?: return
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val distUnit = vehicle.distanceUnit
            val intervalKm = current.intervalKm.toDecimalOrNull()?.takeIf { it > 0.0 }?.let { distUnit.toKilometres(it) }
            val warnKm = current.warnBeforeKm.toDecimalOrNull()?.takeIf { it >= 0.0 }?.let { distUnit.toKilometres(it) } ?: 500.0
            val intervalMonths = current.intervalMonths.toIntOrNull()?.takeIf { it > 0 }
            val warnDays = current.warnBeforeDays.toIntOrNull()?.takeIf { it >= 0 } ?: 14
            val lastKm = current.lastPerformedOdometer.toDecimalOrNull()?.takeIf { it >= 0.0 }?.let { distUnit.toKilometres(it) }
            val lastInstant = current.lastPerformedDate?.atTime(LocalTime.NOON)?.atZone(ZoneId.systemDefault())?.toInstant()

            val schedule = MaintenanceScheduleEntity(
                id = current.scheduleId ?: 0,
                vehicleId = vehicle.id,
                title = current.title.trim(),
                category = current.category,
                intervalKm = intervalKm,
                intervalMonths = intervalMonths,
                lastPerformedAt = lastInstant,
                lastPerformedOdometerKm = lastKm,
                warnBeforeKm = warnKm,
                warnBeforeDays = warnDays,
                isEnabled = current.isEnabled,
                notes = current.notes.trim().ifBlank { null },
            )

            if (current.scheduleId != null) {
                service.updateSchedule(schedule)
            } else {
                service.addSchedule(schedule)
            }
            reminderScheduler.runImmediately()
            _state.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun delete() {
        val id = _state.value.scheduleId ?: return
        viewModelScope.launch {
            val existing = service.findSchedule(id) ?: return@launch
            service.deleteSchedule(existing)
            reminderScheduler.runImmediately()
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
