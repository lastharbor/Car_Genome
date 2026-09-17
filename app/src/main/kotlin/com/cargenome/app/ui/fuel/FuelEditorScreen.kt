package com.cargenome.app.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.ui.common.DateField
import com.cargenome.app.ui.common.DecimalField
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SwitchRow
import com.cargenome.app.ui.common.ReceiptAttachmentSection
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes

private val PopularFuelStations = listOf(
    "Лукойл",
    "Газпромнефть",
    "Роснефть",
    "Татнефть",
    "Teboil",
    "Башнефть",
    "Шелл",
)

private val VolumePresetDeltas: List<Pair<String, Double>> = listOf(
    "+10" to 10.0,
    "+20" to 20.0,
    "+30" to 30.0,
    "30" to -30.0,
    "40" to -40.0,
    "50" to -50.0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FuelEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.fuel_edit_title else R.string.fuel_add_title,
                        ),
                    )
                },
                navigationIcon = {
                    val label = stringResource(R.string.action_back)
                    IconButton(onBack, Modifier.semantics { contentDescription = label }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = label,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current
        val volumeSuffix = stringResource(state.volumeUnit.suffixRes())
        val distanceSuffix = stringResource(state.distanceUnit.suffixRes())

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp + sides.calculateStartPadding(direction),
                    end = 16.dp + sides.calculateEndPadding(direction),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    DateField(
                        value = state.date,
                        onValueChange = viewModel::onDateChanged,
                        label = stringResource(R.string.fuel_date),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    DecimalField(
                        value = state.odometer,
                        onValueChange = viewModel::onOdometerChanged,
                        label = stringResource(R.string.fuel_odometer),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = distanceSuffix,
                        isError = state.odometerGoesBackwards,
                        supportingText = when {
                            state.odometerGoesBackwards ->
                                stringResource(R.string.fuel_odometer_backwards)
                            state.lastOdometerKm != null -> stringResource(
                                R.string.fuel_odometer_last,
                                stringResource(
                                    state.distanceUnit.shortRes(),
                                    Format.distance(state.lastOdometerKm!!, state.distanceUnit, locale),
                                ),
                            )
                            else -> null
                        },
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DecimalField(
                            value = state.volume,
                            onValueChange = viewModel::onVolumeChanged,
                            label = stringResource(R.string.fuel_volume),
                            modifier = Modifier.weight(1f),
                            suffix = volumeSuffix,
                        )
                        DecimalField(
                            value = state.unitPrice,
                            onValueChange = viewModel::onUnitPriceChanged,
                            label = stringResource(R.string.fuel_unit_price),
                            modifier = Modifier.weight(1f),
                            suffix = "${state.currencyCode}/$volumeSuffix",
                        )
                    }
                }

                item {
                    val volumePresets = remember(volumeSuffix) {
                        VolumePresetDeltas.map { (label, delta) ->
                            com.cargenome.app.ui.common.FormPreset<Double>("$label $volumeSuffix", delta)
                        }
                    }
                    com.cargenome.app.ui.common.PresetChipsRow(
                        label = stringResource(R.string.presets_volumes),
                        presets = volumePresets,
                        onSelect = { deltaOrVal: Double ->
                            if (deltaOrVal > 0.0) {
                                viewModel.addVolume(deltaOrVal)
                            } else {
                                viewModel.onVolumeChanged((-deltaOrVal).toInt().toString())
                            }
                        },
                    )
                }

                item {
                    DecimalField(
                        value = state.total,
                        onValueChange = viewModel::onTotalChanged,
                        label = stringResource(R.string.fuel_total),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = state.currencyCode,
                        supportingText = stringResource(R.string.fuel_cost_hint),
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.station,
                        onValueChange = viewModel::onStationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.fuel_station)) },
                        singleLine = true,
                    )
                }

                item {
                    com.cargenome.app.ui.common.PresetStringsRow(
                        label = stringResource(R.string.presets_stations),
                        presets = PopularFuelStations,
                        selected = state.station,
                        onSelect = viewModel::onStationChanged,
                    )
                }

                item {
                    SwitchRow(
                        label = stringResource(R.string.fuel_full_tank),
                        checked = state.isFullTank,
                        onCheckedChange = viewModel::onFullTankChanged,
                        supportingText = stringResource(R.string.fuel_full_tank_hint),
                    )
                }

                item {
                    SwitchRow(
                        label = stringResource(R.string.fuel_missed_previous),
                        checked = state.missedPreviousFillUp,
                        onCheckedChange = viewModel::onMissedFillUpChanged,
                        supportingText = stringResource(R.string.fuel_missed_previous_hint),
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::onNotesChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_notes)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        minLines = 2,
                    )
                }

                item {
                    ReceiptAttachmentSection(
                        attachmentUris = state.attachmentUris,
                        onAddAttachment = viewModel::onAddAttachment,
                        onRemoveAttachment = viewModel::onRemoveAttachment,
                        attachmentManager = viewModel.attachmentManager,
                        ocrScanner = viewModel.ocrScanner,
                        onApplyOcr = viewModel::applyOcrResult,
                    )
                }

                item {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                if (state.isEditing) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::delete,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
