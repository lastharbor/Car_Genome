package com.cargenome.app.ui.vehicle

import android.net.Uri
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.cargenome.app.ui.common.AppDatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.PdfViewerDialog
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.TagBadge
import com.cargenome.app.ui.common.displayName
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.nameRes
import com.cargenome.app.ui.common.shortRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val PopularInsurers = listOf(
    "Тинькофф", "Ингосстрах", "АльфаСтрахование", "РЕСО-Гарантия",
    "СОГАЗ", "ВСК", "СберСтрахование", "Росгосстрах",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOdometerLog: (Long) -> Unit = {},
    onOpenExpenses: (Long) -> Unit = {},
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showInsuranceDialog by remember { mutableStateOf(false) }
    var showPdfViewer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull()
            viewModel.attachInsurancePdf(uri, fileName)
        }
    }

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.vehicle?.displayName() ?: stringResource(R.string.vehicle_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val vehicle = state.vehicle
                    if (vehicle != null) {
                        TextButton(onClick = { onEdit(vehicle.id) }) {
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                },
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
                if (vehicle == null) {
                    if (!state.isLoading) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.vehicle_missing),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Button(onClick = onBack) {
                                        Text(stringResource(R.string.action_back))
                                    }
                                }
                            }
                        }
                    }
                    return@LazyColumn
                }

                item {
                    MileageCard(
                        vehicle = vehicle,
                        currentKm = state.currentOdometerKm,
                        onClick = { onOpenOdometerLog(vehicle.id) },
                    )
                }
                item {
                    Card(
                        onClick = { onOpenExpenses(vehicle.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.expense_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.vehicle_link_expenses),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.expense_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { SpecificationCard(vehicle) }
                item {
                    InsuranceCard(
                        vehicle = vehicle,
                        onAddOrEdit = { showInsuranceDialog = true },
                        onViewPdf = { showPdfViewer = true },
                        onAttachPdf = { pdfPickerLauncher.launch("application/pdf") },
                        onRemovePdf = viewModel::removeInsurancePdf,
                    )
                }
                state.nextMaintenanceEvent?.let { nextEvent ->
                    item {
                        NextMaintenanceCard(
                            event = nextEvent,
                            vehicle = vehicle,
                        )
                    }
                }
                item { PreferencesCard(vehicle) }
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.vehicle_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vehicle_delete_title)) },
            text = { Text(stringResource(R.string.vehicle_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.vehicle_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showInsuranceDialog && state.vehicle != null) {
        val currentVehicle = state.vehicle!!
        val hasExistingInsurance = !currentVehicle.insuranceProvider.isNullOrBlank() ||
            !currentVehicle.insurancePolicyNumber.isNullOrBlank() ||
            currentVehicle.insuranceExpiresOn != null ||
            !currentVehicle.insurancePdfUri.isNullOrBlank()

        InsuranceEditDialog(
            initialProvider = currentVehicle.insuranceProvider,
            initialPolicyNumber = currentVehicle.insurancePolicyNumber,
            initialExpiresOn = currentVehicle.insuranceExpiresOn,
            hasPdf = !currentVehicle.insurancePdfUri.isNullOrBlank(),
            onAttachPdf = { pdfPickerLauncher.launch("application/pdf") },
            onRemovePdf = viewModel::removeInsurancePdf,
            onDismiss = { showInsuranceDialog = false },
            onSave = { provider, policyNumber, expiresOn ->
                viewModel.updateInsurance(provider, policyNumber, expiresOn)
            },
            onDelete = if (hasExistingInsurance) {
                { viewModel.updateInsurance(null, null, null, clearPdf = true) }
            } else null,
        )
    }

    if (showPdfViewer && state.vehicle?.insurancePdfUri != null) {
        PdfViewerDialog(
            pdfUriString = state.vehicle!!.insurancePdfUri!!,
            onDismiss = { showPdfViewer = false },
            title = stringResource(
                R.string.insurance_pdf_title,
                state.vehicle?.displayName() ?: stringResource(R.string.vehicle_default_name),
            ),
        )
    }
}

@Composable
private fun MileageCard(
    vehicle: VehicleEntity,
    currentKm: Double?,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.vehicle_current_mileage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = currentKm?.let {
                        stringResource(
                            vehicle.distanceUnit.shortRes(),
                            Format.distance(it, vehicle.distanceUnit, locale),
                        )
                    } ?: stringResource(R.string.garage_no_mileage),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.odometer_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpecificationCard(vehicle: VehicleEntity) {
    val locale = LocalConfiguration.current.locales[0]

    SectionCard(stringResource(R.string.vehicle_section_identity)) {
        DetailRow(stringResource(R.string.field_make), vehicle.make)
        vehicle.model.takeIf { it.isNotBlank() }
            ?.let { DetailRow(stringResource(R.string.field_model), it) }
        vehicle.modelYear?.let { DetailRow(stringResource(R.string.field_year), it.toString()) }
        vehicle.trim?.let { DetailRow(stringResource(R.string.field_trim), it) }
        vehicle.engine?.let { DetailRow(stringResource(R.string.field_engine), it) }
        DetailRow(stringResource(R.string.field_fuel), stringResource(vehicle.fuelType.labelRes()))
        vehicle.plateNumber?.let { DetailRow(stringResource(R.string.field_plate), it) }
        vehicle.vin?.let { DetailRow(stringResource(R.string.vin_short), it) }
        vehicle.purchasedOn?.let {
            DetailRow(stringResource(R.string.field_purchase_date), Format.date(it, locale))
        }
    }
}

@Composable
private fun InsuranceCard(
    vehicle: VehicleEntity,
    onAddOrEdit: () -> Unit,
    onViewPdf: () -> Unit,
    onAttachPdf: () -> Unit,
    onRemovePdf: () -> Unit,
) {
    val hasInsurance = !vehicle.insuranceProvider.isNullOrBlank() ||
        !vehicle.insurancePolicyNumber.isNullOrBlank() ||
        vehicle.insuranceExpiresOn != null ||
        !vehicle.insurancePdfUri.isNullOrBlank()

    SectionCard(stringResource(R.string.vehicle_section_insurance)) {
        if (!hasInsurance) {
            Text(
                text = stringResource(R.string.insurance_not_added),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddOrEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.insurance_add_action))
                }
                OutlinedButton(
                    onClick = onAttachPdf,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📄 " + stringResource(R.string.insurance_pdf_attach_action))
                }
            }
        } else {
            val locale = LocalConfiguration.current.locales[0]
            val today = remember { LocalDate.now() }
            val expiresOn = vehicle.insuranceExpiresOn

            if (expiresOn != null) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, expiresOn)
                val (statusText, badgeColor, textColor) = when {
                    days < 0 -> Triple(
                        stringResource(R.string.insurance_status_expired),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                    days <= 30 -> Triple(
                        stringResource(R.string.insurance_status_expiring_soon, days.toInt()),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    else -> Triple(
                        stringResource(R.string.insurance_status_active),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                    )
                }
            }

            vehicle.insuranceProvider?.let { DetailRow(stringResource(R.string.field_insurance_provider), it) }
            vehicle.insurancePolicyNumber?.let { DetailRow(stringResource(R.string.field_insurance_policy), it) }
            vehicle.insuranceExpiresOn?.let {
                DetailRow(stringResource(R.string.field_insurance_expires), Format.date(it, locale))
            }

            // PDF Document Presentation Card
            if (vehicle.insurancePdfUri != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "PDF",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.insurance_pdf_attached),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            IconButton(
                                onClick = onRemovePdf,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.insurance_pdf_remove_action),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Button(
                            onClick = onViewPdf,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.insurance_pdf_view_action))
                        }

                        OutlinedButton(
                            onClick = onAttachPdf,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.insurance_pdf_replace_action))
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAttachPdf,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.insurance_pdf_attach_action))
                }
            }

            OutlinedButton(
                onClick = onAddOrEdit,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.insurance_edit_action))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsuranceEditDialog(
    initialProvider: String?,
    initialPolicyNumber: String?,
    initialExpiresOn: LocalDate?,
    hasPdf: Boolean = false,
    onAttachPdf: () -> Unit = {},
    onRemovePdf: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (provider: String, policyNumber: String, expiresOn: LocalDate?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var provider by remember { mutableStateOf(initialProvider.orEmpty()) }
    var policyNumber by remember { mutableStateOf(initialPolicyNumber.orEmpty()) }
    var expiresOn by remember { mutableStateOf(initialExpiresOn) }
    var showDatePicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialProvider.isNullOrBlank() && initialPolicyNumber.isNullOrBlank() && initialExpiresOn == null && !hasPdf) {
                        R.string.insurance_dialog_title_add
                    } else {
                        R.string.insurance_dialog_title_edit
                    }
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text(stringResource(R.string.field_insurance_provider)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                com.cargenome.app.ui.common.PresetStringsRow(
                    label = stringResource(R.string.insurance_presets_label),
                    presets = PopularInsurers,
                    selected = provider,
                    onSelect = { provider = it },
                )

                OutlinedTextField(
                    value = policyNumber,
                    onValueChange = { policyNumber = it },
                    label = { Text(stringResource(R.string.field_insurance_policy)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = expiresOn?.let { Format.date(it, locale) }.orEmpty(),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.field_insurance_expires)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (expiresOn != null) {
                                TextButton(onClick = { expiresOn = null }) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                            TextButton(onClick = { showDatePicker = true }) {
                                Text(stringResource(R.string.action_pick))
                            }
                        }
                    },
                    singleLine = true,
                )

                // PDF Attachment in dialog
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasPdf) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (hasPdf) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = if (hasPdf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasPdf) stringResource(R.string.insurance_pdf_attached) else stringResource(R.string.insurance_pdf_label),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (hasPdf) stringResource(R.string.insurance_pdf_replace_hint) else stringResource(R.string.insurance_pdf_attach_action),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!hasPdf) {
                            FilledTonalButton(
                                onClick = onAttachPdf,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.action_pick))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = onAttachPdf,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.insurance_pdf_replace_action),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onRemovePdf,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.insurance_pdf_remove_action),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(provider, policyNumber, expiresOn)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                    ) {
                        Text(
                            stringResource(R.string.insurance_delete_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = expiresOn,
            onDateSelected = { expiresOn = it },
            onDismiss = { showDatePicker = false },
            title = stringResource(R.string.field_insurance_expires),
        )
    }
}

