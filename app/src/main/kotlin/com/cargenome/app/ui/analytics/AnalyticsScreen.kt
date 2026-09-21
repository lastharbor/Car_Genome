package com.cargenome.app.ui.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.cargenome.app.ui.common.AppDateRangePickerDialog
import com.cargenome.app.ui.common.EmptyVehiclesTabCard
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.displayName
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

// Curated harmonic palette for vehicle spending categories
private fun categoryColor(key: String): Color = when (key.lowercase()) {
    "fuel" -> Color(0xFFFF7043)        // Deep Coral / Orange
    "service" -> Color(0xFF3F51B5)     // Indigo
    "insurance" -> Color(0xFF00897B)   // Teal
    "wash" -> Color(0xFF0288D1)        // Blue
    "parking" -> Color(0xFFFFA000)     // Amber
    "tyres" -> Color(0xFF8E24AA)       // Purple
    "tax" -> Color(0xFFE53935)         // Crimson Red
    "toll" -> Color(0xFF43A047)        // Emerald Green
    "accessories" -> Color(0xFF6D4C41) // Warm Bronze
    "credit" -> Color(0xFF00ACC1)      // Cyan
    "fine" -> Color(0xFFFF5722)        // Orange Red
    "registration" -> Color(0xFF5C6BC0)// Slate Indigo
    else -> Color(0xFF607D8B)          // Blue Grey
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAddVehicle: (() -> Unit)? = null,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDateRangePicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    if (showDateRangePicker) {
        val today = remember { LocalDate.now() }
        AppDateRangePickerDialog(
            initialStartDate = state.customStartDate ?: today.minusDays(29),
            initialEndDate = state.customEndDate ?: today,
            onRangeSelected = { start, end ->
                viewModel.setCustomRange(start, end)
            },
            onDismiss = { showDateRangePicker = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.analytics_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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

        val activeLabel = formatActivePeriodLabel(
            timeRange = state.selectedTimeRange,
            customStartDate = state.customStartDate,
            customEndDate = state.customEndDate,
            locale = locale,
        )

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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (vehicle == null) {
                    if (!state.isLoading) {
                        item(key = "empty_vehicles", contentType = "empty_vehicles") { EmptyVehiclesTabCard(onAddVehicle = onAddVehicle) }
                    }
                    return@LazyColumn
                }

                item(key = "time_range", contentType = "time_range") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeRangeSelector(
                            selectedRange = state.selectedTimeRange,
                            onSelectRange = { range ->
                                if (range == AnalyticsTimeRange.CUSTOM) {
                                    showDateRangePicker = true
                                } else {
                                    viewModel.setTimeRange(range)
                                }
                            },
                        )

                        ActivePeriodBar(
                            periodLabel = activeLabel,
                            isCustomOrRestricted = state.selectedTimeRange != AnalyticsTimeRange.ALL_TIME,
                            canStepBackward = state.canStepBackward,
                            canStepForward = state.canStepForward,
                            onStepBackward = { viewModel.stepPeriod(forward = false) },
                            onStepForward = { viewModel.stepPeriod(forward = true) },
                            onOpenDatePicker = { showDateRangePicker = true },
                            onResetAllTime = { viewModel.setTimeRange(AnalyticsTimeRange.ALL_TIME) },
                        )
                    }
                }

                if (data.totalSpendMinor == 0L && data.trackedDistanceKm == 0.0) {
                    if (!state.isLoading) item(key = "empty", contentType = "empty") { EmptyAnalyticsCard() }
                } else {
                    item(key = "cost_overview", contentType = "cost_overview") {
                        CostOverviewCard(data, vehicle, state.selectedTimeRange, activeLabel)
                    }

                    if (data.categorySpends.isNotEmpty()) {
                        item(key = "category_spends", contentType = "category_spends") {
                            CategorySpendCard(data.categorySpends, vehicle, data.totalSpendMinor, data.totalEntriesCount)
                        }
                    }

                    if (data.monthlySpends.isNotEmpty()) {
                        item(key = "monthly_spends", contentType = "monthly_spends") {
                            MonthlySpendCard(data.monthlySpends, vehicle)
                        }
                    }

                    item(key = "consumption_trend", contentType = "consumption_trend") {
                        ConsumptionTrendCard(data.consumptionHistory, vehicle, data.averageConsumption)
                    }
                }
            }
        }
    }
}

