package com.cargenome.app.ui.vin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.vin.VinLookupState
import com.cargenome.app.data.vin.VinSource
import com.cargenome.app.domain.model.VehicleProfile
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.theme.LocalStatusColors
import com.cargenome.vin.VinProblem

/** VINs that exercise the interesting corners: North America, Germany, Japan, Russia. */
private val EXAMPLE_VINS = listOf(
    "1HGCM82633A004352",
    "WVWZZZ1JZ3W386752",
    "JTDKB20U887746531",
    "XTA21140053912345",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VinDecoderScreen(
    onAddToGarage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onScanVin: (() -> Unit)? = null,
    viewModel: VinDecoderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vin_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        val label = stringResource(R.string.action_back)
                        IconButton(onBack, Modifier.semantics { contentDescription = label }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = label,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Landscape and foldables give far more width than a detail row needs,
        // and the camera cutout eats into one side.
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp + sides.calculateStartPadding(direction),
                    end = 16.dp + sides.calculateEndPadding(direction),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    VinInput(
                        value = state.input,
                        onValueChange = viewModel::onInputChanged,
                        onClear = viewModel::onClear,
                        onScanVin = onScanVin,
                    )
                }

                if (state.input.isEmpty()) {
                    item { EmptyState() }
                    item {
                        Text(
                            text = stringResource(R.string.vin_examples),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EXAMPLE_VINS.forEach { example ->
                                SuggestionChip(
                                    onClick = { viewModel.onInputChanged(example) },
                                    label = {
                                        Text(
                                            text = example,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                when (val lookup = state.lookup) {
                    null -> Unit

                    is VinLookupState.Invalid -> item { ProblemsCard(lookup.problems) }

                    is VinLookupState.Ready -> {
                        if (lookup.profile.problems.isNotEmpty()) {
                            item { ProblemsCard(lookup.profile.problems) }
                        }
                        item { SourceRow(lookup) }
                        item { VehicleCard(lookup.profile) }
                        item { StructureCard(lookup.profile) }
                        item {
                            Button(
                                onClick = { onAddToGarage(lookup.profile.vin) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.vin_add_to_garage))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VinInput(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onScanVin: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vin_input_label)) },
        supportingText = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.vin_input_supporting))
                Text(stringResource(R.string.vin_counter, value.length))
            }
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                val clearLabel = stringResource(R.string.vin_clear)
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.semantics { contentDescription = clearLabel },
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = clearLabel,
                    )
                }
            } else if (onScanVin != null) {
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
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
    )
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.vin_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.vin_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Says where the details came from, because offline and vPIC differ in depth. */
@Composable
private fun SourceRow(state: VinLookupState.Ready) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    when {
                        state.isEnriching -> stringResource(R.string.vin_source_checking)
                        state.source == VinSource.Network -> stringResource(R.string.vin_source_online)
                        state.source == VinSource.Cached -> stringResource(R.string.vin_source_cached)
                        else -> stringResource(R.string.vin_source_offline)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = if (state.isEnriching) {
                { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        if (state.onlineFailure != null) {
            Text(
                text = stringResource(R.string.vin_source_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProblemsCard(problems: List<VinProblem>) {
    val status = LocalStatusColors.current
    val isBlocking = problems.any { it !is VinProblem.CheckDigitMismatch || it.mandatory }
    val container = if (isBlocking) MaterialTheme.colorScheme.errorContainer else status.warningContainer
    val content = if (isBlocking) MaterialTheme.colorScheme.onErrorContainer else status.onWarningContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            problems.forEach { Text(it.describe(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun VinProblem.describe(): String = when (this) {
    is VinProblem.WrongLength -> stringResource(R.string.vin_problem_length, actual)
    is VinProblem.IllegalCharacters ->
        stringResource(R.string.vin_problem_characters, positions.joinToString(", "))
    is VinProblem.CheckDigitMismatch -> if (mandatory) {
        stringResource(R.string.vin_problem_check_error, expected.toString(), actual.toString())
    } else {
        stringResource(R.string.vin_problem_check_warning, actual.toString(), expected.toString())
    }
}

@Composable
private fun VehicleCard(profile: VehicleProfile) {
    val locale = LocalConfiguration.current.locales[0]
    val unknown = stringResource(R.string.vin_unknown)

    SectionCard(stringResource(R.string.vin_section_vehicle)) {
        DetailRow(stringResource(R.string.vin_make), profile.make ?: unknown)
        profile.manufacturer
            ?.takeIf { it != profile.make }
            ?.let { DetailRow(stringResource(R.string.vin_manufacturer), it) }
        profile.model?.let { DetailRow(stringResource(R.string.field_model), it) }
        profile.trim?.let { DetailRow(stringResource(R.string.field_trim), it) }

        DetailRow(
            label = stringResource(R.string.vin_country),
            value = profile.country?.displayName(locale) ?: unknown,
            hint = profile.region?.let { stringResource(it.labelRes()) },
        )

        DetailRow(
            label = stringResource(R.string.vin_year),
            value = when {
                profile.modelYear == null -> stringResource(R.string.vin_year_unknown)
                profile.modelYearIsAmbiguous && profile.alternativeModelYear != null ->
                    stringResource(
                        R.string.vin_year_ambiguous,
                        profile.alternativeModelYear,
                        profile.modelYear,
                    )
                else -> profile.modelYear.toString()
            },
            hint = when {
                profile.modelYear == null -> stringResource(R.string.vin_year_hint_absent)
                profile.modelYearIsAmbiguous -> stringResource(R.string.vin_year_hint_cycle)
                else -> null
            },
        )

        profile.bodyClass?.let { DetailRow(stringResource(R.string.field_body), it) }
        profile.engine?.let { DetailRow(stringResource(R.string.field_engine), it) }
        profile.fuelTypeLabel?.let { DetailRow(stringResource(R.string.field_fuel), it) }
        profile.transmission?.let { DetailRow(stringResource(R.string.field_transmission), it) }
        profile.driveType?.let { DetailRow(stringResource(R.string.field_drive), it) }
        profile.plant?.let { DetailRow(stringResource(R.string.vin_plant_location), it) }
    }
}

@Composable
private fun StructureCard(profile: VehicleProfile) {
    SectionCard(stringResource(R.string.vin_section_structure)) {
        Text(
            text = profile.highlighted(
                wmi = MaterialTheme.colorScheme.primary,
                vds = MaterialTheme.colorScheme.onSurface,
                vis = MaterialTheme.colorScheme.tertiary,
            ),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        DetailRow(stringResource(R.string.vin_wmi), profile.wmi)
        DetailRow(stringResource(R.string.vin_vds), profile.vds)
        DetailRow(stringResource(R.string.vin_vis), profile.vis)
        DetailRow(stringResource(R.string.vin_plant), profile.plantCode?.toString().orEmpty())
        DetailRow(stringResource(R.string.vin_serial), profile.serialNumber)

        val digit = profile.checkDigit ?: return@SectionCard
        val actual = digit.actual?.toString().orEmpty()
        DetailRow(
            label = stringResource(R.string.vin_check_digit),
            value = when {
                digit.matches -> stringResource(R.string.vin_check_digit_ok, actual)
                digit.isMandatory -> actual
                else -> stringResource(R.string.vin_check_digit_unused, actual)
            },
        )
    }
}

private fun VehicleProfile.highlighted(wmi: Color, vds: Color, vis: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = wmi)) { append(this@highlighted.wmi) }
        withStyle(SpanStyle(color = vds)) { append(this@highlighted.vds) }
        withStyle(SpanStyle(color = vis)) { append(this@highlighted.vis) }
    }
