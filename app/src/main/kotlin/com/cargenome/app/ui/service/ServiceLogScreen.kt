package com.cargenome.app.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.totalCostMinor
import com.cargenome.app.domain.service.ScheduleDueStatus
import com.cargenome.app.domain.service.ScheduleStatus
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.EmptyVehiclesTabCard
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.TagBadge
import com.cargenome.app.ui.common.displayName
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.shortRes
import com.cargenome.app.ui.common.suffixRes
import com.cargenome.app.ui.service.calendar.CompleteMaintenanceEventDialog
import com.cargenome.app.ui.service.calendar.MaintenanceCalendarView
import com.cargenome.app.ui.service.calendar.MaintenanceEventCard
import com.cargenome.app.ui.service.calendar.MaintenanceEventDialog
import java.util.Locale
import kotlin.math.abs

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceLogScreen(
    onAddRecord: (Long) -> Unit,
    onOpenRecord: (vehicleId: Long, recordId: Long) -> Unit,
    onAddSchedule: (Long) -> Unit,
    onEditSchedule: (vehicleId: Long, scheduleId: Long) -> Unit,
    onMarkScheduleDone: (vehicleId: Long, scheduleId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAddVehicle: (() -> Unit)? = null,
    viewModel: ServiceLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<MaintenanceEventEntity?>(null) }
    var eventToComplete by remember { mutableStateOf<MaintenanceEventEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.service_title),
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
                SecondaryTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = state.selectedTab == ServiceTab.Records,
                        onClick = { viewModel.selectTab(ServiceTab.Records) },
                        text = { Text(stringResource(R.string.service_tab_records)) },
                    )
                    Tab(
                        selected = state.selectedTab == ServiceTab.Calendar,
                        onClick = { viewModel.selectTab(ServiceTab.Calendar) },
                        text = { Text(stringResource(R.string.service_tab_calendar)) },
                    )
                    Tab(
                        selected = state.selectedTab == ServiceTab.Schedule,
                        onClick = { viewModel.selectTab(ServiceTab.Schedule) },
                        text = { Text(stringResource(R.string.service_tab_schedule)) },
                    )
                }
            }
        },
        floatingActionButton = {
            val currentVehicle = state.vehicle
            if (currentVehicle != null) {
                when (state.selectedTab) {
                    ServiceTab.Records -> {
                        ExtendedFloatingActionButton(
                            onClick = { onAddRecord(currentVehicle.id) },
                            text = { Text(stringResource(R.string.service_add_record)) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                    }
                    ServiceTab.Calendar -> {
                        ExtendedFloatingActionButton(
                            onClick = { showAddEventDialog = true },
                            text = { Text(stringResource(R.string.service_add_event)) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                    }
                    ServiceTab.Schedule -> {
                        ExtendedFloatingActionButton(
                            onClick = { onAddSchedule(currentVehicle.id) },
                            text = { Text(stringResource(R.string.service_add_schedule)) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                    }
                }
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
                        item { EmptyVehiclesTabCard(onAddVehicle = onAddVehicle) }
                    }
                    return@LazyColumn
                }

                when (state.selectedTab) {
                    ServiceTab.Records -> {
                        if (state.records.isEmpty()) {
                            if (!state.isLoading) item { EmptyRecordsCard() }
                        } else {
                            item { ServiceSpendSummaryCard(state, vehicle) }
                            items(
                                items = state.records,
                                key = { it.id },
                                contentType = { "service_record" },
                            ) { record ->
                                ServiceRecordRow(
                                    record = record,
                                    vehicle = vehicle,
                                    onClick = { onOpenRecord(vehicle.id, record.id) },
                                )
                            }
                        }
                    }

                    ServiceTab.Calendar -> {
                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            item {
                                NotificationPermissionCard(
                                    onRequestPermission = {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                )
                            }
                        }

                        // Interactive Month Calendar View
                        item {
                            MaintenanceCalendarView(
                                selectedDate = state.selectedDate,
                                onSelectDate = viewModel::selectDate,
                                events = state.events,
                            )
                        }

                        // Selected Date Header
                        item {
                            val locale = LocalConfiguration.current.locales[0]
                            val selectedDateFormatted = Format.date(state.selectedDate, locale)
                            Text(
                                text = stringResource(R.string.calendar_selected_date_title, selectedDateFormatted),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }

                        // Events on selected date
                        val dayEvents = state.selectedDateEvents
                        if (dayEvents.isNotEmpty()) {
                            items(
                                items = dayEvents,
                                key = { "day_${it.id}" },
                                contentType = { "maintenance_event" },
                            ) { event ->
                                MaintenanceEventCard(
                                    event = event,
                                    vehicle = vehicle,
                                    onComplete = { eventToComplete = event },
                                    onEdit = { eventToEdit = event },
                                    onDelete = { viewModel.deleteEvent(event.id) },
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.calendar_no_events_on_day),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = { showAddEventDialog = true }) {
                                            Text(stringResource(R.string.calendar_add_for_this_day))
                                        }
                                    }
                                }
                            }
                        }

                        // Overdue Events
                        val overdueOther = state.overdueEvents.filter { it.scheduledDate != state.selectedDate }
                        if (overdueOther.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.calendar_overdue_section),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                )
                            }
                            items(
                                items = overdueOther,
                                key = { "overdue_${it.id}" },
                                contentType = { "maintenance_event" },
                            ) { event ->
                                MaintenanceEventCard(
                                    event = event,
                                    vehicle = vehicle,
                                    onComplete = { eventToComplete = event },
                                    onEdit = { eventToEdit = event },
                                    onDelete = { viewModel.deleteEvent(event.id) },
                                )
                            }
                        }

                        // Upcoming Events
                        val upcomingOther = state.upcomingEvents.filter { it.scheduledDate != state.selectedDate }
                        if (upcomingOther.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.calendar_upcoming_section),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                )
                            }
                            items(
                                items = upcomingOther,
                                key = { "upcoming_${it.id}" },
                                contentType = { "maintenance_event" },
                            ) { event ->
                                MaintenanceEventCard(
                                    event = event,
                                    vehicle = vehicle,
                                    onComplete = { eventToComplete = event },
                                    onEdit = { eventToEdit = event },
                                    onDelete = { viewModel.deleteEvent(event.id) },
                                )
                            }
                        }

                        // Empty calendar card
                        if (state.events.isEmpty() && !state.isLoading) {
                            item {
                                EmptyCalendarCard()
                            }
                        }
                    }

                    ServiceTab.Schedule -> {
                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            item {
                                NotificationPermissionCard(
                                    onRequestPermission = {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                )
                            }
                        }

                        if (state.schedules.isEmpty()) {
                            if (!state.isLoading) item { EmptyScheduleCard() }
                        } else {
                            items(
                                items = state.schedules,
                                key = { it.schedule.id },
                                contentType = { "schedule_item" },
                            ) { scheduleStatus ->
                                ScheduleItemCard(
                                    status = scheduleStatus,
                                    vehicle = vehicle,
                                    onMarkDone = { onMarkScheduleDone(vehicle.id, scheduleStatus.schedule.id) },
                                    onEdit = { onEditSchedule(vehicle.id, scheduleStatus.schedule.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentVehicle = state.vehicle
    if (showAddEventDialog && currentVehicle != null) {
        MaintenanceEventDialog(
            initialDate = state.selectedDate,
            vehicle = currentVehicle,
            onDismiss = { showAddEventDialog = false },
            onSave = { title, cat, date, time, odo, cost, shop, notes, remind ->
                viewModel.addEvent(
                    title = title,
                    category = cat,
                    scheduledDate = date,
                    scheduledTimeMinutes = time,
                    targetOdometerKm = odo,
                    estimatedCostMinor = cost,
                    shop = shop,
                    notes = notes,
                    remindAdvanceDays = remind,
                )
                showAddEventDialog = false
            },
        )
    }

    eventToEdit?.let { event ->
        if (currentVehicle != null) {
            MaintenanceEventDialog(
                initialDate = event.scheduledDate,
                vehicle = currentVehicle,
                eventToEdit = event,
                onDismiss = { eventToEdit = null },
                onSave = { title, cat, date, time, odo, cost, shop, notes, remind ->
                    viewModel.updateEvent(
                        event.copy(
                            title = title,
                            category = cat,
                            scheduledDate = date,
                            scheduledTimeMinutes = time,
                            targetOdometerKm = odo,
                            estimatedCostMinor = cost,
                            shop = shop,
                            notes = notes,
                            remindAdvanceDays = remind,
                        ),
                    )
                    eventToEdit = null
                },
            )
        }
    }

    eventToComplete?.let { event ->
        if (currentVehicle != null) {
            CompleteMaintenanceEventDialog(
                event = event,
                vehicle = currentVehicle,
                currentOdometerKm = state.currentOdometerKm,
                onDismiss = { eventToComplete = null },
                onConfirm = { createRecord, actualOdo, labourMinor, partsMinor, shop, notes ->
                    viewModel.completeEvent(
                        event = event,
                        createRecord = createRecord,
                        actualOdometerKm = actualOdo,
                        labourCostMinor = labourMinor,
                        partsCostMinor = partsMinor,
                        shop = shop,
                        notes = notes,
                    )
                    eventToComplete = null
                },
            )
        }
    }
}

@Composable
private fun EmptyCalendarCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.calendar_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.calendar_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyRecordsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.service_empty_records_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.service_empty_records_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyScheduleCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.service_empty_schedule_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.service_empty_schedule_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.notification_permission_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.notification_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.notification_permission_action))
            }
        }
    }
}

