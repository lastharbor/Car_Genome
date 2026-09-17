package com.cargenome.app.ui.service.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.ServiceCategory
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.ui.common.DateField
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.shortRes
import java.time.LocalDate

private val MaintenanceEventPresets = listOf(
    "Замена масла и фильтра" to ServiceCategory.RoutineService,
    "Тормозные колодки" to ServiceCategory.Brakes,
    "Шиномонтаж" to ServiceCategory.Tyres,
    "Свечи зажигания" to ServiceCategory.Engine,
    "Антифриз" to ServiceCategory.RoutineService,
    "Тормозная жидкость" to ServiceCategory.Brakes,
    "Фильтр салона" to ServiceCategory.RoutineService,
    "Диагностика подвески" to ServiceCategory.Suspension,
    "Техосмотр" to ServiceCategory.Other,
)

private val ReminderAdvanceOptions = listOf(
    0 to R.string.event_reminder_opt_day,
    1 to R.string.event_reminder_opt_1_day,
    3 to R.string.event_reminder_opt_3_days,
    7 to R.string.event_reminder_opt_7_days,
    -1 to R.string.event_reminder_opt_none,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceEventDialog(
    initialDate: LocalDate,
    vehicle: VehicleEntity,
    eventToEdit: MaintenanceEventEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: ServiceCategory,
        scheduledDate: LocalDate,
        scheduledTimeMinutes: Int?,
        targetOdometerKm: Double?,
        estimatedCostMinor: Long?,
        shop: String?,
        notes: String?,
        remindAdvanceDays: Int,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(eventToEdit?.title.orEmpty()) }
    var category by remember { mutableStateOf(eventToEdit?.category ?: ServiceCategory.RoutineService) }
    var scheduledDate by remember { mutableStateOf(eventToEdit?.scheduledDate ?: initialDate) }

    val defaultFutureTime = remember { java.time.LocalTime.now().plusMinutes(5) }
    val hasInitialTime = eventToEdit?.scheduledTimeMinutes != null
    var specifyTime by remember { mutableStateOf(hasInitialTime) }
    val initialHour = eventToEdit?.scheduledTimeMinutes?.let { it / 60 } ?: defaultFutureTime.hour
    val initialMinute = eventToEdit?.scheduledTimeMinutes?.let { it % 60 } ?: defaultFutureTime.minute
    var hourText by remember { mutableStateOf("%02d".format(initialHour)) }
    var minuteText by remember { mutableStateOf("%02d".format(initialMinute)) }

    var odometerText by remember {
        mutableStateOf(
            eventToEdit?.targetOdometerKm
                ?.let { vehicle.distanceUnit.fromKilometres(it) }
                ?.let { "%.0f".format(it) }
                .orEmpty(),
        )
    }
    val scale = Format.minorScale(vehicle.currencyCode)
    var costText by remember {
        mutableStateOf(
            eventToEdit?.estimatedCostMinor
                ?.let { (it / scale).let { c -> if (c % 1.0 == 0.0) c.toLong().toString() else c.toString() } }
                .orEmpty(),
        )
    }
    var shop by remember { mutableStateOf(eventToEdit?.shop.orEmpty()) }
    var notes by remember { mutableStateOf(eventToEdit?.notes.orEmpty()) }
    var remindAdvanceDays by remember { mutableIntStateOf(eventToEdit?.remindAdvanceDays ?: 0) }

    var categoryExpanded by remember { mutableStateOf(false) }

    val isEditing = eventToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.event_dialog_edit_title else R.string.event_dialog_add_title,
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Quick presets
                Text(
                    text = stringResource(R.string.event_presets_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MaintenanceEventPresets.forEach { (presetTitle, presetCat) ->
                        SuggestionChip(
                            onClick = {
                                title = presetTitle
                                category = presetCat
                            },
                            label = { Text(presetTitle, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.event_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(category.labelRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.service_record_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        ServiceCategory.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(stringResource(entry.labelRes())) },
                                onClick = {
                                    category = entry
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }

                // Date field
                DateField(
                    value = scheduledDate,
                    onValueChange = { scheduledDate = it },
                    label = stringResource(R.string.event_field_date),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Time specification toggle & inputs
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = specifyTime,
                            onCheckedChange = { specifyTime = it },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.event_specify_time),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!specifyTime) {
                        Text(
                            text = stringResource(R.string.event_default_time_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }

                if (specifyTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { str ->
                                hourText = str.filter { it.isDigit() }.take(2)
                            },
                            label = { Text(stringResource(R.string.event_time_hours)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { str ->
                                minuteText = str.filter { it.isDigit() }.take(2)
                            },
                            label = { Text(stringResource(R.string.event_time_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Target Odometer
                OutlinedTextField(
                    value = odometerText,
                    onValueChange = { odometerText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = {
                        Text(
                            stringResource(
                                R.string.event_target_odometer_hint,
                                stringResource(vehicle.distanceUnit.shortRes(), "").trim(),
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Estimated Cost
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.event_estimated_cost_hint, vehicle.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Workshop / Shop
                OutlinedTextField(
                    value = shop,
                    onValueChange = { shop = it },
                    label = { Text(stringResource(R.string.event_field_shop)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Reminder selector
                Text(
                    text = stringResource(R.string.event_reminder_select_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReminderAdvanceOptions.forEach { (days, labelRes) ->
                        FilterChip(
                            selected = remindAdvanceDays == days,
                            onClick = { remindAdvanceDays = days },
                            label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // Notes / Checklist
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.field_notes)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val parsedHour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 10
                        val parsedMinute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val timeMinutes = if (specifyTime) (parsedHour * 60 + parsedMinute) else null
                        val odoKm = odometerText.replace(',', '.').toDoubleOrNull()
                            ?.coerceAtLeast(0.0)
                            ?.let { vehicle.distanceUnit.toKilometres(it) }
                        val costMinor = costText.replace(',', '.').toDoubleOrNull()
                            ?.coerceAtLeast(0.0)
                            ?.let { kotlin.math.round(it * scale).toLong() }
                        onSave(
                            title.trim(),
                            category,
                            scheduledDate,
                            timeMinutes,
                            odoKm,
                            costMinor,
                            shop.trim().takeIf { it.isNotBlank() },
                            notes.trim().takeIf { it.isNotBlank() },
                            remindAdvanceDays,
                        )
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}
