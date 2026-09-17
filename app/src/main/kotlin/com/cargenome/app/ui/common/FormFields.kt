package com.cargenome.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cargenome.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        singleLine = true,
    )
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A read-only field that opens the platform date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = Format.date(value, locale),
        onValueChange = {},
        modifier = modifier,
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.action_pick))
            }
        },
        singleLine = true,
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The picker speaks in UTC midnights; reading it back in
                        // any other zone would shift the date by a day.
                        pickerState.selectedDateMillis?.let {
                            onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(pickerState)
        }
    }
}

/** A read-only field for nullable dates that opens the platform date picker and supports clearing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalDateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    canClear: Boolean = true,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value?.let { Format.date(it, locale) }.orEmpty(),
        onValueChange = {},
        modifier = modifier,
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            Row {
                if (canClear && value != null) {
                    TextButton(onClick = { onValueChange(null) }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
                TextButton(onClick = { showPicker = true }) {
                    Text(stringResource(R.string.action_pick))
                }
            }
        },
        singleLine = true,
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(pickerState)
        }
    }
}

@Composable
fun <T> ChipSelector(
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

data class FormPreset<T>(
    val label: String,
    val value: T,
)

@Composable
fun <T> PresetChipsRow(
    presets: List<FormPreset<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    selected: T? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(presets) { preset ->
                val isSelected = selected != null && selected == preset.value
                SuggestionChip(
                    onClick = { onSelect(preset.value) },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                    colors = if (isSelected) {
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        SuggestionChipDefaults.suggestionChipColors()
                    },
                )
            }
        }
    }
}

@Composable
fun PresetStringsRow(
    presets: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    selected: String? = null,
) {
    PresetChipsRow(
        presets = presets.map { FormPreset(it, it) },
        onSelect = onSelect,
        modifier = modifier,
        label = label,
        selected = selected,
    )
}

