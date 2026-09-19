package com.cargenome.app.ui.fuel

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.fuel.FuelSegment
import com.cargenome.app.domain.fuel.FuelStatistics
import com.cargenome.app.domain.model.ConsumptionUnit
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.EmptyVehiclesTabCard
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.TagBadge
import com.cargenome.app.ui.common.displayName
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelLogScreen(
    onAddRecord: (Long) -> Unit,
    onOpenRecord: (vehicleId: Long, recordId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAddVehicle: (() -> Unit)? = null,
    viewModel: FuelLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.fuel_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        state.vehicle?.displayName()?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
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
        floatingActionButton = {
            val currentVehicle = state.vehicle
            if (currentVehicle != null) {
                val label = stringResource(R.string.fuel_add)
                ExtendedFloatingActionButton(
                    onClick = { onAddRecord(currentVehicle.id) },
                    modifier = Modifier.semantics { contentDescription = label },
                    text = { Text(label) },
                    icon = { Icon(Icons.Default.Add, contentDescription = label) },
                )
            }
        },
    ) { padding ->
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current
        val vehicle = state.vehicle

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                if (vehicle == null) {
                    if (!state.isLoading) {
                        item(key = "empty_vehicles") { EmptyVehiclesTabCard(onAddVehicle = onAddVehicle) }
                    }
                    return@LazyColumn
                }

                if (state.records.isEmpty()) {
                    if (!state.isLoading) item(key = "empty_fuel_log") { EmptyLog() }
                } else {
                    item(key = "fuel_summary") { SummaryCard(state.statistics, vehicle) }
                }

                items(
                    items = state.records,
                    key = { it.id },
                    contentType = { "fuel_record" },
                ) { record ->
                    FuelRow(
                        record = record,
                        segment = state.consumptionByRecord[record.id],
                        vehicle = vehicle,
                        onClick = { onOpenRecord(vehicle.id, record.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLog() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.fuel_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.fuel_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(statistics: FuelStatistics, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val unit = vehicle.consumptionUnit()
    val unknown = stringResource(R.string.vin_unknown)

    SectionCard(stringResource(R.string.fuel_summary)) {
        DetailRow(
            label = stringResource(R.string.fuel_average),
            value = statistics.averageLitresPer100Km.asConsumption(unit, locale) ?: unknown,
            hint = if (statistics.segments.isEmpty()) {
                stringResource(R.string.fuel_needs_two_full_tanks)
            } else {
                null
            },
        )
        statistics.bestLitresPer100Km.asConsumption(unit, locale)?.let {
            DetailRow(stringResource(R.string.fuel_best), it)
        }
        statistics.worstLitresPer100Km.asConsumption(unit, locale)?.let {
            DetailRow(stringResource(R.string.fuel_worst), it)
        }
        statistics.costPerKmMinor?.let { perKm ->
            DetailRow(
                label = stringResource(R.string.fuel_cost_per_distance),
                value = Format.money(
                    minorUnits = (perKm * vehicle.distanceUnit.kilometresPerUnit).toLong(),
                    currencyCode = vehicle.currencyCode,
                    locale = locale,
                ) + "/" + stringResource(vehicle.distanceUnit.suffixRes()),
            )
        }
        DetailRow(
            label = stringResource(R.string.fuel_total_spent),
            value = Format.money(statistics.loggedCostMinor, vehicle.currencyCode, locale),
        )
        DetailRow(
            label = stringResource(R.string.fuel_total_volume),
            value = stringResource(
                vehicle.volumeUnit.shortRes(),
                Format.volume(statistics.loggedLitres, vehicle.volumeUnit, locale),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelRow(
    record: FuelRecordEntity,
    segment: FuelSegment?,
    vehicle: VehicleEntity,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val unit = vehicle.consumptionUnit()

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Format.date(record.filledAt, locale),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Format.money(record.totalCostMinor, vehicle.currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = listOf(
                    stringResource(
                        vehicle.volumeUnit.shortRes(),
                        Format.volume(record.volumeLitres, vehicle.volumeUnit, locale),
                    ),
                    stringResource(
                        vehicle.distanceUnit.shortRes(),
                        Format.distance(record.odometerKm, vehicle.distanceUnit, locale),
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            record.station?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val consumptionVal = segment?.litresPer100Km
                consumptionVal.asConsumption(unit, locale)?.let { value ->
                    TagBadge(text = value)
                }
                if (!record.isFullTank) {
                    TagBadge(text = stringResource(R.string.fuel_partial))
                }
                if (record.missedPreviousFillUp) {
                    TagBadge(text = stringResource(R.string.fuel_missed))
                }
            }
        }
    }
}

/**
 * Litres per 100 km for metric cars, miles per gallon for the rest: a figure in
 * the wrong unit is worse than no figure at all.
 */
private fun VehicleEntity.consumptionUnit(): ConsumptionUnit = when {
    distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.UsGallons ->
        ConsumptionUnit.MilesPerUsGallon
    distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.ImperialGallons ->
        ConsumptionUnit.MilesPerImperialGallon
    else -> ConsumptionUnit.LitresPer100Km
}

@Composable
private fun Double?.asConsumption(unit: ConsumptionUnit, locale: Locale): String? {
    val converted = this?.let(unit::fromLitresPer100Km) ?: return null
    return "${Format.consumption(converted, locale)} ${stringResource(unit.shortRes())}"
}
