package com.cargenome.app.ui.vin

import androidx.lifecycle.ViewModel
import com.cargenome.vin.DecodedVin
import com.cargenome.vin.OfflineVinDecoder
import com.cargenome.vin.VinDecodeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VinScanUiState(
    val detectedVin: String? = null,
    val decodedInfo: DecodedVin? = null,
    val isScanning: Boolean = true,
    val torchEnabled: Boolean = false,
)

@HiltViewModel
class VinScanViewModel @Inject constructor(
    private val offlineVinDecoder: OfflineVinDecoder,
) : ViewModel() {

    private val _state = MutableStateFlow(VinScanUiState())
    val state: StateFlow<VinScanUiState> = _state.asStateFlow()

    fun onVinDetected(vin: String) {
        if (_state.value.detectedVin == vin) return

        val decoded = when (val res = offlineVinDecoder.decode(vin)) {
            is VinDecodeResult.Decoded -> res.value
            is VinDecodeResult.Malformed -> null
        }

        _state.update {
            it.copy(
                detectedVin = vin,
                decodedInfo = decoded,
                isScanning = false,
            )
        }
    }

    fun onRescan() {
        _state.update {
            it.copy(
                detectedVin = null,
                decodedInfo = null,
                isScanning = true,
            )
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _state.update { it.copy(torchEnabled = enabled) }
    }
}
