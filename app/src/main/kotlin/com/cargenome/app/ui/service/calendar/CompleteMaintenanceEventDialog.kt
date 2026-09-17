package com.cargenome.app.ui.service.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.ui.common.shortRes

@Composable
fun CompleteMaintenanceEventDialog(
    event: MaintenanceEventEntity,
    vehicle: VehicleEntity,
    currentOdometerKm: Double?,
    onDismiss: () -> Unit,
    onConfirm: (
        createRecord: Boolean,
        actualOdometerKm: Double?,
        labourCostMinor: Long,
        partsCostMinor: Long,
        shop: String?,
        notes: String?,
    ) -> Unit,
) {
    var createRecord by remember { mutableStateOf(true) }

    val initialKm = (event.targetOdometerKm ?: currentOdometerKm)?.let {
        vehicle.distanceUnit.fromKilometres(it)
    }
    var odometerText by remember {
        mutableStateOf(initialKm?.let { "%.0f".format(it) }.orEmpty())
    }

    val initialCost = event.estimatedCostMinor?.let {
        val scale = com.cargenome.app.ui.common.Format.minorScale(vehicle.currencyCode)
        (it / scale).let { c -> if (c % 1.0 == 0.0) c.toLong().toString() else c.toString() }
    }
    var partsCostText by remember { mutableStateOf(initialCost.orEmpty()) }
    var labourCostText by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf(event.shop.orEmpty()) }
    var notes by remember { mutableStateOf(event.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.event_dialog_complete_title),
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
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )

                // Checkbox: Create history record
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = createRecord,
                        onCheckedChange = { createRecord = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.event_complete_create_record_switch),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }

                if (createRecord) {
                    Text(
                        text = stringResource(R.string.event_complete_record_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Actual Odometer
                    OutlinedTextField(
                        value = odometerText,
                        onValueChange = { odometerText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = {
                            Text(
                                stringResource(
                                    R.string.event_field_actual_odometer,
                                    stringResource(vehicle.distanceUnit.shortRes(), "").trim(),
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Parts Cost
                    OutlinedTextField(
                        value = partsCostText,
                        onValueChange = { partsCostText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("${stringResource(R.string.service_record_parts)} (${vehicle.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Labour Cost
                    OutlinedTextField(
                        value = labourCostText,
                        onValueChange = { labourCostText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("${stringResource(R.string.service_record_labour)} (${vehicle.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Shop
                    OutlinedTextField(
                        value = shop,
                        onValueChange = { shop = it },
                        label = { Text(stringResource(R.string.service_record_shop)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.field_notes)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odoKm = odometerText.replace(',', '.').toDoubleOrNull()
                        ?.coerceAtLeast(0.0)
                        ?.let { vehicle.distanceUnit.toKilometres(it) }
                    val scale = com.cargenome.app.ui.common.Format.minorScale(vehicle.currencyCode)
                    val partsMinor = partsCostText.replace(',', '.').toDoubleOrNull()
                        ?.coerceAtLeast(0.0)
                        ?.let { kotlin.math.round(it * scale).toLong() } ?: 0L
                    val labourMinor = labourCostText.replace(',', '.').toDoubleOrNull()
                        ?.coerceAtLeast(0.0)
                        ?.let { kotlin.math.round(it * scale).toLong() } ?: 0L
                    onConfirm(
                        createRecord,
                        odoKm,
                        labourMinor,
                        partsMinor,
                        shop.trim().takeIf { it.isNotBlank() },
                        notes.trim().takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text(stringResource(R.string.event_action_confirm_complete))
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
