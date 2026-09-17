package com.cargenome.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.BuildConfig
import com.cargenome.app.R
import com.cargenome.app.data.settings.ThemeMode
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.nameRes
import java.time.LocalDate

private val ReminderIntervalOptions = listOf(
    0 to R.string.settings_reminder_interval_immediate,
    15 to R.string.settings_reminder_interval_15m,
    30 to R.string.settings_reminder_interval_30m,
    60 to R.string.settings_reminder_interval_1h,
    120 to R.string.settings_reminder_interval_2h,
    240 to R.string.settings_reminder_interval_4h,
    1440 to R.string.settings_reminder_interval_1d,
)

private val ReminderStartTimePresets = listOf(
    7 * 60 to "07:00",
    8 * 60 to "08:00",
    9 * 60 to "09:00",
    10 * 60 to "10:00",
    11 * 60 to "11:00",
    12 * 60 to "12:00",
    14 * 60 to "14:00",
    18 * 60 to "18:00",
    20 * 60 to "20:00",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val appContext = context.applicationContext

    var showClearDialog by remember { mutableStateOf(false) }
    var showPromoDialog by remember { mutableStateOf(false) }
    var promoCodeInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(event) {
        when (val e = event) {
            is SettingsEvent.Success -> {
                val msg = if (e.arg != null) appContext.getString(e.messageRes, e.arg) else appContext.getString(e.messageRes)
                snackbarHostState.showSnackbar(msg)
                viewModel.clearEvent()
            }
            is SettingsEvent.Error -> {
                val msg = if (e.arg != null) appContext.getString(e.messageRes, e.arg) else appContext.getString(e.messageRes)
                snackbarHostState.showSnackbar(msg)
                viewModel.clearEvent()
            }
            null -> Unit
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { stream ->
                viewModel.exportBackup(stream)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let { stream ->
                viewModel.importBackup(stream)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val direction = LocalLayoutDirection.current

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(
                    start = 16.dp + sides.calculateStartPadding(direction),
                    end = 16.dp + sides.calculateEndPadding(direction),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.settings_section_appearance)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_theme_mode),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ThemeMode.entries.forEachIndexed { index, mode ->
                                    val labelRes = when (mode) {
                                        ThemeMode.System -> R.string.settings_theme_system
                                        ThemeMode.Light -> R.string.settings_theme_light
                                        ThemeMode.Dark -> R.string.settings_theme_dark
                                    }
                                    SegmentedButton(
                                        selected = settings.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ThemeMode.entries.size,
                                        ),
                                        modifier = Modifier.weight(1f),
                                        icon = {},
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_dynamic_color),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_dynamic_color_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = settings.dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) },
                                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_amoled),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_amoled_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = settings.amoledDark,
                                    onCheckedChange = { viewModel.setAmoledDark(it) },
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.settings_section_defaults)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.field_distance_unit),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                DistanceUnit.entries.forEachIndexed { index, unit ->
                                    SegmentedButton(
                                        selected = settings.defaultDistanceUnit == unit,
                                        onClick = { viewModel.setDefaultDistanceUnit(unit) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = DistanceUnit.entries.size,
                                        ),
                                        modifier = Modifier.weight(1f),
                                        icon = {},
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(unit.nameRes()),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.field_volume_unit),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                VolumeUnit.entries.forEachIndexed { index, unit ->
                                    SegmentedButton(
                                        selected = settings.defaultVolumeUnit == unit,
                                        onClick = { viewModel.setDefaultVolumeUnit(unit) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = VolumeUnit.entries.size,
                                        ),
                                        modifier = Modifier.weight(1f),
                                        icon = {},
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(unit.nameRes()),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            var currencyText by remember(settings.defaultCurrencyCode) {
                                mutableStateOf(settings.defaultCurrencyCode)
                            }
                            OutlinedTextField(
                                value = currencyText,
                                onValueChange = { input ->
                                    val clean = input.filter { it.isLetter() }.take(4).uppercase()
                                    currencyText = clean
                                    if (clean.isNotBlank()) {
                                        viewModel.setDefaultCurrencyCode(clean)
                                    }
                                },
                                label = { Text(stringResource(R.string.field_currency)) },
                                placeholder = { Text("RUB") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.notification_channel_maintenance)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_persistent_maintenance_notification),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_persistent_maintenance_notification_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = settings.persistentMaintenanceNotification,
                                    onCheckedChange = { viewModel.setPersistentMaintenanceNotification(it) },
                                )
                            }

                            if (settings.persistentMaintenanceNotification) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_reminder_interval_title),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_reminder_interval_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    var intervalMenuExpanded by remember { mutableStateOf(false) }
                                    val currentLabelRes = ReminderIntervalOptions.find { it.first == settings.maintenanceReminderIntervalMinutes }?.second
                                        ?: R.string.settings_reminder_interval_30m
                                    val currentLabel = stringResource(currentLabelRes)

                                    ExposedDropdownMenuBox(
                                        expanded = intervalMenuExpanded,
                                        onExpandedChange = { intervalMenuExpanded = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = currentLabel,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalMenuExpanded) },
                                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        )
                                        ExposedDropdownMenu(
                                            expanded = intervalMenuExpanded,
                                            onDismissRequest = { intervalMenuExpanded = false },
                                        ) {
                                            ReminderIntervalOptions.forEach { (minutes, labelRes) ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(labelRes)) },
                                                    onClick = {
                                                        viewModel.setMaintenanceReminderIntervalMinutes(minutes)
                                                        intervalMenuExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_reminder_start_time_title),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = stringResource(R.string.settings_reminder_start_time_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                var startTimeMenuExpanded by remember { mutableStateOf(false) }
                                var showTimePickerDialog by remember { mutableStateOf(false) }

                                val formattedCurrentStartTime = remember(settings.maintenanceReminderStartTimeMinutes) {
                                    val h = settings.maintenanceReminderStartTimeMinutes / 60
                                    val m = settings.maintenanceReminderStartTimeMinutes % 60
                                    String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
                                }

                                ExposedDropdownMenuBox(
                                    expanded = startTimeMenuExpanded,
                                    onExpandedChange = { startTimeMenuExpanded = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                ) {
                                    OutlinedTextField(
                                        value = formattedCurrentStartTime,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startTimeMenuExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth(),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = startTimeMenuExpanded,
                                        onDismissRequest = { startTimeMenuExpanded = false },
                                    ) {
                                        ReminderStartTimePresets.forEach { (minutes, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    viewModel.setMaintenanceReminderStartTimeMinutes(minutes)
                                                    startTimeMenuExpanded = false
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.settings_reminder_start_time_custom)) },
                                            onClick = {
                                                startTimeMenuExpanded = false
                                                showTimePickerDialog = true
                                            },
                                        )
                                    }
                                }

                                if (showTimePickerDialog) {
                                    val timePickerState = rememberTimePickerState(
                                        initialHour = settings.maintenanceReminderStartTimeMinutes / 60,
                                        initialMinute = settings.maintenanceReminderStartTimeMinutes % 60,
                                        is24Hour = true,
                                    )
                                    AlertDialog(
                                        onDismissRequest = { showTimePickerDialog = false },
                                        title = { Text(stringResource(R.string.settings_reminder_start_time_title)) },
                                        text = {
                                            TimePicker(state = timePickerState)
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val minutes = timePickerState.hour * 60 + timePickerState.minute
                                                    viewModel.setMaintenanceReminderStartTimeMinutes(minutes)
                                                    showTimePickerDialog = false
                                                },
                                            ) {
                                                Text(stringResource(R.string.action_ok))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showTimePickerDialog = false }) {
                                                Text(stringResource(R.string.action_cancel))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.settings_promo_dialog_title)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                onClick = { showPromoDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.settings_redeem_promo),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_promo_dialog_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.settings_section_data)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                onClick = {
                                    val filename = "cargenome_backup_${LocalDate.now()}.json"
                                    exportLauncher.launch(filename)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_backup_export),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_backup_export_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Card(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_backup_import),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_backup_import_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Card(
                                onClick = { viewModel.seedDemoData() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_demo_data),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_demo_data_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showClearDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.settings_clear_data))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_data_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showPromoDialog) {
        AlertDialog(
            onDismissRequest = {
                showPromoDialog = false
                promoCodeInput = ""
            },
            title = { Text(stringResource(R.string.settings_promo_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_promo_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = promoCodeInput,
                        onValueChange = { promoCodeInput = it },
                        label = { Text(stringResource(R.string.settings_redeem_promo)) },
                        placeholder = { Text(stringResource(R.string.settings_promo_code_placeholder)) },
                        maxLines = 3,
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        promoCodeInput = clip.trim()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.settings_promo_paste))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = promoCodeInput
                        showPromoDialog = false
                        promoCodeInput = ""
                        viewModel.redeemPromoCode(code)
                    },
                    enabled = promoCodeInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.settings_promo_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPromoDialog = false
                        promoCodeInput = ""
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}


