package com.cargenome.app.ui.analytics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.ExpenseRepository
import com.cargenome.app.data.repository.FuelRepository
import com.cargenome.app.data.repository.OdometerRepository
import com.cargenome.app.data.repository.ServiceRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.domain.analytics.AnalyticsTimeRange
import com.cargenome.app.domain.analytics.VehicleAnalyticsCalculator
import com.cargenome.app.domain.analytics.VehicleAnalyticsData
import com.cargenome.app.ui.navigation.AnalyticsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.withContext

@Immutable
data class DateFilter(
    val timeRange: AnalyticsTimeRange = AnalyticsTimeRange.ALL_TIME,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
)

@Immutable
data class AnalyticsUiState(
    val vehicle: VehicleEntity? = null,
    val data: VehicleAnalyticsData = VehicleAnalyticsData(),
    val selectedTimeRange: AnalyticsTimeRange = AnalyticsTimeRange.ALL_TIME,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val canStepBackward: Boolean = false,
    val canStepForward: Boolean = false,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val service: ServiceRepository,
    private val expenses: ExpenseRepository,
    private val odometer: OdometerRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<AnalyticsRoute>().vehicleId

    private val dateFilter = MutableStateFlow(DateFilter())

    val state: StateFlow<AnalyticsUiState> = vehicles.observe(vehicleId)
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(
                    AnalyticsUiState(
                        vehicle = null,
                        isLoading = false,
                    ),
                )
            } else {
                combine(
                    fuel.observe(vehicle.id),
                    service.observeRecords(vehicle.id),
                    expenses.observe(vehicle.id),
                    odometer.observeCurrentKm(vehicle.id),
                    dateFilter,
                ) { fuels, services, exps, currentKm, filter ->
                    withContext(Dispatchers.Default) {
                        val analyticsData = VehicleAnalyticsCalculator.calculate(
                            vehicle = vehicle,
                            fuelRecords = fuels,
                            serviceRecords = services,
                            expenses = exps,
                            currentOdometerKm = currentKm,
                            timeRange = filter.timeRange,
                            customStartDate = filter.customStartDate,
                            customEndDate = filter.customEndDate,
                        )
                        AnalyticsUiState(
                            vehicle = vehicle,
                            data = analyticsData,
                            selectedTimeRange = filter.timeRange,
                            customStartDate = filter.customStartDate,
                            customEndDate = filter.customEndDate,
                            canStepBackward = canStepBackward(filter),
                            canStepForward = canStepForward(filter),
                            isLoading = false,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AnalyticsUiState(),
        )

    fun setTimeRange(range: AnalyticsTimeRange) {
        if (range == AnalyticsTimeRange.CUSTOM) {
            val today = LocalDate.now()
            dateFilter.value = DateFilter(
                timeRange = AnalyticsTimeRange.CUSTOM,
                customStartDate = today.minusDays(29),
                customEndDate = today,
            )
        } else {
            dateFilter.value = DateFilter(timeRange = range)
        }
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val today = LocalDate.now()
        if (start == today.withDayOfMonth(1) && end == today) {
            dateFilter.value = DateFilter(timeRange = AnalyticsTimeRange.THIS_MONTH)
        } else if (start == today.withDayOfYear(1) && end == today) {
            dateFilter.value = DateFilter(timeRange = AnalyticsTimeRange.THIS_YEAR)
        } else {
            dateFilter.value = DateFilter(
                timeRange = AnalyticsTimeRange.CUSTOM,
                customStartDate = start,
                customEndDate = end,
            )
        }
    }

    fun stepPeriod(forward: Boolean) {
        val current = dateFilter.value
        val today = LocalDate.now()

        val nextFilter: DateFilter = when (current.timeRange) {
            AnalyticsTimeRange.ALL_TIME -> current
            AnalyticsTimeRange.THIS_MONTH -> {
                if (!forward) {
                    val prevMonth = YearMonth.from(today).minusMonths(1)
                    DateFilter(
                        timeRange = AnalyticsTimeRange.CUSTOM,
                        customStartDate = prevMonth.atDay(1),
                        customEndDate = prevMonth.atEndOfMonth(),
                    )
                } else current
            }
            AnalyticsTimeRange.THIS_YEAR -> {
                if (!forward) {
                    val prevYear = today.year - 1
                    DateFilter(
                        timeRange = AnalyticsTimeRange.CUSTOM,
                        customStartDate = LocalDate.of(prevYear, 1, 1),
                        customEndDate = LocalDate.of(prevYear, 12, 31),
                    )
                } else current
            }
            AnalyticsTimeRange.YEAR_1 -> {
                if (!forward) {
                    val end = today.minusDays(365)
                    val start = end.minusDays(365)
                    DateFilter(
                        timeRange = AnalyticsTimeRange.CUSTOM,
                        customStartDate = start,
                        customEndDate = end,
                    )
                } else current
            }
            AnalyticsTimeRange.MONTHS_6 -> {
                if (!forward) {
                    val end = today.minusDays(183)
                    val start = end.minusDays(183)
                    DateFilter(
                        timeRange = AnalyticsTimeRange.CUSTOM,
                        customStartDate = start,
                        customEndDate = end,
                    )
                } else current
            }
            AnalyticsTimeRange.MONTHS_3 -> {
                if (!forward) {
                    val end = today.minusDays(92)
                    val start = end.minusDays(92)
                    DateFilter(
                        timeRange = AnalyticsTimeRange.CUSTOM,
                        customStartDate = start,
                        customEndDate = end,
                    )
                } else current
            }
            AnalyticsTimeRange.CUSTOM -> {
                val start = current.customStartDate ?: today.minusDays(29)
                val end = current.customEndDate ?: today

                val isFullMonth = start.dayOfMonth == 1 &&
                    end == start.withDayOfMonth(start.lengthOfMonth())

                if (isFullMonth) {
                    val ym = YearMonth.of(start.year, start.month)
                    val newYm = if (forward) ym.plusMonths(1) else ym.minusMonths(1)
                    val currentYm = YearMonth.from(today)
                    if (newYm == currentYm) {
                        DateFilter(timeRange = AnalyticsTimeRange.THIS_MONTH)
                    } else if (newYm.isAfter(currentYm)) {
                        current
                    } else {
                        DateFilter(
                            timeRange = AnalyticsTimeRange.CUSTOM,
                            customStartDate = newYm.atDay(1),
                            customEndDate = newYm.atEndOfMonth(),
                        )
                    }
                } else {
                    val days = ChronoUnit.DAYS.between(start, end) + 1
                    if (forward) {
                        val newStart = end.plusDays(1)
                        val newEnd = newStart.plusDays(days - 1)
                        if (!newStart.isAfter(today)) {
                            DateFilter(
                                timeRange = AnalyticsTimeRange.CUSTOM,
                                customStartDate = newStart,
                                customEndDate = if (newEnd.isAfter(today)) today else newEnd,
                            )
                        } else current
                    } else {
                        val newEnd = start.minusDays(1)
                        val newStart = newEnd.minusDays(days - 1)
                        DateFilter(
                            timeRange = AnalyticsTimeRange.CUSTOM,
                            customStartDate = newStart,
                            customEndDate = newEnd,
                        )
                    }
                }
            }
        }

        dateFilter.value = nextFilter
    }

    private fun canStepBackward(filter: DateFilter): Boolean =
        filter.timeRange != AnalyticsTimeRange.ALL_TIME

    private fun canStepForward(filter: DateFilter, today: LocalDate = LocalDate.now()): Boolean =
        when (filter.timeRange) {
            AnalyticsTimeRange.ALL_TIME,
            AnalyticsTimeRange.THIS_MONTH,
            AnalyticsTimeRange.THIS_YEAR,
            AnalyticsTimeRange.YEAR_1,
            AnalyticsTimeRange.MONTHS_6,
            AnalyticsTimeRange.MONTHS_3 -> false
            AnalyticsTimeRange.CUSTOM -> {
                val end = filter.customEndDate
                end != null && end.isBefore(today)
            }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