@Composable
private fun ServiceSpendSummaryCard(state: ServiceLogUiState, vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]

    SectionCard(stringResource(R.string.service_title)) {
        DetailRow(
            label = stringResource(R.string.service_summary_total),
            value = Format.money(state.totalSpendMinor, vehicle.currencyCode, locale),
        )
        if (state.partsSpendMinor > 0) {
            DetailRow(
                label = stringResource(R.string.service_summary_parts),
                value = Format.money(state.partsSpendMinor, vehicle.currencyCode, locale),
            )
        }
        if (state.labourSpendMinor > 0) {
            DetailRow(
                label = stringResource(R.string.service_summary_labour),
                value = Format.money(state.labourSpendMinor, vehicle.currencyCode, locale),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceRecordRow(
    record: ServiceRecordEntity,
    vehicle: VehicleEntity,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Format.date(record.performedAt, locale),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Format.money(record.totalCostMinor, vehicle.currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyLarge,
            )

            record.odometerKm?.let { km ->
                Text(
                    text = stringResource(
                        vehicle.distanceUnit.shortRes(),
                        Format.distance(km, vehicle.distanceUnit, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            record.shop?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagBadge(text = stringResource(record.category.labelRes()))
                if (record.scheduleId != null) {
                    TagBadge(text = "\u2713 " + stringResource(R.string.service_tab_schedule))
                }
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    status: ScheduleStatus,
    vehicle: VehicleEntity,
    onMarkDone: () -> Unit,
    onEdit: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val schedule = status.schedule

    val (cardColor, badgeColor, badgeText) = when (status.overallStatus) {
        ScheduleDueStatus.Overdue -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.error,
            stringResource(R.string.schedule_status_overdue),
        )
        ScheduleDueStatus.DueSoon -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.schedule_status_due_soon),
        )
        ScheduleDueStatus.Ok -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.outline,
            stringResource(R.string.schedule_status_ok),
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TagBadge(
                    text = badgeText,
                    textColor = badgeColor,
                    containerColor = badgeColor.copy(alpha = 0.15f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagBadge(text = stringResource(schedule.category.labelRes()))

                val intervalParts = mutableListOf<String>()
                schedule.intervalKm?.let { km ->
                    val distStr = Format.distance(
                        kilometres = km,
                        unit = vehicle.distanceUnit,
                        locale = locale,
                    )
                    intervalParts.add("$distStr ${stringResource(vehicle.distanceUnit.suffixRes())}")
                }
                schedule.intervalMonths?.let { months ->
                    intervalParts.add(stringResource(R.string.schedule_interval_months_badge, months))
                }
                if (intervalParts.isNotEmpty()) {
                    TagBadge(text = intervalParts.joinToString(" / "))
                }
            }

            // Due information details
            status.remainingKm?.let { diffKm ->
                val formattedDiff = stringResource(
                    vehicle.distanceUnit.shortRes(),
                    Format.distance(abs(diffKm), vehicle.distanceUnit, locale),
                )
                val line = if (diffKm <= 0) {
                    stringResource(R.string.schedule_overdue_by_km, formattedDiff)
                } else {
                    stringResource(R.string.schedule_due_in_km, formattedDiff)
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diffKm <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }

            status.remainingDays?.let { days ->
                val absDays = abs(days)
                val line = if (days < 0) {
                    stringResource(R.string.schedule_overdue_by_days, absDays)
                } else {
                    stringResource(R.string.schedule_due_in_days, absDays)
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Last performed information
            val lastDoneText = when {
                schedule.lastPerformedAt != null || schedule.lastPerformedOdometerKm != null -> {
                    val parts = mutableListOf<String>()
                    schedule.lastPerformedAt?.let { parts.add(Format.date(it, locale)) }
                    schedule.lastPerformedOdometerKm?.let {
                        parts.add(
                            stringResource(
                                vehicle.distanceUnit.shortRes(),
                                Format.distance(it, vehicle.distanceUnit, locale),
                            ),
                        )
                    }
                    stringResource(R.string.schedule_last_done, parts.joinToString(" · "))
                }
                else -> stringResource(R.string.schedule_never_performed)
            }
            Text(
                text = lastDoneText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.action_edit))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onMarkDone) {
                    Text(stringResource(R.string.schedule_mark_done))
                }
            }
        }
    }
}
