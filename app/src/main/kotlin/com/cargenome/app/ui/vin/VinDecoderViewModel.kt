package com.cargenome.app.ui.vin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cargenome.app.data.vin.VinLookupState
import com.cargenome.app.data.vin.VinRepository
import com.cargenome.vin.VinFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VinDecoderUiState(
    val input: String = "",
    val lookup: VinLookupState? = null,
) {
    val isComplete: Boolean get() = input.length == VinFormat.LENGTH
}

@HiltViewModel
class VinDecoderViewModel @Inject constructor(
    private val repository: VinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VinDecoderUiState())
    val state: StateFlow<VinDecoderUiState> = _state.asStateFlow()

    private var decoding: Job? = null

    fun onInputChanged(raw: String) {
        val normalized = VinFormat.normalize(raw).take(VinFormat.LENGTH)
        if (normalized == _state.value.input) return
        _state.update { it.copy(input = normalized, lookup = null) }

        decoding?.cancel()
        if (normalized.length < VinFormat.LENGTH) return
        // Seventeen characters is the only signal the user is done typing, so
        // there is nothing to debounce: decode right away.
        decoding = viewModelScope.launch {
            repository.lookup(normalized).collect { lookup ->
                _state.update { if (it.input == normalized) it.copy(lookup = lookup) else it }
            }
        }
    }

    fun onClear() {
        decoding?.cancel()
        _state.value = VinDecoderUiState()
    }
}
