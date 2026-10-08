package com.cargenome.app.ui.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cargenome.app.data.db.entity.LoyaltyCardEntity
import com.cargenome.app.data.db.entity.LoyaltyCategory
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.repository.LoyaltyCardRepository
import com.cargenome.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoyaltyCardsUiState(
    val cards: List<LoyaltyCardEntity> = emptyList(),
    val filteredCards: List<LoyaltyCardEntity> = emptyList(),
    val selectedCategory: LoyaltyCategory? = null,
    val vehicles: List<VehicleEntity> = emptyList(),
    val selectedVehicleId: Long? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class LoyaltyCardsViewModel @Inject constructor(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<LoyaltyCategory?>(null)
    val selectedCategory: StateFlow<LoyaltyCategory?> = _selectedCategory.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId: StateFlow<Long?> = _selectedVehicleId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<LoyaltyCardsUiState> = combine(
        loyaltyCardRepository.observeAll(),
        vehicleRepository.observeActive(),
        _selectedCategory,
        _selectedVehicleId,
        _searchQuery,
    ) { allCards, vehicles, category, vehicleId, query ->
        val filtered = allCards.filter { card ->
            val matchCategory = category == null || card.category == category
            val matchVehicle = vehicleId == null || card.vehicleId == null || card.vehicleId == vehicleId
            val matchQuery = query.isBlank() ||
                card.title.contains(query, ignoreCase = true) ||
                card.cardNumber.contains(query, ignoreCase = true) ||
                (card.note?.contains(query, ignoreCase = true) == true)
            matchCategory && matchVehicle && matchQuery
        }
        LoyaltyCardsUiState(
            cards = allCards,
            filteredCards = filtered,
            selectedCategory = category,
            vehicles = vehicles,
            selectedVehicleId = vehicleId,
            searchQuery = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoyaltyCardsUiState(),
    )

    fun onCategorySelected(category: LoyaltyCategory?) {
        _selectedCategory.value = category
    }

    fun onVehicleSelected(vehicleId: Long?) {
        _selectedVehicleId.value = vehicleId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteCard(card: LoyaltyCardEntity) {
        viewModelScope.launch {
            loyaltyCardRepository.delete(card)
        }
    }
}
