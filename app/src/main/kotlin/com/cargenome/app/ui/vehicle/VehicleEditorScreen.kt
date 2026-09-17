package com.cargenome.app.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.vin.VinLookupState
import com.cargenome.app.data.vin.VinSource
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.OptionalDateField
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.nameRes
import com.cargenome.app.ui.common.suffixRes
import com.cargenome.vin.VinFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Currencies offered up front; anything else can be typed in. */
private val COMMON_CURRENCIES = listOf("RUB", "USD", "EUR", "KZT", "BYN", "UAH", "GBP")

private val POPULAR_MAKES = listOf(
    "BMW", "Toyota", "Mercedes-Benz", "Audi", "Volkswagen",
    "Lada", "Kia", "Hyundai", "Honda", "Haval", "Geely", "Chery", "Lexus", "Nissan", "Skoda",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScanVin: (() -> Unit)? = null,
    viewModel: VehicleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedVehicleId) {
        state.savedVehicleId?.let(onSaved)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.vehicle_edit_title else R.string.vehicle_add_title,
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
                    VinField(
                        state = state,
                        onVinChanged = viewModel::onVinChanged,
                        onScanVin = onScanVin,
                    )
                }
                item { VinStatus(state) }

                item { SectionLabel(stringResource(R.string.vehicle_section_identity)) }
                item {
                    OutlinedTextField(
                        value = state.make,
                        onValueChange = viewModel::onMakeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_make)) },
                        isError = state.showMakeError,
                        singleLine = true,
                    )
                }
                item {
                    com.cargenome.app.ui.common.PresetStringsRow(
                        label = stringResource(R.string.presets_makes),
                        presets = POPULAR_MAKES,
                        selected = state.make,
                        onSelect = viewModel::onMakeChanged,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = viewModel::onModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_model)) },
                        singleLine = true,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.modelYear,
                            onValueChange = viewModel::onModelYearChanged,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.field_year)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.trim,
                            onValueChange = viewModel::onTrimChanged,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.field_trim)) },
                            singleLine = true,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.engine,
                        onValueChange = viewModel::onEngineChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_engine)) },
                        singleLine = true,
                    )
                }
                item {
                    LabelledChips(
                        label = stringResource(R.string.field_fuel),
                        options = FuelType.entries,
                        selected = state.fuelType,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelected = viewModel::onFuelTypeChanged,
                    )
                }

                item { SectionLabel(stringResource(R.string.vehicle_section_yours)) }
                item {
                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = viewModel::onNicknameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_nickname)) },
                        supportingText = { Text(stringResource(R.string.field_nickname_hint)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.plateNumber,
                        onValueChange = { viewModel.onPlateChanged(it.uppercase()) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_plate)) },
                        placeholder = { Text("А000АА777") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                        ),
                        singleLine = true,
                    )
                }
                item { OptionalDateField(state.purchasedOn, viewModel::onPurchaseDateChanged, stringResource(R.string.field_purchase_date)) }
                item {
                    OutlinedTextField(
                        value = state.initialOdometer,
                        onValueChange = viewModel::onInitialOdometerChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_initial_odometer)) },
                        suffix = { Text(stringResource(state.distanceUnit.suffixRes())) },
                        supportingText = { Text(stringResource(R.string.field_initial_odometer_hint)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                    )
                }

                item { SectionLabel(stringResource(R.string.vehicle_section_insurance)) }
                item {
                    OutlinedTextField(
                        value = state.insuranceProvider,
                        onValueChange = viewModel::onInsuranceProviderChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_insurance_provider)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.insurancePolicyNumber,
                        onValueChange = viewModel::onInsurancePolicyNumberChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_insurance_policy)) },
                        singleLine = true,
                    )
                }
                item { OptionalDateField(state.insuranceExpiresOn, viewModel::onInsuranceExpiryChanged, stringResource(R.string.field_insurance_expires)) }


                item { SectionLabel(stringResource(R.string.vehicle_section_units)) }
                item {
                    UnitSelector(
                        label = stringResource(R.string.field_distance_unit),
                        options = DistanceUnit.entries,
                        selected = state.distanceUnit,
                        optionLabel = { stringResource(it.nameRes()) },
                        onSelected = viewModel::onDistanceUnitChanged,
                    )
                }
                item {
                    UnitSelector(
                        label = stringResource(R.string.field_volume_unit),
                        options = VolumeUnit.entries,
                        selected = state.volumeUnit,
                        optionLabel = { stringResource(it.nameRes()) },
                        onSelected = viewModel::onVolumeUnitChanged,
                    )
                }
                item { CurrencyField(state.currencyCode, viewModel::onCurrencyChanged) }

                if (state.duplicateVin) {
                    item {
                        Text(
                            text = stringResource(R.string.vehicle_duplicate_vin),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun VinField(
    state: VehicleEditorUiState,
    onVinChanged: (String) -> Unit,
    onScanVin: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = state.vin,
        onValueChange = onVinChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vin_input_label)) },
        supportingText = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.vehicle_vin_hint))
                Text(stringResource(R.string.vin_counter, state.vin.length))
            }
        },
        trailingIcon = {
            if (onScanVin != null && state.vin.isEmpty() && !state.isEditing) {
                val scanLabel = stringResource(R.string.vin_scan_action)
                IconButton(
                    onClick = onScanVin,
                    modifier = Modifier.semantics { contentDescription = scanLabel },
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = scanLabel,
                    )
                }
            }
        },
        isError = state.vin.isNotEmpty() && state.vin.length < VinFormat.LENGTH,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
    )
}

/** One line under the VIN field saying what the decode found, or why it did not. */
@Composable
private fun VinStatus(state: VehicleEditorUiState) {
    val message = when (val lookup = state.vinLookup) {
        null -> if (state.vinIsComplete) stringResource(R.string.vin_source_checking) else null

        is VinLookupState.Invalid -> stringResource(R.string.vehicle_vin_invalid)

        is VinLookupState.Ready -> when {
            lookup.isEnriching -> stringResource(R.string.vin_source_checking)
            lookup.onlineFailure != null -> stringResource(R.string.vin_source_failed)
            lookup.source == VinSource.Offline -> stringResource(R.string.vehicle_vin_offline_only)
            else -> stringResource(R.string.vehicle_vin_prefilled)
        }
    } ?: return

    val isBusy = state.vinLookup.let { it == null || (it as? VinLookupState.Ready)?.isEnriching == true }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.vinLookup is VinLookupState.Invalid) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> UnitSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(optionLabel(option), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun <T> LabelledChips(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

@Composable
private fun CurrencyField(value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_currency)) },
            supportingText = { Text(stringResource(R.string.field_currency_hint)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            singleLine = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_CURRENCIES.forEach { code ->
                FilterChip(
                    selected = code == value,
                    onClick = { onChange(code) },
                    label = { Text(code) },
                )
            }
        }
    }
}
