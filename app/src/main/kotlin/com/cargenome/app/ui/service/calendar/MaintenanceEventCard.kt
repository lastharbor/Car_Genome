package com.cargenome.app.ui.service.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.ui.common.DetailRow
import com.cargenome.app.ui.common.Format
import com.cargenome.app.ui.common.TagBadge
import com.cargenome.app.ui.common.labelRes
import com.cargenome.app.ui.common.shortRes
import java.time.LocalDate

@Composable
fun MaintenanceEventCard(
    event: MaintenanceEventEntity,
    vehicle: VehicleEntity,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val locale = LocalConfiguration.current.locales[0]

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TagBadge(
                            text = stringResource(event.category.labelRes()),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (event.scheduleId != null) {
                            TagBadge(
                                text = "✓ " + stringResource(R.string.service_action_to_schedule),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Status tag
                when {
                    event.isCompleted -> {
                        TagBadge(
                            text = stringResource(R.string.event_status_completed),
                            containerColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF2E7D32),
                        )
                    }
                    event.scheduledDate.isBefore(today) -> {
                        TagBadge(
                            text = stringResource(R.string.event_status_overdue),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    event.scheduledDate == today -> {
                        TagBadge(
                            text = stringResource(R.string.event_status_today),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    else -> {
                        TagBadge(
                            text = stringResource(R.string.event_status_planned),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Details rows
            val dateFormatted = Format.date(event.scheduledDate, locale)
            val timeFormatted = event.scheduledTimeMinutes?.let { minutes ->
                val h = minutes / 60
                val m = minutes % 60
                String.format(locale, "%02d:%02d", h, m)
            }
            val dateTimeString = if (timeFormatted != null) {
                "$dateFormatted, $timeFormatted"
            } else {
                dateFormatted
            }

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

            event.estimatedCostMinor?.let { costMinor ->
                DetailRow(
                    label = stringResource(R.string.event_field_cost),
                    value = Format.money(costMinor, vehicle.currencyCode, locale),
                )
            }

            if (!event.shop.isNullOrBlank()) {
                DetailRow(
                    label = stringResource(R.string.event_field_shop),
                    value = event.shop,
                )
            }

            val reminderText = when (event.remindAdvanceDays) {
                0 -> stringResource(R.string.event_reminder_day_of)
                1 -> stringResource(R.string.event_reminder_1_day)
                3 -> stringResource(R.string.event_reminder_3_days)
                7 -> stringResource(R.string.event_reminder_7_days)
                else -> stringResource(R.string.event_reminder_none)
            }
            DetailRow(
                label = stringResource(R.string.event_field_reminder),
                value = reminderText,
            )

            if (!event.notes.isNullOrBlank()) {
                DetailRow(
                    label = stringResource(R.string.field_notes),
                    value = event.notes,
                )
            }

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!event.isCompleted) {
                    FilledTonalButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFE8F5E9),
                            contentColor = Color(0xFF2E7D32),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.event_action_complete))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.event_completed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
}
