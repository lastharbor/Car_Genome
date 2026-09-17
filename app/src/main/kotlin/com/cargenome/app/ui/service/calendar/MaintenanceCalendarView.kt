package com.cargenome.app.ui.service.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cargenome.app.R
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MaintenanceCalendarView(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<MaintenanceEventEntity>,
    modifier: Modifier = Modifier,
) {
    var currentYearMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.of(selectedDate.year, selectedDate.month))
    }

    val today = remember { LocalDate.now() }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

    // Map events by date for fast lookup
    val eventsByDate = remember(events) {
        events.groupBy { it.scheduledDate }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Month + Year header with navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.calendar_prev_month),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val monthName = currentYearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                    Text(
                        text = "$monthName ${currentYearMonth.year}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )

                    if (currentYearMonth != YearMonth.from(today)) {
                        SuggestionChip(
                            onClick = {
                                currentYearMonth = YearMonth.from(today)
                                onSelectDate(today)
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.calendar_today),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.height(26.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.calendar_next_month),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Weekdays row
            val daysOfWeek = remember {
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                daysOfWeek.forEach { dayOfWeek ->
                    val dayShort = dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                    Text(
                        text = dayShort,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Days Grid
            val firstDayOfMonth = currentYearMonth.atDay(1)
            val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value - 1 // Monday = 0
            val daysInMonth = currentYearMonth.lengthOfMonth()
            val prevMonth = currentYearMonth.minusMonths(1)
            val daysInPrevMonth = prevMonth.lengthOfMonth()

            val totalGridSlots = ((dayOfWeekOffset + daysInMonth + 6) / 7) * 7

            for (week in 0 until (totalGridSlots / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    for (col in 0..6) {
                        val slotIndex = week * 7 + col
                        val dayNumber: Int
                        val isCurrentMonth: Boolean
                        val slotDate: LocalDate

                        if (slotIndex < dayOfWeekOffset) {
                            // Day from previous month
                            dayNumber = daysInPrevMonth - (dayOfWeekOffset - slotIndex - 1)
                            isCurrentMonth = false
                            slotDate = prevMonth.atDay(dayNumber)
                        } else if (slotIndex < dayOfWeekOffset + daysInMonth) {
                            // Day of current month
                            dayNumber = slotIndex - dayOfWeekOffset + 1
                            isCurrentMonth = true
                            slotDate = currentYearMonth.atDay(dayNumber)
                        } else {
                            // Day of next month
                            dayNumber = slotIndex - (dayOfWeekOffset + daysInMonth) + 1
                            isCurrentMonth = false
                            slotDate = currentYearMonth.plusMonths(1).atDay(dayNumber)
                        }

                        val isSelected = slotDate == selectedDate
                        val isToday = slotDate == today
                        val dayEvents = eventsByDate[slotDate].orEmpty()

                        DayCell(
                            day = dayNumber,
                            isCurrentMonth = isCurrentMonth,
                            isSelected = isSelected,
                            isToday = isToday,
                            events = dayEvents,
                            today = today,
                            onClick = { onSelectDate(slotDate) },
                        )
                    }
                }
                if (week < (totalGridSlots / 7) - 1) {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<MaintenanceEventEntity>,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val backgroundModifier = when {
        isSelected -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
        isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        else -> Modifier
    }

    Column(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(backgroundModifier)
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
            ),
            color = textColor,
            textAlign = TextAlign.Center,
        )

        // Dot indicators for events
        if (events.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 1.dp),
            ) {
                val hasOverdue = events.any { !it.isCompleted && it.scheduledDate.isBefore(today) }
                val hasPlanned = events.any { !it.isCompleted && !it.scheduledDate.isBefore(today) }
                val hasCompleted = events.any { it.isCompleted }

                val dotColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    hasOverdue -> MaterialTheme.colorScheme.error
                    hasPlanned -> MaterialTheme.colorScheme.primary
                    hasCompleted -> Color(0xFF388E3C)
                    else -> MaterialTheme.colorScheme.secondary
                }

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(dotColor, CircleShape),
                )
            }
        }
    }
}
