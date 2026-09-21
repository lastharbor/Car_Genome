package com.cargenome.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.cargenome.app.domain.premium.LocalIsPremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.dao.VehicleSummary
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.TagBadge
import com.cargenome.app.ui.common.shortRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: GarageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPremium = LocalIsPremium.current
    var showVehicleLimitDialog by remember { mutableStateOf(false) }

    val handleAddVehicle = {
        if (!isPremium && state.vehicles.isNotEmpty()) {
            showVehicleLimitDialog = true
        } else {
            onAddVehicle()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garage_title)) },
                actions = {
                    if (onOpenSettings != null) {
                        val settingsDesc = stringResource(R.string.settings_title)
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.semantics { contentDescription = settingsDesc },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = settingsDesc,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicles.isNotEmpty()) {
                val addLabel = stringResource(R.string.garage_add)
                ExtendedFloatingActionButton(
                    onClick = handleAddVehicle,
                    modifier = Modifier.semantics { contentDescription = addLabel },
                    text = { Text(addLabel) },
                    icon = { Icon(Icons.Default.Add, contentDescription = addLabel) },
                )
            }
        },
    ) { padding ->
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp + sides.calculateStartPadding(direction),
                    end = 16.dp + sides.calculateEndPadding(direction),
                    top = padding.calculateTopPadding() + 8.dp,
                    // Clear of the FAB, which floats over the last card.
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isLoading && state.vehicles.isEmpty()) {
                    item {
                        EmptyGarage(
                            onAddVehicle = onAddVehicle,
                        )
                    }
                }

                items(
                    items = state.vehicles,
                    key = { it.vehicle.id },
                    contentType = { "vehicle_card" },
                ) { summary ->
                    VehicleCard(
                        summary = summary,
                        isSelected = summary.vehicle.id == state.selectedVehicleId,
                        onClick = { onOpenVehicle(summary.vehicle.id) },
                    )
                }
            }
        }
    }

    if (showVehicleLimitDialog) {
        AlertDialog(
            onDismissRequest = { showVehicleLimitDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.premium_limit_vehicle_title)) },
            text = { Text(stringResource(R.string.premium_limit_vehicle_desc)) },
            confirmButton = {
                if (onOpenSettings != null) {
                    Button(onClick = {
                        onOpenSettings()
                        showVehicleLimitDialog = false
                    }) {
                        Text(stringResource(R.string.premium_btn_to_settings))
                    }
                } else {
                    TextButton(onClick = { showVehicleLimitDialog = false }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVehicleLimitDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyGarage(
    onAddVehicle: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.garage_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.garage_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddVehicle,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.garage_add))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleCard(
    summary: VehicleSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val vehicle = summary.vehicle
    val title = remember(vehicle) {
        vehicle.nickname?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(vehicle.make, vehicle.model).joinToString(" ")
    }
    val subtitle = remember(vehicle) {
        listOfNotNull(
            vehicle.modelYear?.toString(),
            vehicle.nickname?.takeIf { it.isNotBlank() }
                ?.let { listOfNotNull(vehicle.make, vehicle.model).joinToString(" ") },
            vehicle.engine,
        ).filter { it.isNotBlank() }
    }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelected) {
                    TagBadge(
                        text = "✓ " + stringResource(R.string.garage_active_vehicle),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = summary.currentOdometerKm
                        ?.let {
                            stringResource(
                                vehicle.distanceUnit.shortRes(),
                                Format.distance(it, vehicle.distanceUnit, locale),
                            )
                        }
                        ?: stringResource(R.string.garage_no_mileage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (summary.fuelCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.garage_fill_ups,
                            summary.fuelCount,
                            summary.fuelCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.serviceCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.garage_jobs,
                            summary.serviceCount,
                            summary.serviceCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val plate = vehicle.plateNumber?.takeIf { it.isNotBlank() }
            val vin = vehicle.vin?.takeIf { it.isNotBlank() }
            if (plate != null || vin != null) {
                Text(
                    text = listOfNotNull(plate, vin).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
