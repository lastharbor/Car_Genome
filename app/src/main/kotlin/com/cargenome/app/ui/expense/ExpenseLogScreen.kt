package com.cargenome.app.ui.expense

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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.SectionCard
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.shortRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseLogScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_title)) },
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
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                text = { Text(stringResource(R.string.expense_add)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
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

                if (state.expenses.isEmpty()) {
                    if (!state.isLoading) item { EmptyExpenseCard() }
                } else {
                    item {
                        SectionCard(stringResource(R.string.expense_title)) {
                            DetailRow(
                                label = stringResource(R.string.service_summary_total),
                                value = Format.money(state.totalSpendMinor, vehicle.currencyCode, LocalConfiguration.current.locales[0]),
                            )
                        }
                    }

                    items(
                        items = state.expenses,
                        key = { it.id },
                        contentType = { "expense_record" },
                    ) { expense ->
                        ExpenseRow(
                            expense = expense,
                            vehicle = vehicle,
                            onClick = { onOpenExpense(expense.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExpenseCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.expense_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.expense_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
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
                    text = Format.date(expense.incurredAt, locale),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Format.money(expense.amountMinor, vehicle.currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = expense.title,
                style = MaterialTheme.typography.bodyLarge,
            )

            expense.odometerKm?.let { km ->
                Text(
                    text = stringResource(
                        vehicle.distanceUnit.shortRes(),
                        Format.distance(km, vehicle.distanceUnit, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            expense.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(expense.category.labelRes())) },
            )
        }
    }
}