private val AnalyticsTimeRange.labelRes: Int
    get() = when (this) {
        AnalyticsTimeRange.ALL_TIME -> R.string.analytics_range_all
        AnalyticsTimeRange.THIS_YEAR -> R.string.analytics_range_this_year
        AnalyticsTimeRange.YEAR_1 -> R.string.analytics_range_year
        AnalyticsTimeRange.MONTHS_6 -> R.string.analytics_range_6m
        AnalyticsTimeRange.MONTHS_3 -> R.string.analytics_range_3m
        AnalyticsTimeRange.THIS_MONTH -> R.string.analytics_range_this_month
        AnalyticsTimeRange.CUSTOM -> R.string.analytics_range_custom
    }

@Composable
private fun formatActivePeriodLabel(
    timeRange: AnalyticsTimeRange,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    locale: Locale,
): String = when (timeRange) {
    AnalyticsTimeRange.ALL_TIME -> stringResource(R.string.analytics_range_all)
    AnalyticsTimeRange.THIS_YEAR -> stringResource(R.string.analytics_range_this_year)
    AnalyticsTimeRange.THIS_MONTH -> {
        val now = LocalDate.now()
        val monthName = now.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        "$monthName ${now.year}"
    }
    AnalyticsTimeRange.YEAR_1 -> stringResource(R.string.analytics_range_year)
    AnalyticsTimeRange.MONTHS_6 -> stringResource(R.string.analytics_range_6m)
    AnalyticsTimeRange.MONTHS_3 -> stringResource(R.string.analytics_range_3m)
    AnalyticsTimeRange.CUSTOM -> {
        if (customStartDate != null && customEndDate != null) {
            val days = ChronoUnit.DAYS.between(customStartDate, customEndDate) + 1
            val daysText = stringResource(R.string.analytics_days_format, days)
            if (customStartDate == customEndDate) {
                Format.date(customStartDate, locale)
            } else if (customStartDate.year == customEndDate.year && customStartDate.month == customEndDate.month) {
                val monthName = customStartDate.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                "${customStartDate.dayOfMonth}–${customEndDate.dayOfMonth} $monthName ${customStartDate.year} ($daysText)"
            } else {
                "${Format.date(customStartDate, locale)} – ${Format.date(customEndDate, locale)} ($daysText)"
            }
        } else {
            stringResource(R.string.analytics_range_custom)
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: AnalyticsTimeRange,
    onSelectRange: (AnalyticsTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ranges = remember {
        listOf(
            AnalyticsTimeRange.ALL_TIME,
            AnalyticsTimeRange.THIS_YEAR,
            AnalyticsTimeRange.YEAR_1,
            AnalyticsTimeRange.MONTHS_6,
            AnalyticsTimeRange.MONTHS_3,
            AnalyticsTimeRange.THIS_MONTH,
            AnalyticsTimeRange.CUSTOM,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(ranges) { range ->
                val label = stringResource(range.labelRes)
                val isSelected = range == selectedRange
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "pill_bg",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pill_text",
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectRange(range)
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (range == AnalyticsTimeRange.CUSTOM) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = textColor,
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = textColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePeriodBar(
    periodLabel: String,
    isCustomOrRestricted: Boolean,
    canStepBackward: Boolean,
    canStepForward: Boolean,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onResetAllTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStepBackward()
                },
                enabled = canStepBackward,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.analytics_previous_period),
                    tint = if (canStepBackward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenDatePicker()
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStepForward()
                },
                enabled = canStepForward,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.analytics_next_period),
                    tint = if (canStepForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }

            if (isCustomOrRestricted) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResetAllTime()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.analytics_reset_all_time),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAnalyticsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.analytics_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.analytics_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CostOverviewCard(
    data: VehicleAnalyticsData,
    vehicle: VehicleEntity,
    timeRange: AnalyticsTimeRange,
    periodLabel: String,
) {
    val locale = LocalConfiguration.current.locales[0]
    val distSuffix = stringResource(vehicle.distanceUnit.suffixRes())
    val consUnit = vehicle.consumptionUnit()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Total Ownership Cost Banner
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.analytics_total_cost).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Format.money(data.totalSpendMinor, vehicle.currencyCode, locale),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (data.totalEntriesCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.analytics_entries_count,
                                    data.totalEntriesCount,
                                    data.totalEntriesCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    data.costPerDayMinor?.let { perDay ->
                        Text(
                            text = "•   ${Format.money(perDay, vehicle.currencyCode, locale)} / день",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // 2x2 Glanceable Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Metric 1: Average Consumption
                MetricTile(
                    icon = Icons.Default.LocalGasStation,
                    iconTint = Color(0xFFFF7043),
                    iconBg = Color(0xFFFF7043).copy(alpha = 0.12f),
                    title = stringResource(R.string.fuel_average),
                    value = data.averageConsumption?.let {
                        "${Format.consumption(it, locale)} ${stringResource(consUnit.shortRes())}"
                    } ?: "—",
                    subtitle = data.bestConsumption?.let {
                        stringResource(R.string.analytics_best_consumption, Format.consumption(it, locale))
                    },
                    modifier = Modifier.weight(1f),
                )

                // Metric 2: Cost Per Distance
                val perKmText = data.totalCostPerKmMinor?.let { perKm ->
                    val perDist = (perKm * vehicle.distanceUnit.kilometresPerUnit).toLong()
                    "${Format.money(perDist, vehicle.currencyCode, locale)} / $distSuffix"
                } ?: "—"
                val fuelPerKmText = data.fuelCostPerKmMinor?.let { perKm ->
                    val perDist = (perKm * vehicle.distanceUnit.kilometresPerUnit).toLong()
                    "Топливо: ${Format.money(perDist, vehicle.currencyCode, locale)}"
                }
                MetricTile(
                    icon = Icons.Default.Speed,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    title = "Цена / $distSuffix",
                    value = perKmText,
                    subtitle = fuelPerKmText,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Metric 3: Monthly Average
                MetricTile(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = Color(0xFF00897B),
                    iconBg = Color(0xFF00897B).copy(alpha = 0.12f),
                    title = stringResource(R.string.analytics_avg_monthly),
                    value = if (data.averageMonthlySpendMinor > 0L) {
                        Format.money(data.averageMonthlySpendMinor, vehicle.currencyCode, locale)
                    } else "—",
                    subtitle = if (data.monthlySpends.isNotEmpty()) "${data.monthlySpends.size} мес." else null,
                    modifier = Modifier.weight(1f),
                )

                // Metric 4: Tracked Distance
                MetricTile(
                    icon = Icons.Default.DirectionsCar,
                    iconTint = Color(0xFF3F51B5),
                    iconBg = Color(0xFF3F51B5).copy(alpha = 0.12f),
                    title = stringResource(R.string.analytics_distance_tracked),
                    value = if (data.trackedDistanceKm > 0) {
                        stringResource(
                            vehicle.distanceUnit.shortRes(),
                            Format.distance(data.trackedDistanceKm, vehicle.distanceUnit, locale),
                        )
                    } else "—",
                    subtitle = periodLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconBg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    val haptic = LocalHapticFeedback.current
    var selectedCategoryKey by remember(categories) { mutableStateOf<String?>(null) }
    val selectedCat = remember(categories, selectedCategoryKey) {
        categories.find { it.key == selectedCategoryKey }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.analytics_category_breakdown),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (selectedCategoryKey != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategoryKey = null
                            },
                    ) {
                        Text(
                            text = stringResource(R.string.analytics_reset_selection),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Modern Gapped Donut Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
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
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedCategoryKey = if (selectedCategoryKey == tappedKey) null else tappedKey
                                    } else if (dist < radius - defaultStroke) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    val gapAngle = if (categories.size > 1) 2.5f else 0f

                    var startAngle = -90f
                    categories.forEach { cat ->
                        val sweepAngle = (cat.percentage * 3.6f - gapAngle).coerceAtLeast(0.5f)
                        val isSelected = selectedCategoryKey == cat.key
                        val hasAnySelection = selectedCategoryKey != null
                        val rawColor = categoryColor(cat.key)
                        val color = if (hasAnySelection && !isSelected) {
                            rawColor.copy(alpha = 0.28f)
                        } else {
                            rawColor
                        }
                        val stroke = if (isSelected) selectedStroke else baseStroke

                        drawArc(
                            color = color,
                            startAngle = startAngle + gapAngle / 2f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        startAngle += cat.percentage * 3.6f
                    }
                }

                // Donut Center Hub
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (selectedCategoryKey != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategoryKey = null
                            }
                        }
                        .padding(16.dp),
                ) {
                    if (selectedCat != null) {
                        Text(
                            text = categoryDisplayName(selectedCat.key),
                            style = MaterialTheme.typography.labelMedium,
                            color = categoryColor(selectedCat.key),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Format.money(selectedCat.amountMinor, vehicle.currencyCode, locale),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = categoryColor(selectedCat.key).copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                text = "${selectedCat.percentage.roundToInt()}% (${selectedCat.count})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = categoryColor(selectedCat.key),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.analytics_total_cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = Format.money(totalSpendMinor, vehicle.currencyCode, locale),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        )
                        if (totalEntriesCount > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.analytics_entries_count, totalEntriesCount, totalEntriesCount),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Proportional Interactive Legend List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    val color = categoryColor(cat.key)
                    val label = categoryDisplayName(cat.key)
                    val money = Format.money(cat.amountMinor, vehicle.currencyCode, locale)
                    val pct = "${cat.percentage.roundToInt()}%"
                    val isSelected = selectedCategoryKey == cat.key

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategoryKey = if (selectedCategoryKey == cat.key) null else cat.key
                            },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 12.dp else 10.dp)
                                            .background(color, CircleShape),
                                    )
                                    val fullLabel = if (cat.count > 0) "$label (${cat.count})" else label
                                    Text(
                                        text = fullLabel,
                                        style = if (isSelected) {
                                            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "$money  •  $pct",
                                    style = if (isSelected) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            // Visual proportion bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (cat.percentage / 100f).coerceIn(0.01f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) color else color.copy(alpha = 0.85f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySpendCard(monthlySpends: List<MonthlySpend>, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val haptic = LocalHapticFeedback.current
    val maxSpend = remember(monthlySpends) { monthlySpends.maxOf { it.amountMinor }.coerceAtLeast(1L) }
    var selectedMonthIndex by remember(monthlySpends) { mutableStateOf<Int?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val barColorDimmed = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val slotTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
    val maxLabel = remember(maxSpend, vehicle.currencyCode, locale) { Format.money(maxSpend, vehicle.currencyCode, locale) }
    val midLabel = remember(maxSpend, vehicle.currencyCode, locale) { Format.money(maxSpend / 2L, vehicle.currencyCode, locale) }

    val selectedItem = selectedMonthIndex?.let { monthlySpends.getOrNull(it) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Dynamic Integrated Header (No Layout Shift)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.analytics_monthly_spend),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    if (selectedItem != null) {
                        val monthTitle = remember(selectedItem.yearMonth, locale) {
                            try {
                                DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(selectedItem.yearMonth)
                                    .replaceFirstChar { it.titlecase(locale) }
                            } catch (_: Exception) {
                                "${selectedItem.yearMonth.monthValue}/${selectedItem.yearMonth.year}"
                            }
                        }
                        Text(
                            text = "$monthTitle: ${Format.money(selectedItem.amountMinor, vehicle.currencyCode, locale)} (${selectedItem.percentageOfTotal.roundToInt()}%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = highlightColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        val avgSpend = remember(monthlySpends) {
                            if (monthlySpends.isNotEmpty()) monthlySpends.sumOf { it.amountMinor } / monthlySpends.size else 0L
                        }
                        Text(
                            text = "${stringResource(R.string.analytics_avg_monthly)}: ${Format.money(avgSpend, vehicle.currencyCode, locale)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                        )
                    }
                }
                if (selectedMonthIndex != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedMonthIndex = null
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = textColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Interactive Bar Chart with Continuous Gesture Scrubbing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Y-Axis labels
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(text = maxLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                    Text(text = midLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                    Text(text = "0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))

                // Canvas
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(monthlySpends) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateTouch(touchX: Float) {
                                    val chartWidth = size.width.toFloat()
                                    val barCount = monthlySpends.size
                                    val maxBarWidth = 44.dp.toPx()
                                    val minBarWidth = 4.dp.toPx()
                                    val spacing = (chartWidth / (barCount * 4 + 1)).coerceIn(3.dp.toPx(), 8.dp.toPx())
                                    val rawBarWidth = ((chartWidth - (barCount + 1) * spacing) / barCount).coerceAtLeast(minBarWidth)
                                    val barWidth = rawBarWidth.coerceAtMost(maxBarWidth)
                                    val totalBarsWidth = barCount * barWidth + (barCount - 1) * spacing
                                    val startX = if (totalBarsWidth < chartWidth) (chartWidth - totalBarsWidth) / 2f else spacing

                                    var hitIdx: Int? = null
                                    monthlySpends.forEachIndexed { idx, _ ->
                                        val x = startX + idx * (barWidth + spacing)
                                        if (touchX in (x - spacing / 2f)..(x + barWidth + spacing / 2f)) {
                                            hitIdx = idx
                                        }
                                    }
                                    if (hitIdx != selectedMonthIndex && hitIdx != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    selectedMonthIndex = hitIdx
                                }
                                updateTouch(down.position.x)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val current = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!current.pressed) break
                                    updateTouch(current.position.x)
                                }
                            }
                        },
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height - 20.dp.toPx()

                    // Grid lines
                    val strokeW = 1.dp.toPx()
                    drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(chartWidth, 0f), strokeWidth = strokeW, pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, chartHeight / 2f), end = Offset(chartWidth, chartHeight / 2f), strokeWidth = strokeW, pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, chartHeight), end = Offset(chartWidth, chartHeight), strokeWidth = strokeW)

                    val barCount = monthlySpends.size
                    val maxBarWidth = 44.dp.toPx()
                    val minBarWidth = 4.dp.toPx()
                    val spacing = (chartWidth / (barCount * 4 + 1)).coerceIn(3.dp.toPx(), 8.dp.toPx())
                    val rawBarWidth = ((chartWidth - (barCount + 1) * spacing) / barCount).coerceAtLeast(minBarWidth)
                    val barWidth = rawBarWidth.coerceAtMost(maxBarWidth)
                    val totalBarsWidth = barCount * barWidth + (barCount - 1) * spacing
                    val startX = if (totalBarsWidth < chartWidth) (chartWidth - totalBarsWidth) / 2f else spacing
                    val barCornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    monthlySpends.forEachIndexed { index, item ->
                        val x = startX + index * (barWidth + spacing)
                        val barHeight = (item.amountMinor.toDouble() / maxSpend * chartHeight).toFloat().coerceIn(0f, chartHeight)
                        val y = chartHeight - barHeight
                        val isSelected = selectedMonthIndex == index
                        val hasSelection = selectedMonthIndex != null

                        // Background slot track
                        drawRoundRect(
                            color = slotTrackColor,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = barCornerRadius,
                        )

                        // Bar fill
                        if (barHeight > 0f) {
                            val fillBrush = if (isSelected) {
                                Brush.verticalGradient(
                                    colors = listOf(highlightColor, highlightColor.copy(alpha = 0.75f)),
                                    startY = y,
                                    endY = chartHeight,
                                )
                            } else if (hasSelection) {
                                Brush.verticalGradient(
                                    colors = listOf(barColorDimmed, barColorDimmed.copy(alpha = 0.5f)),
                                    startY = y,
                                    endY = chartHeight,
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(barColor, barColor.copy(alpha = 0.7f)),
                                    startY = y,
                                    endY = chartHeight,
                                )
                            }

                            drawRoundRect(
                                brush = fillBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = barCornerRadius,
                            )

                            if (isSelected) {
                                // Glowing top cap marker
                                drawCircle(
                                    color = highlightColor,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(x + barWidth / 2f, (y - 5.dp.toPx()).coerceAtLeast(3.5.dp.toPx())),
                                )
                            }
                        }
                    }
                }
            }

            // Month Labels on X-axis
            if (monthlySpends.size == 1) {
                val only = monthlySpends.first().yearMonth
                val onlyStr = try {
                    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(only)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                } catch (_: Exception) { "${only.monthValue}/${only.year}" }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = onlyStr, style = MaterialTheme.typography.bodySmall, color = textColor)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val first = monthlySpends.first().yearMonth
                    val last = monthlySpends.last().yearMonth
                    val firstStr = try {
                        DateTimeFormatter.ofPattern("LLL yy", locale).format(first)
                    } catch (_: Exception) { "${first.monthValue}/${first.year}" }
                    val lastStr = try {
                        DateTimeFormatter.ofPattern("LLL yy", locale).format(last)
                    } catch (_: Exception) { "${last.monthValue}/${last.year}" }

                    Text(text = firstStr, style = MaterialTheme.typography.bodySmall, color = textColor)
                    if (monthlySpends.size > 2) {
                        val mid = monthlySpends[monthlySpends.size / 2].yearMonth
                        val midStr = try {
                            DateTimeFormatter.ofPattern("LLL yy", locale).format(mid)
                        } catch (_: Exception) { "${mid.monthValue}/${mid.year}" }
                        Text(text = midStr, style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                    Text(text = lastStr, style = MaterialTheme.typography.bodySmall, color = textColor)
                }
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
    val haptic = LocalHapticFeedback.current
    val unit = vehicle.consumptionUnit()
    var selectedPointIndex by remember(history) { mutableStateOf<Int?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val avgLineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) }
    val trendPath = remember { Path() }
    val areaPath = remember { Path() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val selectedPoint = selectedPointIndex?.let { history.getOrNull(it) }

            // Dynamic Header (No Layout Shift)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.analytics_consumption_trend)} (${stringResource(unit.shortRes())})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    if (selectedPoint != null) {
                        val delta = selectedPoint.deltaFromAverage
                        val deltaText = if (delta != null && kotlin.math.abs(delta) > 0.05) {
                            val deltaFormatted = Format.consumption(kotlin.math.abs(delta), locale)
                            if (delta > 0) {
                                stringResource(R.string.analytics_diff_more, deltaFormatted)
                            } else {
                                stringResource(R.string.analytics_diff_less, deltaFormatted)
                            }
                        } else null

                        Text(
                            text = "${Format.date(selectedPoint.date, locale)} • ${Format.consumption(selectedPoint.consumptionValue, locale)} ${stringResource(unit.shortRes())}${if (deltaText != null) " ($deltaText)" else ""}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = highlightColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (average != null) {
                        Text(
                            text = "${stringResource(R.string.fuel_average)}: ${Format.consumption(average, locale)} ${stringResource(unit.shortRes())} • ${history.size} заправок",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                        )
                    }
                }
                if (selectedPointIndex != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedPointIndex = null
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = textColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (history.size < 2) {
                // Graceful single/zero point state
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.analytics_trend_need_records),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Interactive Cubic Bézier Spline Area Chart
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
                        .height(160.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Y-Axis labels
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(maxLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                        Text(midLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                        Text(minLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = textColor, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(history) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    fun updateTouch(touchX: Float) {
                                        val pad = 12f
                                        val chartW = size.width - pad * 2
                                        var bestIdx: Int? = null
                                        var bestDist = Float.MAX_VALUE
                                        history.forEachIndexed { i, _ ->
                                            val x = pad + (i.toFloat() / (history.size - 1) * chartW)
                                            val d = kotlin.math.abs(touchX - x)
                                            if (d < bestDist && d < 48.dp.toPx()) {
                                                bestDist = d
                                                bestIdx = i
                                            }
                                        }
                                        if (bestIdx != selectedPointIndex && bestIdx != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        selectedPointIndex = bestIdx
                                    }
                                    updateTouch(down.position.x)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val current = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!current.pressed) break
                                        updateTouch(current.position.x)
                                    }
                                }
                            },
                    ) {
                        val pad = 12f
                        val chartW = size.width - pad * 2
                        val chartH = size.height - pad * 2
                        val chartBottom = pad + chartH

                        // Grid lines
                        val strokeW = 1.dp.toPx()
                        drawLine(color = gridColor, start = Offset(0f, pad), end = Offset(size.width, pad), strokeWidth = strokeW, pathEffect = dashEffect)
                        drawLine(color = gridColor, start = Offset(0f, pad + chartH / 2f), end = Offset(size.width, pad + chartH / 2f), strokeWidth = strokeW, pathEffect = dashEffect)
                        drawLine(color = gridColor, start = Offset(0f, chartBottom), end = Offset(size.width, chartBottom), strokeWidth = strokeW)

                        val n = history.size
                        val points = mutableListOf<Offset>()
                        for (i in 0 until n) {
                            val ptVal = history[i].consumptionValue
                            val px = pad + (i.toFloat() / (n - 1) * chartW)
                            val py = (pad + chartH - ((ptVal - minVal) / range * chartH).toFloat()).coerceIn(pad, chartBottom)
                            points.add(Offset(px, py))
                        }

                        // Build smooth Cubic Bézier curve
                        trendPath.rewind()
                        areaPath.rewind()

                        if (points.isNotEmpty()) {
                            trendPath.moveTo(points[0].x, points[0].y)
                            areaPath.moveTo(points[0].x, chartBottom)
                            areaPath.lineTo(points[0].x, points[0].y)

                            for (i in 1 until points.size) {
                                val pPrev = points[i - 1]
                                val pCurr = points[i]
                                val cp1X = pPrev.x + (pCurr.x - pPrev.x) / 2f
                                val cp1Y = pPrev.y
                                val cp2X = pCurr.x - (pCurr.x - pPrev.x) / 2f
                                val cp2Y = pCurr.y
                                trendPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, pCurr.x, pCurr.y)
                                areaPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, pCurr.x, pCurr.y)
                            }

                            areaPath.lineTo(points.last().x, chartBottom)
                            areaPath.close()

                            // Gradient area under curve
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent),
                                    startY = pad,
                                    endY = chartBottom,
                                ),
                            )

                            // Average reference guideline
                            if (average != null) {
                                val avgY = (pad + chartH - ((average - minVal) / range * chartH).toFloat()).coerceIn(pad, chartBottom)
                                drawLine(
                                    color = avgLineColor,
                                    start = Offset(0f, avgY),
                                    end = Offset(size.width, avgY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect,
                                )
                            }

                            // Spline curve stroke
                            drawPath(
                                path = trendPath,
                                color = primaryColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            )

                            // Scrubber vertical cursor line
                            val activeIndex = selectedPointIndex
                            if (activeIndex != null && activeIndex in points.indices) {
                                val activePt = points[activeIndex]
                                drawLine(
                                    color = highlightColor.copy(alpha = 0.6f),
                                    start = Offset(activePt.x, pad),
                                    end = Offset(activePt.x, chartBottom),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect,
                                )
                            }

                            // Draw data points
                            val selHaloRadius = 11.dp.toPx()
                            val selPointRadius = 5.dp.toPx()
                            val normalPointRadius = 3.5.dp.toPx()

                            points.forEachIndexed { i, pt ->
                                if (i == activeIndex) {
                                    // Outer glowing halo
                                    drawCircle(highlightColor.copy(alpha = 0.22f), radius = selHaloRadius, center = pt)
                                    // Solid outer ring
                                    drawCircle(highlightColor, radius = selPointRadius, center = pt)
                                    // Crisp inner core
                                    drawCircle(Color.White, radius = 2.dp.toPx(), center = pt)
                                } else {
                                    drawCircle(primaryColor, radius = normalPointRadius, center = pt)
                                    drawCircle(Color.White, radius = 1.5.dp.toPx(), center = pt)
                                }
                            }
                        }
                    }
                }

                // X-axis Dates
                val firstDate = Format.date(history.first().date, locale)
                val lastDate = Format.date(history.last().date, locale)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = firstDate, style = MaterialTheme.typography.bodySmall, color = textColor)
                    if (firstDate != lastDate) {
                        Text(text = lastDate, style = MaterialTheme.typography.bodySmall, color = textColor)
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
