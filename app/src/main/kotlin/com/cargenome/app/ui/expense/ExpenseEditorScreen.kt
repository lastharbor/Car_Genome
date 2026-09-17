package com.cargenome.app.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.ui.common.ChipSelector
import com.cargenome.app.ui.common.DateField
import com.cargenome.app.ui.common.DecimalField
import com.cargenome.app.ui.common.ReceiptAttachmentSection
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.suffixRes

private val ExpensePresets = listOf(
    com.cargenome.app.ui.common.FormPreset("Мойка комплекс", Pair("Комплексная мойка и салон", ExpenseCategory.Wash)),
    com.cargenome.app.ui.common.FormPreset("Самомойка", Pair("Мойка самообслуживания", ExpenseCategory.Wash)),
    com.cargenome.app.ui.common.FormPreset("Парковка", Pair("Городская парковка", ExpenseCategory.Parking)),
    com.cargenome.app.ui.common.FormPreset("Платная дорога", Pair("Оплата проезда по платной трассе", ExpenseCategory.Toll)),
    com.cargenome.app.ui.common.FormPreset("ОСАГО", Pair("Полис ОСАГО", ExpenseCategory.Insurance)),
    com.cargenome.app.ui.common.FormPreset("КАСКО", Pair("Полис КАСКО", ExpenseCategory.Insurance)),
    com.cargenome.app.ui.common.FormPreset("Транспортный налог", Pair("Транспортный налог", ExpenseCategory.Tax)),
    com.cargenome.app.ui.common.FormPreset("Штраф ГИБДД", Pair("Оплата штрафа ГИБДД", ExpenseCategory.Fine)),
    com.cargenome.app.ui.common.FormPreset("Омывайка / химия", Pair("Омывающая жидкость и автохимия", ExpenseCategory.Accessories)),
    com.cargenome.app.ui.common.FormPreset("Автотовары", Pair("Аксессуары и автотовары", ExpenseCategory.Accessories)),
    com.cargenome.app.ui.common.FormPreset("Автокредит", Pair("Платеж по автокредиту", ExpenseCategory.Credit)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseEditorViewModel = hiltViewModel(),
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
                            if (state.isEditing) R.string.expense_edit_title else R.string.expense_add_title,
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
        val distanceSuffix = stringResource(state.distanceUnit.suffixRes())

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp + sides.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    end = 16.dp + sides.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    DateField(
                        value = state.date,
                        onValueChange = viewModel::onDateChanged,
                        label = stringResource(R.string.expense_date),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.expense_title_label)) },
                        placeholder = { Text(stringResource(R.string.expense_title_hint)) },
                        singleLine = true,
                    )
                }

                item {
                    com.cargenome.app.ui.common.PresetChipsRow(
                        label = stringResource(R.string.presets_expenses),
                        presets = ExpensePresets,
                        onSelect = { (presetTitle, presetCategory) ->
                            viewModel.onTitleChanged(presetTitle)
                            viewModel.onCategoryChanged(presetCategory)
                        },
                    )
                }

                item {
                    ChipSelector(
                        label = stringResource(R.string.expense_category),
                        options = ExpenseCategory.entries,
                        selected = state.category,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelected = viewModel::onCategoryChanged,
                    )
                }

                item {
                    DecimalField(
                        value = state.amount,
                        onValueChange = viewModel::onAmountChanged,
                        label = stringResource(R.string.expense_amount),
                        modifier = Modifier.fillMaxWidth(),
                        suffix = state.currencyCode,
                    )
                }

                item {
                    DecimalField(
                        value = state.odometer,
                        onValueChange = viewModel::onOdometerChanged,
                        label = stringResource(R.string.expense_odometer),
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
