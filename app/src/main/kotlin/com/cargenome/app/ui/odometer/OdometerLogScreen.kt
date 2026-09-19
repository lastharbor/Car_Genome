package com.cargenome.app.ui.odometer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.OdometerSource
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.ui.common.DateField
import com.cargenome.app.ui.common.DecimalField
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdometerLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OdometerLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.odometer_title)) },
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
        floatingActionButton = {
            val label = stringResource(R.string.odometer_add)
            ExtendedFloatingActionButton(
                onClick = viewModel::openAddDialog,
                modifier = Modifier.semantics { contentDescription = label },
                text = { Text(label) },
                icon = { Icon(Icons.Default.Add, contentDescription = label) },
            )
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
                if (vehicle == null) return@LazyColumn

                if (state.readings.isEmpty()) {
                    if (!state.isLoading) item(key = "empty_odometer") { EmptyOdometerCard() }
                } else {
                    if (state.chronologicalReadings.size >= 2) {
                        item(key = "mileage_chart") {
                            MileageChartCard(
                                readings = state.chronologicalReadings,
                                vehicle = vehicle,
                            )
                        }
                    }

                    items(state.readings, key = { it.id }) { reading ->
                        OdometerRow(
                            reading = reading,
                            vehicle = vehicle,
                            onDelete = if (reading.source == OdometerSource.Manual) {
                                { viewModel.deleteReading(reading) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.isAddDialogOpen && state.vehicle != null) {
        AddReadingDialog(
            vehicle = state.vehicle!!,
            onDismiss = viewModel::closeAddDialog,
            onConfirm = viewModel::addManualReading,
        )
    }
}

@Composable
private fun EmptyOdometerCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.odometer_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.odometer_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MileageChartCard(
    readings: List<OdometerReadingEntity>,
    vehicle: VehicleEntity,
) {
    val locale = LocalConfiguration.current.locales[0]
    val lineColor = MaterialTheme.colorScheme.primary
    val gradientColor = MaterialTheme.colorScheme.primaryContainer

    SectionCard(stringResource(R.string.odometer_chart_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val convertedValues = remember(readings, vehicle.distanceUnit) {
                readings.map { vehicle.distanceUnit.fromKilometres(it.odometerKm) }
            }
            val minVal = convertedValues.minOrNull() ?: 0.0
            val maxVal = convertedValues.maxOrNull() ?: 1.0
            val range = (maxVal - minVal).coerceAtLeast(1.0)

            val minTime = readings.first().recordedAt.toEpochMilli().toDouble()
            val maxTime = readings.last().recordedAt.toEpochMilli().toDouble()
            val timeRange = (maxTime - minTime).coerceAtLeast(1.0)
            val linePath = remember { Path() }
            val fillPath = remember { Path() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                val w = size.width
                val h = size.height
                val padding = 16f
                val chartW = w - padding * 2
                val chartH = h - padding * 2

                val points = readings.mapIndexed { index, reading ->
                    val x = if (minTime == maxTime) {
                        padding + (index.toFloat() / (readings.size - 1).coerceAtLeast(1) * chartW)
                    } else {
                        (padding + ((reading.recordedAt.toEpochMilli() - minTime) / timeRange * chartW).toFloat())
                            .coerceIn(padding, padding + chartW)
                    }
                    val y = (padding + chartH - ((convertedValues[index] - minVal) / range * chartH).toFloat())
                        .coerceIn(padding, padding + chartH)
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    linePath.rewind()
                    linePath.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        linePath.lineTo(points[i].x, points[i].y)
                    }

                    fillPath.rewind()
                    fillPath.addPath(linePath)
                    fillPath.lineTo(points.last().x, h)
                    fillPath.lineTo(points.first().x, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                gradientColor.copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h,
                        ),
                    )

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    points.forEach { pt ->
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = pt,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = Format.date(readings.first().recordedAt, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Format.date(readings.last().recordedAt, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OdometerRow(
    reading: OdometerReadingEntity,
    vehicle: VehicleEntity,
    onDelete: (() -> Unit)?,
) {
    val locale = LocalConfiguration.current.locales[0]

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        vehicle.distanceUnit.shortRes(),
                        Format.distance(reading.odometerKm, vehicle.distanceUnit, locale),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = Format.date(reading.recordedAt, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                reading.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(reading.source.labelRes())) },
                )
            }

            if (onDelete != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddReadingDialog(
    vehicle: VehicleEntity,
    onDismiss: () -> Unit,
    onConfirm: (date: LocalDate, distanceValue: Double, note: String?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var readingText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val distanceSuffix = stringResource(vehicle.distanceUnit.suffixRes())
    val parsedValue = readingText.replace(',', '.').toDoubleOrNull()
    val canSave = parsedValue != null && parsedValue >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.odometer_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.odometer_date),
                    modifier = Modifier.fillMaxWidth(),
                )

                DecimalField(
                    value = readingText,
                    onValueChange = { readingText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = stringResource(R.string.odometer_reading),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = distanceSuffix,
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_notes)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (parsedValue != null && parsedValue >= 0.0) {
                        onConfirm(date, parsedValue, noteText)
                    }
                },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
