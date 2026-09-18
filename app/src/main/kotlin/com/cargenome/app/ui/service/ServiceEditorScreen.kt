package com.cargenome.app.ui.service

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.ui.common.ChipSelector
import com.cargenome.app.ui.common.DateField
import com.cargenome.app.ui.common.DecimalField
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.ReceiptAttachmentSection
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.suffixRes

private val ServicePresets = listOf(
    com.cargenome.app.ui.common.FormPreset("Масло и фильтр", Pair("Замена масла двигателя и фильтра", ServiceCategory.RoutineService)),
    com.cargenome.app.ui.common.FormPreset("Воздушный фильтр", Pair("Замена воздушного фильтра двигателя", ServiceCategory.Engine)),
    com.cargenome.app.ui.common.FormPreset("Салонный фильтр", Pair("Замена салонного фильтра", ServiceCategory.RoutineService)),
    com.cargenome.app.ui.common.FormPreset("Тормозные колодки", Pair("Замена передних тормозных колодок", ServiceCategory.Brakes)),
    com.cargenome.app.ui.common.FormPreset("Свечи зажигания", Pair("Замена свечей зажигания", ServiceCategory.Engine)),
    com.cargenome.app.ui.common.FormPreset("Тормозная жидкость", Pair("Замена тормозной жидкости", ServiceCategory.Brakes)),
    com.cargenome.app.ui.common.FormPreset("Антифриз", Pair("Замена охлаждающей жидкости", ServiceCategory.RoutineService)),
    com.cargenome.app.ui.common.FormPreset("Шиномонтаж", Pair("Сезонный шиномонтаж и балансировка", ServiceCategory.Tyres)),
    com.cargenome.app.ui.common.FormPreset("Диагностика подвески", Pair("Диагностика подвески и ходовой", ServiceCategory.Suspension)),
    com.cargenome.app.ui.common.FormPreset("Сход-развал", Pair("Регулировка сход-развала", ServiceCategory.Suspension)),
    com.cargenome.app.ui.common.FormPreset("Кондиционер", Pair("Заправка кондиционера", ServiceCategory.RoutineService)),
    com.cargenome.app.ui.common.FormPreset("Аккумулятор", Pair("Замена аккумулятора", ServiceCategory.Electrical)),
)

private val ShopPresets = listOf(
    "Своими руками / Гараж",
    "СТО / Автосервис",
    "Официальный дилер",
    "Шиномонтаж",
    "FIT Service",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var confirmDelete by remember { mutableStateOf(false) }

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
                            if (state.isEditing) R.string.service_record_edit_title else R.string.service_record_add_title,
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
                    DateField(
                        value = state.date,
                        onValueChange = viewModel::onDateChanged,
                        label = stringResource(R.string.service_record_date),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    DecimalField(
                        value = state.odometer,
                        onValueChange = viewModel::onOdometerChanged,
                        label = stringResource(R.string.service_record_odometer),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = distanceSuffix,
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.service_record_title)) },
                        placeholder = { Text(stringResource(R.string.service_record_title_hint)) },
                        singleLine = true,
                    )
                }

                item {
                    com.cargenome.app.ui.common.PresetChipsRow(
                        label = stringResource(R.string.presets_services),
                        presets = ServicePresets,
                        onSelect = { (presetTitle, presetCategory) ->
                            viewModel.onTitleChanged(presetTitle)
                            viewModel.onCategoryChanged(presetCategory)
                        },
                    )
                }

                item {
                    ChipSelector(
                        label = stringResource(R.string.service_record_category),
                        options = ServiceCategory.entries,
                        selected = state.category,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelected = viewModel::onCategoryChanged,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DecimalField(
                            value = state.partsCost,
                            onValueChange = viewModel::onPartsCostChanged,
                            label = stringResource(R.string.service_record_parts),
                            modifier = Modifier.weight(1f),
                            suffix = state.currencyCode,
                        )
                        DecimalField(
                            value = state.labourCost,
                            onValueChange = viewModel::onLabourCostChanged,
                            label = stringResource(R.string.service_record_labour),
                            modifier = Modifier.weight(1f),
                            suffix = state.currencyCode,
                        )
                    }
                }

                if (state.totalCostValue > 0) {
                    item {
                        Text(
                            text = "${stringResource(R.string.service_record_total)}: ${
                                Format.money(
                                    (state.totalCostValue * Format.minorScale(state.currencyCode)).toLong(),
                                    state.currencyCode,
                                    locale,
                                )
                            }",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.shop,
                        onValueChange = viewModel::onShopChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.service_record_shop)) },
                        placeholder = { Text(stringResource(R.string.service_record_shop_hint)) },
                        singleLine = true,
                    )
                }

                item {
                    com.cargenome.app.ui.common.PresetStringsRow(
                        label = stringResource(R.string.presets_shops),
                        presets = ShopPresets,
                        selected = state.shop,
                        onSelect = viewModel::onShopChanged,
                    )
                }

                if (state.availableSchedules.isNotEmpty()) {
                    item {
                        SchedulePicker(
                            selectedId = state.selectedScheduleId,
                            available = state.availableSchedules,
                            onSelect = viewModel::onScheduleSelected,
                        )
                    }
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
                            onClick = { confirmDelete = true },
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.record_delete_confirm_title)) },
            text = { Text(stringResource(R.string.record_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulePicker(
    selectedId: Long?,
    available: List<com.cargenome.app.data.db.entity.MaintenanceScheduleEntity>,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSchedule = available.find { it.id == selectedId }
    val displayText = selectedSchedule?.title ?: stringResource(R.string.service_record_schedule_none)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.service_record_schedule)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.service_record_schedule_none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            available.forEach { schedule ->
                DropdownMenuItem(
                    text = { Text(schedule.title) },
                    onClick = {
                        onSelect(schedule.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
