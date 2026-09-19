package com.cargenome.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.analytics.AnalyticsTimeRange
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
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
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
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp),
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

                item(key = "time_range") {
                    TimeRangeSelector(
                        selectedRange = state.selectedTimeRange,
                        onSelectRange = viewModel::setTimeRange,
                    )
                }

                if (data.totalSpendMinor == 0L && data.trackedDistanceKm == 0.0) {
                    if (!state.isLoading) item(key = "empty") { EmptyAnalyticsCard() }
                } else {
                    item(key = "cost_overview") { CostOverviewCard(data, vehicle) }

                    if (data.categorySpends.isNotEmpty()) {
                        item(key = "category_spends") { CategorySpendCard(data.categorySpends, vehicle, data.totalSpendMinor, data.totalEntriesCount) }
                    }

                    if (data.monthlySpends.isNotEmpty()) {
                        item(key = "monthly_spends") { MonthlySpendCard(data.monthlySpends, vehicle) }
                    }

                    if (data.consumptionHistory.size >= 2) {
                        item(key = "consumption_trend") { ConsumptionTrendCard(data.consumptionHistory, vehicle, data.averageConsumption) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: AnalyticsTimeRange,
    onSelectRange: (AnalyticsTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnalyticsTimeRange.entries.forEach { range ->
            val label = when (range) {
                AnalyticsTimeRange.ALL_TIME -> stringResource(R.string.analytics_range_all)
                AnalyticsTimeRange.YEAR_1 -> stringResource(R.string.analytics_range_year)
                AnalyticsTimeRange.MONTHS_6 -> stringResource(R.string.analytics_range_6m)
                AnalyticsTimeRange.MONTHS_3 -> stringResource(R.string.analytics_range_3m)
            }
            val selected = range == selectedRange
            FilterChip(
                selected = selected,
                onClick = { onSelectRange(range) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
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

        if (data.averageMonthlySpendMinor > 0L) {
            DetailRow(
                label = stringResource(R.string.analytics_avg_monthly),
                value = Format.money(data.averageMonthlySpendMinor, vehicle.currencyCode, locale),
            )
        }

        data.costPerDayMinor?.let { perDay ->
            DetailRow(
                label = stringResource(R.string.analytics_cost_per_day),
                value = "${Format.money(perDay, vehicle.currencyCode, locale)} / день",
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
private fun CategorySpendCard(
    categories: List<CategorySpend>,
    vehicle: VehicleEntity,
    totalSpendMinor: Long,
    totalEntriesCount: Int = 0,
) {
    val locale = LocalConfiguration.current.locales[0]
    var selectedCategoryKey by remember(categories) { mutableStateOf<String?>(null) }
    val selectedCat = remember(categories, selectedCategoryKey) {
        categories.find { it.key == selectedCategoryKey }
    }

    SectionCard(stringResource(R.string.analytics_category_breakdown)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Interactive Donut chart with centered summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(categories) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    val offset = up.position
                                    val defaultStroke = 24.dp.toPx()
                                    val minDim = kotlin.math.min(size.width, size.height).toFloat()
                                    val radius = (minDim - defaultStroke - 8.dp.toPx()) / 2f
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = offset.x - center.x
                                    val dy = offset.y - center.y
                                    val dist = hypot(dx, dy)

                                    if (dist in (radius - defaultStroke)..(radius + defaultStroke)) {
                                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                        if (angle < 0f) angle += 360f
                                        var currentAngle = 0f
                                        var tappedKey: String? = null
                                        for (cat in categories) {
                                            val sweep = cat.percentage * 3.6f
                                            if (angle >= currentAngle && angle <= currentAngle + sweep) {
                                                tappedKey = cat.key
                                                break
                                            }
                                            currentAngle += sweep
                                        }
                                        selectedCategoryKey = if (selectedCategoryKey == tappedKey) null else tappedKey
                                    } else if (dist < radius - defaultStroke) {
                                        // Tap center to reset
                                        selectedCategoryKey = null
                                    }
                                }
                            }
                        },
                ) {
                    val baseStroke = 22.dp.toPx()
                    val selectedStroke = 30.dp.toPx()
                    val radius = (size.minDimension - selectedStroke - 4.dp.toPx()) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    var startAngle = -90f
                    categories.forEachIndexed { index, cat ->
                        val sweepAngle = cat.percentage * 3.6f
                        val isSelected = selectedCategoryKey == cat.key
                        val hasAnySelection = selectedCategoryKey != null
                        val rawColor = CategoryColors[index % CategoryColors.size]
                        val color = if (hasAnySelection && !isSelected) {
                            rawColor.copy(alpha = 0.35f)
                        } else {
                            rawColor
                        }
                        val stroke = if (isSelected) selectedStroke else baseStroke

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                        startAngle += sweepAngle
                    }
                }

                // Center information text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { selectedCategoryKey = null }
                        .padding(12.dp),
                ) {
                    if (selectedCat != null) {
                        Text(
                            text = categoryDisplayName(selectedCat.key),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Format.money(selectedCat.amountMinor, vehicle.currencyCode, locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${selectedCat.percentage.roundToInt()}% (${selectedCat.count})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.analytics_total_cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = Format.money(totalSpendMinor, vehicle.currencyCode, locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (totalEntriesCount > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.analytics_entries_count, totalEntriesCount, totalEntriesCount),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Category legend (clickable to highlight / inspect)
            categories.forEachIndexed { index, cat ->
                val color = CategoryColors[index % CategoryColors.size]
                val label = categoryDisplayName(cat.key)
                val money = Format.money(cat.amountMinor, vehicle.currencyCode, locale)
                val pct = "${cat.percentage.roundToInt()}%"
                val isSelected = selectedCategoryKey == cat.key

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedCategoryKey = if (selectedCategoryKey == cat.key) null else cat.key
                        },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 14.dp else 10.dp)
                                    .background(color, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = if (isSelected) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (cat.count > 0) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "(${cat.count})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(
                            text = "$money ($pct)",
                            style = if (isSelected) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySpendCard(monthlySpends: List<MonthlySpend>, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val maxSpend = remember(monthlySpends) { monthlySpends.maxOf { it.amountMinor }.coerceAtLeast(1L) }
    var selectedMonthIndex by remember(monthlySpends) { mutableStateOf<Int?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val barColorDimmed = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
    val maxLabel = remember(maxSpend, vehicle.currencyCode, locale) { Format.money(maxSpend, vehicle.currencyCode, locale) }
    val midLabel = remember(maxSpend, vehicle.currencyCode, locale) { Format.money(maxSpend / 2L, vehicle.currencyCode, locale) }
    val zeroLabel = remember(vehicle.currencyCode, locale) { Format.money(0L, vehicle.currencyCode, locale) }

    SectionCard(stringResource(R.string.analytics_monthly_spend)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = maxLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = textColor,
                        maxLines = 1,
                    )
                    Text(
                        text = midLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = textColor,
                        maxLines = 1,
                    )
                    Text(
                        text = zeroLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = textColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(monthlySpends) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    val offset = up.position
                                    val chartWidth = size.width.toFloat()
                                    val barCount = monthlySpends.size
                                    val spacing = (chartWidth / (barCount * 4 + 1)).coerceIn(2.dp.toPx(), 8.dp.toPx())
                                    val barWidth = ((chartWidth - (barCount + 1) * spacing) / barCount).coerceAtLeast(2.dp.toPx())

                                    var hitIdx: Int? = null
                                    monthlySpends.forEachIndexed { idx, _ ->
                                        val x = spacing + idx * (barWidth + spacing)
                                        if (offset.x in (x - spacing / 2f)..(x + barWidth + spacing / 2f)) {
                                            hitIdx = idx
                                        }
                                    }
                                    selectedMonthIndex = if (selectedMonthIndex == hitIdx) null else hitIdx
                                }
                            }
                        },
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height - 12.dp.toPx()

                    // Y-axis ticks
                    val yPositions = floatArrayOf(0f, chartHeight / 2f, chartHeight)
                    for (y in yPositions) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }

                    val barCount = monthlySpends.size
                    val spacing = (chartWidth / (barCount * 4 + 1)).coerceIn(2.dp.toPx(), 8.dp.toPx())
                    val barWidth = ((chartWidth - (barCount + 1) * spacing) / barCount).coerceAtLeast(2.dp.toPx())
                    val barCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    val dotRadius = 3.dp.toPx()
                    val dotOffset = 5.dp.toPx()

                    monthlySpends.forEachIndexed { index, item ->
                        val x = spacing + index * (barWidth + spacing)
                        val barHeight = (item.amountMinor.toDouble() / maxSpend * chartHeight).toFloat().coerceIn(0f, chartHeight)
                        val y = chartHeight - barHeight
                        val isSelected = selectedMonthIndex == index
                        val hasSelection = selectedMonthIndex != null

                        val fill = when {
                            isSelected -> highlightColor
                            hasSelection -> barColorDimmed
                            else -> barColor
                        }

                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = barCornerRadius,
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = highlightColor,
                                    radius = dotRadius,
                                    center = Offset(x + barWidth / 2f, (y - dotOffset).coerceAtLeast(dotRadius)),
                                )
                            }
                        }
                    }
                }
            }

            // Month labels (first and last)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 66.dp),
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

            // Interactive inspection detail banner
            val selectedItem = selectedMonthIndex?.let { monthlySpends.getOrNull(it) }
            if (selectedItem != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMonthIndex = null },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val monthTitle = remember(selectedItem.yearMonth, locale) {
                            try {
                                DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(selectedItem.yearMonth)
                                    .replaceFirstChar { it.titlecase(locale) }
                            } catch (_: Exception) {
                                "${selectedItem.yearMonth.monthValue}/${selectedItem.yearMonth.year}"
                            }
                        }
                        Column {
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${Format.money(selectedItem.amountMinor, vehicle.currencyCode, locale)} • ${selectedItem.percentageOfTotal.roundToInt()}% • ${pluralStringResource(R.plurals.analytics_entries_count, selectedItem.count, selectedItem.count)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { selectedMonthIndex = null },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.analytics_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp),
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
    var selectedPointIndex by remember(history) { mutableStateOf<Int?>(null) }

    val lineColor = MaterialTheme.colorScheme.primary
    val avgLineColor = MaterialTheme.colorScheme.error
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
    val trendPath = remember { Path() }

    SectionCard("${stringResource(R.string.analytics_consumption_trend)} (${stringResource(unit.shortRes())})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (minVal, maxVal, range) = remember(history, average) {
                val values = history.map { it.consumptionValue }
                val allValues = if (average != null) values + average else values
                val min = allValues.minOrNull() ?: 0.0
                val max = allValues.maxOrNull() ?: 1.0
                Triple(min, max, (max - min).coerceAtLeast(0.5))
            }
            val midVal = (minVal + maxVal) / 2.0
            val maxLabel = remember(maxVal, locale) { Format.consumption(maxVal, locale) }
            val midLabel = remember(midVal, locale) { Format.consumption(midVal, locale) }
            val minLabel = remember(minVal, locale) { Format.consumption(minVal, locale) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(maxLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                    Text(midLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                    Text(minLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(history) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    val offset = up.position
                                    val pad = 12f
                                    val chartW = size.width - pad * 2

                                    var bestIdx: Int? = null
                                    var bestDist = Float.MAX_VALUE
                                    history.forEachIndexed { i, _ ->
                                        val x = if (history.size <= 1) {
                                            pad + chartW / 2f
                                        } else {
                                            pad + (i.toFloat() / (history.size - 1) * chartW)
                                        }
                                        val d = kotlin.math.abs(offset.x - x)
                                        if (d < bestDist && d < 36.dp.toPx()) {
                                            bestDist = d
                                            bestIdx = i
                                        }
                                    }
                                    selectedPointIndex = if (selectedPointIndex == bestIdx) null else bestIdx
                                }
                            }
                        },
                ) {
                    val pad = 12f
                    val chartW = size.width - pad * 2
                    val chartH = size.height - pad * 2

                    // Grid lines
                    val yPositions = floatArrayOf(pad, pad + chartH / 2f, pad + chartH)
                    for (y in yPositions) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }

                    val n = history.size
                    var selX = 0f
                    var selY = 0f
                    val hasSel = selectedPointIndex != null && selectedPointIndex in history.indices

                    trendPath.rewind()
                    for (i in 0 until n) {
                        val ptVal = history[i].consumptionValue
                        val px = if (n <= 1) {
                            pad + chartW / 2f
                        } else {
                            pad + (i.toFloat() / (n - 1) * chartW)
                        }
                        val py = (pad + chartH - ((ptVal - minVal) / range * chartH).toFloat())
                            .coerceIn(pad, pad + chartH)

                        if (i == 0) {
                            trendPath.moveTo(px, py)
                        } else {
                            trendPath.lineTo(px, py)
                        }

                        if (selectedPointIndex == i) {
                            selX = px
                            selY = py
                        }
                    }

                    // Average guideline
                    if (average != null) {
                        val avgY = (pad + chartH - ((average - minVal) / range * chartH).toFloat())
                            .coerceIn(pad, pad + chartH)
                        drawLine(
                            color = avgLineColor.copy(alpha = 0.6f),
                            start = Offset(pad, avgY),
                            end = Offset(pad + chartW, avgY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }

                    // Line connecting points
                    if (n >= 2) {
                        drawPath(trendPath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }

                    // Selected point vertical indicator line
                    if (hasSel) {
                        drawLine(
                            color = highlightColor.copy(alpha = 0.7f),
                            start = Offset(selX, pad),
                            end = Offset(selX, pad + chartH),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect,
                        )
                    }

                    val selHaloRadius = 10.dp.toPx()
                    val selPointRadius = 5.dp.toPx()
                    val normalPointRadius = 4.dp.toPx()
                    val selHaloColor = highlightColor.copy(alpha = 0.25f)

                    for (i in 0 until n) {
                        val ptVal = history[i].consumptionValue
                        val px = if (n <= 1) {
                            pad + chartW / 2f
                        } else {
                            pad + (i.toFloat() / (n - 1) * chartW)
                        }
                        val py = (pad + chartH - ((ptVal - minVal) / range * chartH).toFloat())
                            .coerceIn(pad, pad + chartH)
                        val ptOffset = Offset(px, py)

                        if (selectedPointIndex == i) {
                            drawCircle(selHaloColor, radius = selHaloRadius, center = ptOffset)
                            drawCircle(highlightColor, radius = selPointRadius, center = ptOffset)
                        } else {
                            drawCircle(lineColor, radius = normalPointRadius, center = ptOffset)
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                val firstDate = Format.date(history.first().date, locale)
                val lastDate = Format.date(history.last().date, locale)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp),
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

            // Interactive inspection banner for selected point
            val selectedPoint = selectedPointIndex?.let { history.getOrNull(it) }
            if (selectedPoint != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPointIndex = null },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "${Format.date(selectedPoint.date, locale)}: ${Format.consumption(selectedPoint.consumptionValue, locale)} ${stringResource(unit.shortRes())}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            val delta = selectedPoint.deltaFromAverage
                            if (delta != null && kotlin.math.abs(delta) > 0.05) {
                                val deltaFormatted = Format.consumption(kotlin.math.abs(delta), locale)
                                val deltaText = if (delta > 0) {
                                    stringResource(R.string.analytics_diff_more, deltaFormatted)
                                } else {
                                    stringResource(R.string.analytics_diff_less, deltaFormatted)
                                }
                                Text(
                                    text = deltaText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            selectedPoint.odometerKm?.let { odo ->
                                Text(
                                    text = stringResource(
                                        vehicle.distanceUnit.shortRes(),
                                        Format.distance(odo, vehicle.distanceUnit, locale),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(
                            onClick = { selectedPointIndex = null },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            } else {
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
