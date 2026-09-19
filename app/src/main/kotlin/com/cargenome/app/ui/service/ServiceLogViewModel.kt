package com.cargenome.app.ui.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.totalCostMinor
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.service.MaintenanceReminderScheduler
import com.cargenome.app.domain.service.MaintenanceScheduleCalculator
import com.cargenome.app.domain.service.ScheduleStatus
import com.cargenome.app.ui.navigation.ServiceLogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.withContext

enum class ServiceTab {
    Records,
    Calendar,
    Schedule,
}

@Immutable
data class ServiceLogUiState(
    val vehicle: VehicleEntity? = null,
    val records: List<ServiceRecordEntity> = emptyList(),
    val schedules: List<ScheduleStatus> = emptyList(),
    val events: List<MaintenanceEventEntity> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val currentOdometerKm: Double? = null,
    val totalSpendMinor: Long = 0,
    val partsSpendMinor: Long = 0,
    val labourSpendMinor: Long = 0,
    val selectedTab: ServiceTab = ServiceTab.Records,
    val isLoading: Boolean = true,
    val selectedDateEvents: List<MaintenanceEventEntity> = emptyList(),
    val upcomingEvents: List<MaintenanceEventEntity> = emptyList(),
    val overdueEvents: List<MaintenanceEventEntity> = emptyList(),
    val urgentSchedules: List<ScheduleStatus> = emptyList(),
    val activeEventsByScheduleId: Map<Long, MaintenanceEventEntity> = emptyMap(),
    val unscheduledUrgentSchedules: List<ScheduleStatus> = emptyList(),
    val nextUpcomingEvent: MaintenanceEventEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServiceLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val service: ServiceRepository,
    private val odometer: OdometerRepository,
    private val reminderScheduler: MaintenanceReminderScheduler,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<ServiceLogRoute>().vehicleId
    private val _selectedTab = MutableStateFlow(ServiceTab.Records)
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    val state: StateFlow<ServiceLogUiState> = combine(
        vehicles.observe(vehicleId),
        _selectedTab,
        _selectedDate,
    ) { vehicle, tab, selectedDate ->
        Triple(vehicle, tab, selectedDate)
    }.flatMapLatest { (vehicle, tab, selectedDate) ->
        if (vehicle == null) {
            flowOf(
                ServiceLogUiState(
                    vehicle = null,
                    selectedTab = tab,
                    selectedDate = selectedDate,
                    isLoading = false,
                ),
            )
        } else {
            combine(
                service.observeRecords(vehicle.id),
                service.observeSchedules(vehicle.id),
                service.observeEvents(vehicle.id),
                odometer.observeCurrentKm(vehicle.id),
            ) { records, schedules, events, currentKm ->
                withContext(Dispatchers.Default) {
                    val scheduleStatuses = MaintenanceScheduleCalculator.calculateAll(
                        schedules = schedules,
                        currentOdometerKm = currentKm,
                        initialOdometerKm = vehicle.initialOdometerKm,
                        purchasedOn = vehicle.purchasedOn,
                        vehicleCreatedAt = vehicle.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    )

                    val totalLabour = records.sumOf { it.labourCostMinor }
                    val totalParts = records.sumOf { it.partsCostMinor }
                    val totalAll = records.sumOf { it.totalCostMinor }

                    val today = LocalDate.now()
                    val selectedDateEvents = events.filter { it.scheduledDate == selectedDate }
                    val upcomingEvents = events.filter { !it.isCompleted && !it.scheduledDate.isBefore(today) }
                        .sortedWith(compareBy<MaintenanceEventEntity> { it.scheduledDate }.thenBy { it.scheduledTimeMinutes ?: 1440 })
                    val overdueEvents = events.filter { !it.isCompleted && it.scheduledDate.isBefore(today) }
                        .sortedWith(compareBy<MaintenanceEventEntity> { it.scheduledDate }.thenBy { it.scheduledTimeMinutes ?: 1440 })
                    val urgentSchedules = scheduleStatuses.filter { it.isOverdue || it.isDueSoon }
                    val activeEventsByScheduleId = events
                        .filter { !it.isCompleted && it.scheduleId != null && !it.scheduledDate.isBefore(today) }
                        .groupBy { it.scheduleId!! }
                        .mapValues { (_, evts) -> evts.minBy { it.scheduledDate } }
                    val unscheduledUrgentSchedules = urgentSchedules.filter { it.schedule.id !in activeEventsByScheduleId.keys }
                    val nextUpcomingEvent = upcomingEvents.minByOrNull { it.scheduledDate }

                    ServiceLogUiState(
                        vehicle = vehicle,
                        records = records,
                        schedules = scheduleStatuses,
                        events = events,
                        selectedDate = selectedDate,
                        currentOdometerKm = currentKm,
                        totalSpendMinor = totalAll,
                        partsSpendMinor = totalParts,
                        labourSpendMinor = totalLabour,
                        selectedTab = tab,
                        isLoading = false,
                        selectedDateEvents = selectedDateEvents,
                        upcomingEvents = upcomingEvents,
                        overdueEvents = overdueEvents,
                        urgentSchedules = urgentSchedules,
                        activeEventsByScheduleId = activeEventsByScheduleId,
                        unscheduledUrgentSchedules = unscheduledUrgentSchedules,
                        nextUpcomingEvent = nextUpcomingEvent,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ServiceLogUiState(),
    )

    fun selectTab(tab: ServiceTab) {
        _selectedTab.value = tab
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun addEvent(
        title: String,
        category: ServiceCategory,
        scheduledDate: LocalDate,
        scheduledTimeMinutes: Int?,
        targetOdometerKm: Double?,
        estimatedCostMinor: Long?,
        shop: String?,
        notes: String?,
        remindAdvanceDays: Int,
        scheduleId: Long? = null,
    ) {
        viewModelScope.launch {
            val targetVehicleId = vehicleId
            val event = MaintenanceEventEntity(
                vehicleId = targetVehicleId,
                scheduleId = scheduleId,
                title = title,
                category = category,
                scheduledDate = scheduledDate,
                scheduledTimeMinutes = scheduledTimeMinutes,
                targetOdometerKm = targetOdometerKm,
                estimatedCostMinor = estimatedCostMinor,
                shop = shop,
                notes = notes,
                remindAdvanceDays = remindAdvanceDays,
            )
            service.addEvent(event)
            reminderScheduler.runImmediately()
        }
    }

    fun updateEvent(event: MaintenanceEventEntity) {
        viewModelScope.launch {
            service.updateEvent(event)
            reminderScheduler.runImmediately()
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            service.deleteEvent(id)
            reminderScheduler.runImmediately()
        }
    }

    fun completeEvent(
        event: MaintenanceEventEntity,
        createRecord: Boolean,
        actualOdometerKm: Double?,
        labourCostMinor: Long,
        partsCostMinor: Long,
        shop: String?,
        notes: String?,
    ) {
        viewModelScope.launch {
            service.completeEvent(
                eventId = event.id,
                createServiceRecord = createRecord,
                actualOdometerKm = actualOdometerKm,
                labourCostMinor = labourCostMinor,
                partsCostMinor = partsCostMinor,
                shop = shop,
                notes = notes,
            )
            reminderScheduler.runImmediately()
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            service.deleteRecord(id)
            reminderScheduler.runImmediately()
        }
    }

    fun deleteSchedule(schedule: MaintenanceScheduleEntity) {
        viewModelScope.launch {
            service.deleteSchedule(schedule)
            reminderScheduler.runImmediately()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
