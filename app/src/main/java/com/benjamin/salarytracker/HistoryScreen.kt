package com.benjamin.salarytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    job: Job,
    entries: List<DayEntry>,
    connectionStatus: ConnectionStatus,
    isSubscribed: Boolean = false,
    onAddEntry: (LocalDate) -> Unit,
    onEditEntry: (DayEntry) -> Unit,
    onSettings: () -> Unit,
    onSelectJob: () -> Unit
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    val monthEntries = remember(entries, selectedMonth) {
        entries.filter { YearMonth.from(it.date) == selectedMonth }.sortedByDescending { it.date }
    }

    val daysInMonth = selectedMonth.lengthOfMonth()
    val firstDayOfWeek = selectedMonth.atDay(1).dayOfWeek.value // 1 = Lun, 7 = Dim

    val entriesByDate = remember(entries) {
        entries.associateBy { it.date }
    }

    val netPerHour = job.hourlyRateBrut * SalaryCalculator.NET_COEFFICIENT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(top = 20.dp, bottom = 100.dp), // space for bottom bar
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    ConnectionTag(status = connectionStatus, modifier = Modifier.padding(bottom = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.hist_header), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            Text(stringResource(R.string.hist_subtitle), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        ProfileIconButton(isSubscribed = isSubscribed, onClick = onSettings)
                        Spacer(Modifier.width(10.dp))
                        SquareIconButton(Icons.Default.Work, onClick = onSelectJob, active = true)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Calendar Card
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column {
                        // Month Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                                Icon(Icons.Default.ChevronLeft, null, tint = Ink)
                            }
                            Text(
                                text = selectedMonth.format(formatter).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                                Icon(Icons.Default.ChevronRight, null, tint = Ink)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Weekdays Header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val headers = listOf(
                                stringResource(R.string.dash_wd_mon), stringResource(R.string.dash_wd_tue),
                                stringResource(R.string.dash_wd_wed), stringResource(R.string.dash_wd_thu),
                                stringResource(R.string.dash_wd_fri), stringResource(R.string.dash_wd_sat),
                                stringResource(R.string.dash_wd_sun)
                            )
                            headers.forEach { h ->
                                Text(
                                    text = h,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = InkMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Grid Days
                        val totalCells = ((firstDayOfWeek - 1) + daysInMonth)
                        val rows = (totalCells + 6) / 7

                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - (firstDayOfWeek - 2)

                                    if (dayNum in 1..daysInMonth) {
                                        val date = selectedMonth.atDay(dayNum)
                                        val entry = entriesByDate[date]

                                        val isToday = date == LocalDate.now()
                                        val cellColor = when {
                                            entry == null -> Color.Transparent
                                            entry.isLeave -> MaterialTheme.colorScheme.surfaceVariant
                                            entry.totalHours > 8.0 -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }

                                        val cellTextColor = when {
                                            entry == null -> Ink
                                            entry.isLeave -> InkMuted
                                            entry.totalHours > 8.0 -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cellColor)
                                                .then(
                                                    if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                    else if (entry == null) Modifier.border(0.5.dp, OutlineLt, RoundedCornerShape(8.dp))
                                                    else Modifier
                                                )
                                                .clickable {
                                                    if (entry != null) onEditEntry(entry)
                                                    else onAddEntry(date)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    color = cellTextColor,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (entry != null && !entry.isLeave) {
                                                    val hrs = entry.totalHours
                                                    val hrsStr = if (hrs == hrs.toLong().toDouble()) "${hrs.toLong()}" else String.format(Locale.getDefault(), "%.1f", hrs)
                                                    // Heures + prix collés ensemble dans une sous-colonne sans espacement
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(0.dp)
                                                    ) {
                                                        Text(
                                                            text = "${hrsStr}h",
                                                            color = cellTextColor.copy(alpha = 0.85f),
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            lineHeight = 11.sp
                                                        )
                                                        val netDay = hrs * netPerHour
                                                        Text(
                                                            text = String.format(Locale.getDefault(), "%.0f€", netDay),
                                                            color = cellTextColor.copy(alpha = 0.6f),
                                                            fontSize = 8.sp,
                                                            maxLines = 1,
                                                            lineHeight = 10.sp
                                                        )
                                                    }
                                                } else if (entry != null && entry.isLeave) {
                                                    Text(
                                                        text = stringResource(R.string.common_leave),
                                                        color = cellTextColor.copy(alpha = 0.8f),
                                                        fontSize = 9.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // List Title / Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hist_entries_of, selectedMonth.format(formatter).replaceFirstChar { it.lowercase() }),
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.hist_days_count, monthEntries.size),
                        color = InkMuted,
                        fontSize = 13.sp
                    )
                }
            }

            if (monthEntries.isEmpty()) {
                item {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = 24.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.hist_no_entries),
                                color = Ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.hist_no_entries_hint),
                                color = InkMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            AppButton(
                                text = stringResource(R.string.hist_add_hour),
                                onClick = { onAddEntry(selectedMonth.atDay(1)) },
                                leading = Icons.Default.Add,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                items(monthEntries, key = { it.id }) { entry ->
                    Appear {
                        EntryRow(entry = entry, onClick = { onEditEntry(entry) }, hourlyRateBrut = job.hourlyRateBrut)
                    }
                }
            }
        }
    }
}
