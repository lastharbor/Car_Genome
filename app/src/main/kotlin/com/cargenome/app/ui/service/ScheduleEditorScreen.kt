package com.cargenome.app.ui.service

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.ui.common.ChipSelector
import com.cargenome.app.ui.common.DecimalField
import com.cargenome.app.ui.common.OptionalDateField
import com.cargenome.app.ui.common.SwitchRow
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.suffixRes
import java.time.LocalDate

private val SchedulePresets = listOf(
    com.cargenome.app.ui.common.FormPreset("Моторное масло", listOf("Моторное масло и фильтр", "8000", "12", "1000")),
    com.cargenome.app.ui.common.FormPreset("Воздушный фильтр", listOf("Воздушный фильтр двигателя", "15000", "12", "1500")),
    com.cargenome.app.ui.common.FormPreset("Салонный фильтр", listOf("Салонный фильтр", "15000", "12", "1500")),
    com.cargenome.app.ui.common.FormPreset("Свечи зажигания", listOf("Свечи зажигания", "30000", "24", "2000")),
    com.cargenome.app.ui.common.FormPreset("Тормозная жидкость", listOf("Тормозная жидкость", "40000", "24", "2000")),
    com.cargenome.app.ui.common.FormPreset("Антифриз", listOf("Охлаждающая жидкость", "60000", "36", "3000")),
    com.cargenome.app.ui.common.FormPreset("Масло в КПП", listOf("Масло в трансмиссии", "60000", "48", "3000")),
    com.cargenome.app.ui.common.FormPreset("Ремень/цепь ГРМ", listOf("Привод ГРМ и ролики", "90000", "60", "5000")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                            if (state.isEditing) R.string.schedule_edit_title else R.string.schedule_add_title,
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
        val distanceSuffix = stringResource(state.distanceUnit.suffixRes())

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
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.schedule_title)) },
                        placeholder = { Text(stringResource(R.string.schedule_title_hint)) },
                        singleLine = true,
                    )
                }

                item {
                    com.cargenome.app.ui.common.PresetChipsRow(
                        label = stringResource(R.string.presets_schedules),
                        presets = SchedulePresets,
                        onSelect = { data ->
                            viewModel.onTitleChanged(data[0])
                            viewModel.onIntervalKmChanged(data[1])
                            viewModel.onIntervalMonthsChanged(data[2])
                            viewModel.onWarnBeforeKmChanged(data[3])
                        },
                    )
                }

                item {
                    ChipSelector(
                        label = stringResource(R.string.schedule_category),
                        options = ServiceCategory.entries,
                        selected = state.category,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelected = viewModel::onCategoryChanged,
                    )
                }

                item {
                    DecimalField(
                        value = state.intervalKm,
                        onValueChange = viewModel::onIntervalKmChanged,
                        label = stringResource(R.string.schedule_interval_km),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = distanceSuffix,
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.intervalMonths,
                        onValueChange = viewModel::onIntervalMonthsChanged,
                        label = { Text(stringResource(R.string.schedule_interval_months)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DecimalField(
                            value = state.warnBeforeKm,
                            onValueChange = viewModel::onWarnBeforeKmChanged,
                            label = stringResource(R.string.schedule_warn_km),
                            modifier = Modifier.weight(1f),
                            suffix = distanceSuffix,
                        )
                        OutlinedTextField(
                            value = state.warnBeforeDays,
                            onValueChange = viewModel::onWarnBeforeDaysChanged,
                            label = { Text(stringResource(R.string.schedule_warn_days)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                        )
                    }
                }

                item {
                    SwitchRow(
                        label = stringResource(R.string.schedule_is_enabled),
                        checked = state.isEnabled,
                        onCheckedChange = viewModel::onIsEnabledChanged,
                    )
                }

                item {
                    OptionalDateField(
                        value = state.lastPerformedDate,
                        onValueChange = viewModel::onLastPerformedDateChanged,
                        label = stringResource(R.string.schedule_last_performed_date),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    DecimalField(
                        value = state.lastPerformedOdometer,
                        onValueChange = viewModel::onLastPerformedOdometerChanged,
                        label = stringResource(R.string.schedule_last_performed_odometer),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = distanceSuffix,
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
