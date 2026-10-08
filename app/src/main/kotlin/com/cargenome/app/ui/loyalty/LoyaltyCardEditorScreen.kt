package com.cargenome.app.ui.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.BarcodeType
import com.cargenome.app.data.db.entity.LoyaltyCategory
import com.cargenome.app.data.db.entity.displayName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoyaltyCardEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LoyaltyCardEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showScannerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collect {
            onSaved()
        }
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            onDismiss = { showScannerDialog = false },
            onBarcodeScanned = { raw, type ->
                viewModel.onBarcodeScanned(raw, type)
                showScannerDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.loyalty_cards_edit else R.string.loyalty_cards_add,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Visual Card Preview
            LoyaltyCardPreviewCard(
                title = state.title.ifBlank { stringResource(R.string.loyalty_cards_card_title) },
                cardNumber = state.cardNumber.ifBlank { "•••• •••• ••••" },
                category = state.category,
                colorHex = state.colorHex,
            )

            // Scan Barcode button
            OutlinedButton(
                onClick = { showScannerDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.loyalty_cards_scan_camera))
            }

            // Card Title Field
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.loyalty_cards_card_title)) },
                isError = state.titleError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )

            // Card Number Field
            OutlinedTextField(
                value = state.cardNumber,
                onValueChange = viewModel::onCardNumberChanged,
                label = { Text(stringResource(R.string.loyalty_cards_card_number)) },
                isError = state.numberError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showScannerDialog = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
            )

            // Category Selection
            Text(
                text = stringResource(R.string.loyalty_cards_category),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LoyaltyCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { viewModel.onCategoryChanged(cat) },
                        label = { Text(getCategoryTitle(cat)) },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            // Card Color Picker
            Text(
                text = stringResource(R.string.loyalty_cards_color),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PRESET_CARD_COLORS.forEach { hex ->
                    val color = Color(hex)
                    val isSelected = state.colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.onColorChanged(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Barcode Format Picker
            var formatExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = !formatExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.barcodeType.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Barcode Format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false },
                ) {
                    BarcodeType.entries.forEach { bType ->
                        DropdownMenuItem(
                            text = { Text(bType.name) },
                            onClick = {
                                viewModel.onBarcodeTypeChanged(bType)
                                formatExpanded = false
                            },
                        )
                    }
                }
            }

            // Vehicle Binding
            if (state.vehicles.isNotEmpty()) {
                var vehicleExpanded by remember { mutableStateOf(false) }
                val selectedVehicleName = state.vehicles.firstOrNull { it.id == state.vehicleId }?.displayName()
                    ?: stringResource(R.string.loyalty_cards_vehicle_all)

                ExposedDropdownMenuBox(
                    expanded = vehicleExpanded,
                    onExpandedChange = { vehicleExpanded = !vehicleExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedVehicleName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.loyalty_cards_vehicle_bind)) },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = vehicleExpanded,
                        onDismissRequest = { vehicleExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.loyalty_cards_vehicle_all)) },
                            onClick = {
                                viewModel.onVehicleIdChanged(null)
                                vehicleExpanded = false
                            },
                        )
                        state.vehicles.forEach { veh ->
                            DropdownMenuItem(
                                text = { Text(veh.displayName()) },
                                onClick = {
                                    viewModel.onVehicleIdChanged(veh.id)
                                    vehicleExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Note Field
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChanged,
                label = { Text(stringResource(R.string.loyalty_cards_note)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun LoyaltyCardPreviewCard(
    title: String,
    cardNumber: String,
    category: LoyaltyCategory,
    colorHex: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(colorHex)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(category),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = getCategoryTitle(category),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "CARD NUMBER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = cardNumber,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

fun getCategoryIcon(category: LoyaltyCategory): ImageVector = when (category) {
    LoyaltyCategory.Fuel -> Icons.Default.LocalGasStation
    LoyaltyCategory.Wash -> Icons.Default.LocalCarWash
    LoyaltyCategory.Service -> Icons.Default.Build
    LoyaltyCategory.Parts -> Icons.Default.ShoppingBag
    LoyaltyCategory.Insurance -> Icons.Default.Security
    LoyaltyCategory.Other -> Icons.Default.Tag
}

@Composable
fun getCategoryTitle(category: LoyaltyCategory): String = when (category) {
    LoyaltyCategory.Fuel -> stringResource(R.string.loyalty_cards_category_fuel)
    LoyaltyCategory.Wash -> stringResource(R.string.loyalty_cards_category_wash)
    LoyaltyCategory.Service -> stringResource(R.string.loyalty_cards_category_service)
    LoyaltyCategory.Parts -> stringResource(R.string.loyalty_cards_category_parts)
    LoyaltyCategory.Insurance -> stringResource(R.string.loyalty_cards_category_insurance)
    LoyaltyCategory.Other -> stringResource(R.string.loyalty_cards_category_other)
}