@Composable
private fun PreferencesCard(vehicle: VehicleEntity) {
    SectionCard(stringResource(R.string.vehicle_section_units)) {
        DetailRow(
            stringResource(R.string.field_distance_unit),
            stringResource(vehicle.distanceUnit.nameRes()),
        )
        DetailRow(
            stringResource(R.string.field_volume_unit),
            stringResource(vehicle.volumeUnit.nameRes()),
        )
        DetailRow(stringResource(R.string.field_currency), vehicle.currencyCode)
    }
}

@Composable
private fun NextMaintenanceCard(
    event: MaintenanceEventEntity,
    vehicle: VehicleEntity,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }

    SectionCard(title = stringResource(R.string.vehicle_card_next_service)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )

                val isOverdue = event.scheduledDate.isBefore(today)
                val isToday = event.scheduledDate == today
                when {
                    isOverdue -> TagBadge(
                        text = stringResource(R.string.event_status_overdue),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        textColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    isToday -> TagBadge(
                        text = stringResource(R.string.event_status_today),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    else -> TagBadge(
                        text = stringResource(R.string.event_status_planned),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            val dateFormatted = Format.date(event.scheduledDate, locale)
            val timeFormatted = event.scheduledTimeMinutes?.let { minutes ->
                val h = minutes / 60
                val m = minutes % 60
                String.format(locale, "%02d:%02d", h, m)
            }
            val dateTimeString = if (timeFormatted != null) "$dateFormatted, $timeFormatted" else dateFormatted

            DetailRow(
                label = stringResource(R.string.event_field_date),
                value = dateTimeString,
            )

            event.targetOdometerKm?.let { km ->
                val distStr = Format.distance(km, vehicle.distanceUnit, locale)
                DetailRow(
                    label = stringResource(R.string.event_field_odometer),
                    value = stringResource(vehicle.distanceUnit.shortRes(), distStr),
                )
            }

            if (!event.shop.isNullOrBlank()) {
                DetailRow(
                    label = stringResource(R.string.event_field_shop),
                    value = event.shop,
                )
            }
        }
    }
}

