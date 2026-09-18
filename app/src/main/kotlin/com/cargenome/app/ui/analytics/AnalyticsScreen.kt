package com.cargenome.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.analytics.CategorySpend
import com.cargenome.app.domain.analytics.ConsumptionPoint
import com.cargenome.app.domain.analytics.MonthlySpend
import com.cargenome.app.domain.analytics.VehicleAnalyticsData
import com.cargenome.app.domain.model.ConsumptionUnit
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.EmptyVehiclesTabCard
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.displayName
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes
import java.util.Locale
import kotlin.math.roundToInt

private val CategoryColors = listOf(
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF607D8B), // Blue Grey
    Color(0xFF795548), // Brown
    Color(0xFFFFEB3B), // Yellow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAddVehicle: (() -> Unit)? = null,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.analytics_title),
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
    ) { padding ->
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current
        val vehicle = state.vehicle
        val data = state.data

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
                        item { EmptyVehiclesTabCard(onAddVehicle = onAddVehicle) }
                    }
                    return@LazyColumn
                }

                if (data.totalSpendMinor == 0L && data.trackedDistanceKm == 0.0) {
                    if (!state.isLoading) item { EmptyAnalyticsCard() }
                } else {
                    item { CostOverviewCard(data, vehicle) }

                    if (data.categorySpends.isNotEmpty()) {
                        item { CategorySpendCard(data.categorySpends, vehicle) }
                    }

                    if (data.monthlySpends.size >= 2) {
                        item { MonthlySpendCard(data.monthlySpends, vehicle) }
                    }

                    if (data.consumptionHistory.size >= 2) {
                        item { ConsumptionTrendCard(data.consumptionHistory, vehicle, data.averageConsumption) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAnalyticsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.analytics_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.analytics_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CostOverviewCard(data: VehicleAnalyticsData, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val distSuffix = stringResource(vehicle.distanceUnit.suffixRes())
    val consUnit = vehicle.consumptionUnit()

    SectionCard(stringResource(R.string.analytics_total_cost)) {
        DetailRow(
            label = stringResource(R.string.analytics_total_cost),
            value = Format.money(data.totalSpendMinor, vehicle.currencyCode, locale),
        )

        data.totalCostPerKmMinor?.let { perKm ->
            val perDist = (perKm * vehicle.distanceUnit.kilometresPerUnit).toLong()
            DetailRow(
                label = stringResource(R.string.analytics_cost_per_km),
                value = "${Format.money(perDist, vehicle.currencyCode, locale)} / $distSuffix",
            )
        }

        data.fuelCostPerKmMinor?.let { perKm ->
            val perDist = (perKm * vehicle.distanceUnit.kilometresPerUnit).toLong()
            DetailRow(
                label = stringResource(R.string.analytics_fuel_cost_per_km),
                value = "${Format.money(perDist, vehicle.currencyCode, locale)} / $distSuffix",
            )
        }

        if (data.trackedDistanceKm > 0) {
            DetailRow(
                label = stringResource(R.string.analytics_distance_tracked),
                value = stringResource(
                    vehicle.distanceUnit.shortRes(),
                    Format.distance(data.trackedDistanceKm, vehicle.distanceUnit, locale),
                ),
            )
        }

        data.averageConsumption?.let { avg ->
            DetailRow(
                label = stringResource(R.string.fuel_average),
                value = "${Format.consumption(avg, locale)} ${stringResource(consUnit.shortRes())}",
            )
        }
    }
}

@Composable
private fun CategorySpendCard(categories: List<CategorySpend>, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]

    SectionCard(stringResource(R.string.analytics_category_breakdown)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Donut chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(8.dp),
            ) {
                val strokeWidth = 22.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                var startAngle = -90f
                categories.forEachIndexed { index, cat ->
                    val sweepAngle = cat.percentage * 3.6f
                    val color = CategoryColors[index % CategoryColors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += sweepAngle
                }
            }

            // Category legend
            categories.forEachIndexed { index, cat ->
                val color = CategoryColors[index % CategoryColors.size]
                val label = categoryDisplayName(cat.key)
                val money = Format.money(cat.amountMinor, vehicle.currencyCode, locale)
                val pct = "${cat.percentage.roundToInt()}%"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "$money ($pct)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlySpendCard(monthlySpends: List<MonthlySpend>, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val maxSpend = remember(monthlySpends) { monthlySpends.maxOf { it.amountMinor }.coerceAtLeast(1L) }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = textColor)

    SectionCard(stringResource(R.string.analytics_monthly_spend)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 12.dp, bottom = 4.dp),
            ) {
                val yAxisWidth = 60.dp.toPx()
                val chartWidth = size.width - yAxisWidth
                val chartHeight = size.height - 12.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

                // Y-axis ticks (top, mid, bottom)
                val ticks = listOf(
                    0f to maxSpend,
                    chartHeight / 2f to maxSpend / 2L,
                    chartHeight to 0L,
                )

                ticks.forEach { (y, amount) ->
                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                    val label = Format.money(amount, vehicle.currencyCode, locale)
                    val measured = textMeasurer.measure(label, textStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset((yAxisWidth - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f), (y - measured.size.height / 2f).coerceAtLeast(0f)),
                    )
                }

                val barCount = monthlySpends.size
                val spacing = (chartWidth / (barCount * 4 + 1)).coerceIn(2.dp.toPx(), 8.dp.toPx())
                val barWidth = ((chartWidth - (barCount + 1) * spacing) / barCount).coerceAtLeast(2.dp.toPx())

                monthlySpends.forEachIndexed { index, item ->
                    val x = yAxisWidth + spacing + index * (barWidth + spacing)
                    val barHeight = (item.amountMinor.toDouble() / maxSpend * chartHeight).toFloat().coerceIn(0f, chartHeight)
                    val y = chartHeight - barHeight

                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val first = monthlySpends.first().yearMonth
                val last = monthlySpends.last().yearMonth
                Text(
                    text = "${first.monthValue}/${first.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${last.monthValue}/${last.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConsumptionTrendCard(
    history: List<ConsumptionPoint>,
    vehicle: VehicleEntity,
    average: Double?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val unit = vehicle.consumptionUnit()
    val lineColor = MaterialTheme.colorScheme.primary
    val avgLineColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = textColor)

    SectionCard("${stringResource(R.string.analytics_consumption_trend)} (${stringResource(unit.shortRes())})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (minVal, maxVal, range) = remember(history, average) {
                val values = history.map { it.consumptionValue }
                val allValues = if (average != null) values + average else values
                val min = allValues.minOrNull() ?: 0.0
                val max = allValues.maxOrNull() ?: 1.0
                Triple(min, max, (max - min).coerceAtLeast(0.5))
            }
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(8.dp),
            ) {
                val yAxisWidth = 44.dp.toPx()
                val pad = 12f
                val chartW = size.width - yAxisWidth - pad * 2
                val chartH = size.height - pad * 2

                val midVal = (minVal + maxVal) / 2.0
                val ticks = listOf(
                    pad to maxVal,
                    pad + chartH / 2f to midVal,
                    pad + chartH to minVal,
                )

                ticks.forEach { (y, valNum) ->
                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                    val label = Format.consumption(valNum, locale)
                    val measured = textMeasurer.measure(label, textStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset((yAxisWidth - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f), (y - measured.size.height / 2f).coerceAtLeast(0f)),
                    )
                }

                val points = history.mapIndexed { i, point ->
                    val x = if (history.size <= 1) {
                        yAxisWidth + pad + chartW / 2f
                    } else {
                        yAxisWidth + pad + (i.toFloat() / (history.size - 1) * chartW)
                    }
                    val y = (pad + chartH - ((point.consumptionValue - minVal) / range * chartH).toFloat())
                        .coerceIn(pad, pad + chartH)
                    Offset(x, y)
                }

                // Average guideline
                if (average != null) {
                    val avgY = (pad + chartH - ((average - minVal) / range * chartH).toFloat())
                        .coerceIn(pad, pad + chartH)
                    drawLine(
                        color = avgLineColor.copy(alpha = 0.6f),
                        start = Offset(yAxisWidth + pad, avgY),
                        end = Offset(yAxisWidth + pad + chartW, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                }

                // Line connecting points
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }

                points.forEach { pt ->
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = pt)
                }
            }

            if (history.isNotEmpty()) {
                val firstDate = Format.date(history.first().date, locale)
                val lastDate = Format.date(history.last().date, locale)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = firstDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (firstDate != lastDate) {
                        Text(
                            text = lastDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            average?.let {
                Row(
                    modifier = Modifier.padding(start = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(modifier = Modifier.size(width = 16.dp, height = 2.dp)) {
                        drawLine(
                            color = avgLineColor,
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.fuel_average)}: ${Format.consumption(it, locale)} ${stringResource(unit.shortRes())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryDisplayName(key: String): String = when (key.lowercase()) {
    "fuel" -> stringResource(R.string.analytics_fuel_category)
    "service" -> stringResource(R.string.analytics_service_category)
    "insurance" -> stringResource(R.string.expense_category_insurance)
    "tax" -> stringResource(R.string.expense_category_tax)
    "tyres" -> stringResource(R.string.expense_category_tyres)
    "fine" -> stringResource(R.string.expense_category_fine)
    "wash" -> stringResource(R.string.expense_category_wash)
    "parking" -> stringResource(R.string.expense_category_parking)
    "toll" -> stringResource(R.string.expense_category_toll)
    "registration" -> stringResource(R.string.expense_category_registration)
    "accessories" -> stringResource(R.string.expense_category_accessories)
    "credit" -> stringResource(R.string.expense_category_credit)
    else -> stringResource(R.string.expense_category_other)
}

private fun VehicleEntity.consumptionUnit(): ConsumptionUnit = when {
    distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.UsGallons ->
        ConsumptionUnit.MilesPerUsGallon
    distanceUnit == DistanceUnit.Miles && volumeUnit == VolumeUnit.ImperialGallons ->
        ConsumptionUnit.MilesPerImperialGallon
    else -> ConsumptionUnit.LitresPer100Km
}
