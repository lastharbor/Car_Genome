package com.cargenome.app.ui.common

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cargenome.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun AppDatePickerDialog(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.action_pick),
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate ?: today) }
    var currentYearMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.of(selectedDate.year, selectedDate.month))
    }
    var isYearPickerMode by remember { mutableStateOf(false) }

    val locale = LocalConfiguration.current.locales[0]

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header: Title & Selected Date
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Format.date(selectedDate, locale),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                // Month / Year toggle header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val monthName = currentYearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isYearPickerMode = !isYearPickerMode }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$monthName ${currentYearMonth.year}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Icon(
                            imageVector = if (isYearPickerMode) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!isYearPickerMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.calendar_prev_month),
                                )
                            }
                            IconButton(
                                onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.calendar_next_month),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Content: Year picker or Calendar grid
                if (isYearPickerMode) {
                    val years = remember { (1950..(today.year + 20)).toList() }
                    val selectedYearIndex = remember(currentYearMonth.year) {
                        years.indexOf(currentYearMonth.year).coerceAtLeast(0)
                    }
                    val gridState = rememberLazyGridState()

                    LaunchedEffect(selectedYearIndex) {
                        val target = (selectedYearIndex - 3).coerceAtLeast(0)
                        gridState.scrollToItem(target)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(years) { year ->
                            val isSelected = year == currentYearMonth.year
                            val isCurrentYear = year == today.year

                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                    .then(
                                        if (isCurrentYear && !isSelected) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        } else Modifier,
                                    )
                                    .clickable {
                                        currentYearMonth = YearMonth.of(year, currentYearMonth.month)
                                        selectedDate = runCatching { selectedDate.withYear(year) }
                                            .getOrDefault(LocalDate.of(year, currentYearMonth.month, 1))
                                        isYearPickerMode = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected || isCurrentYear) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                } else {
                    // Weekday headers (2-letter abbreviations in Russian: Пн, Вт, Ср, Чт, Пт, Сб, Вс)
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

                    // Calendar days grid
                    val firstDayOfMonth = currentYearMonth.atDay(1)
                    val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value - 1 // Monday = 0
                    val daysInMonth = currentYearMonth.lengthOfMonth()
                    val prevMonth = currentYearMonth.minusMonths(1)
                    val daysInPrevMonth = prevMonth.lengthOfMonth()
                    val totalGridSlots = ((dayOfWeekOffset + daysInMonth + 6) / 7) * 7

                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                        dayNumber = daysInPrevMonth - (dayOfWeekOffset - slotIndex - 1)
                                        isCurrentMonth = false
                                        slotDate = prevMonth.atDay(dayNumber)
                                    } else if (slotIndex < dayOfWeekOffset + daysInMonth) {
                                        dayNumber = slotIndex - dayOfWeekOffset + 1
                                        isCurrentMonth = true
                                        slotDate = currentYearMonth.atDay(dayNumber)
                                    } else {
                                        dayNumber = slotIndex - (dayOfWeekOffset + daysInMonth) + 1
                                        isCurrentMonth = false
                                        slotDate = currentYearMonth.plusMonths(1).atDay(dayNumber)
                                    }

                                    val isSelected = slotDate == selectedDate
                                    val isToday = slotDate == today

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else androidx.compose.ui.graphics.Color.Transparent,
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                } else Modifier,
                                            )
                                            .clickable {
                                                selectedDate = slotDate
                                                if (!isCurrentMonth) {
                                                    currentYearMonth = YearMonth.of(slotDate.year, slotDate.month)
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                            ),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            if (week < (totalGridSlots / 7) - 1) {
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions: Cancel and OK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onDateSelected(selectedDate)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}
