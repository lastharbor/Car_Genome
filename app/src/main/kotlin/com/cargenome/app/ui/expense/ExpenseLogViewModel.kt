package com.cargenome.app.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.ExpenseRepository
import com.cargenome.app.data.repository.VehicleRepository
import com.cargenome.app.ui.navigation.ExpenseLogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch

@Immutable
data class ExpenseLogUiState(
    val vehicle: VehicleEntity? = null,
    val expenses: List<ExpenseEntity> = emptyList(),
    val totalSpendMinor: Long = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ExpenseLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vehicles: VehicleRepository,
    private val expenseRepo: ExpenseRepository,
) : ViewModel() {

    val vehicleId: Long = savedStateHandle.toRoute<ExpenseLogRoute>().vehicleId

    val state: StateFlow<ExpenseLogUiState> = combine(
        vehicles.observe(vehicleId),
        expenseRepo.observe(vehicleId),
    ) { vehicle, expenses ->
        ExpenseLogUiState(
            vehicle = vehicle,
            expenses = expenses,
            totalSpendMinor = expenses.sumOf { it.amountMinor },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ExpenseLogUiState(),
    )

    fun delete(id: Long) {
        viewModelScope.launch {
            expenseRepo.delete(id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
