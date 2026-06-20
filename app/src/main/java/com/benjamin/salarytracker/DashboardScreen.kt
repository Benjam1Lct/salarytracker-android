package com.benjamin.salarytracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign

@Composable
fun DashboardScreen(
    job: Job,
    entries: List<DayEntry>,
    templates: List<DayTemplate>,
    onAddEntry: () -> Unit,
    onEditEntry: (DayEntry) -> Unit,
    onCreateTemplate: () -> Unit,
    onSelectJob: () -> Unit,
    onSettings: () -> Unit,
    onUpdateTarget: (Double) -> Unit,
    connectionStatus: ConnectionStatus,
    isSubscribed: Boolean = false,
    onSeeAll: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    importStatus: ImportStatus = ImportStatus.Idle,
    onDismissImport: () -> Unit = {},
    activeSessionStartTime: Long = 0L,
    activeSessionPauseStartTime: Long = 0L,
    activeSessionTotalPauseMs: Long = 0L,
    onStartWorkday: () -> Unit = {},
    onStartPause: () -> Unit = {},
    onEndPause: () -> Unit = {},
    onEndWorkday: () -> Unit = {},
    onCancelWorkday: () -> Unit = {}
) {
    val stats = SalaryCalculator.calculateMonthStats(job, entries, YearMonth.now())
    val contrat = SalaryCalculator.calculateContractSummary(job, entries)

    val today = LocalDate.now()
    val currentMonth = YearMonth.now()
    
    // Filtrer les entrées du mois en cours
    val currentMonthEntries = entries.filter {
        it.date.year == currentMonth.year && it.date.monthValue == currentMonth.monthValue
    }
    
    // Compter les journées réellement travaillées
    val daysWorkedThisMonth = currentMonthEntries.count { it.totalHours > 0 && !it.isLeave }
    
    // Taux net moyen gagné par jour travaillé ce mois-ci
    val averageNetPerDay = if (daysWorkedThisMonth > 0) stats.salaireNetEstime / daysWorkedThisMonth else 0.0
    
    // Estimation d'un taux journalier par défaut
    val defaultDailyNet = job.hourlyRateBrut * 7.0 * 0.78
    val dailyNetRate = if (averageNetPerDay > 0.0) averageNetPerDay else defaultDailyNet
    
    // Somme restante pour atteindre l'objectif
    val remainingNet = (job.targetMonthlySalary - stats.salaireNetEstime).coerceAtLeast(0.0)
    
    // Calcul de la date de projection
    val targetReachable = stats.salaireNetEstime >= job.targetMonthlySalary
    var targetDateText = ""
    var targetWarningText: String? = null
    var targetColor: Color = MaterialTheme.colorScheme.primary

    if (targetReachable) {
        targetDateText = stringResource(R.string.dash_target_reached)
        targetColor = Color(0xFF10B981) // Vert
    } else if (dailyNetRate <= 0.1) {
        targetDateText = stringResource(R.string.dash_no_daily_rate)
        targetWarningText = stringResource(R.string.dash_set_hourly)
        targetColor = InkMuted
    } else {
        val remainingDaysNeeded = Math.ceil(remainingNet / dailyNetRate).toInt()

        // Trouver la date en ajoutant uniquement les jours de la semaine (lundi-vendredi) à partir de demain
        var projectDate = today
        var daysCounted = 0
        while (daysCounted < remainingDaysNeeded) {
            projectDate = projectDate.plusDays(1)
            if (projectDate.dayOfWeek.value in 1..5) {
                daysCounted++
            }
        }

        val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.getDefault())
        val formattedDate = projectDate.format(formatter)
        targetDateText = stringResource(R.string.dash_target_estimated_on, formattedDate)

        val endOfContract = job.endDate
        if (endOfContract != null) {
            val isBeforeEndOfContract = !projectDate.isAfter(endOfContract)
            if (!isBeforeEndOfContract) {
                targetColor = Color(0xFFEF4444) // Rouge
                val endFormatted = endOfContract.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                targetWarningText = stringResource(R.string.dash_unreachable_contract, endFormatted)
            } else {
                targetColor = MaterialTheme.colorScheme.primary
            }
        } else {
            val lastDayOfThisMonth = currentMonth.atEndOfMonth()
            val isBeforeEndOfMonth = !projectDate.isAfter(lastDayOfThisMonth)
            if (!isBeforeEndOfMonth) {
                targetColor = Color(0xFFEF4444) // Rouge
                targetWarningText = stringResource(R.string.dash_unreachable_month)
            } else {
                targetColor = MaterialTheme.colorScheme.primary
            }
        }
    }

    var showTargetDialog by remember { mutableStateOf(false) }
    var showCalendarView by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    ConnectionTag(
                        status = connectionStatus,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HeaderBar(job, onSelectJob, onSettings, isSubscribed)
                }
            }

            // Carte d'état de l'import (en cours / succès / erreur)
            if (importStatus != ImportStatus.Idle) {
                item { ImportStatusCard(importStatus, onDismissImport) }
            }

            // Carte contrat — tout en haut, fond sombre
            if (job.endDate != null) {
                item { Appear(0) { ContractCard(job.startDate, job.endDate, contrat, onAddEntry) } }
            }

            // Enregistrement rapide de journée et de pause
            item {
                val hasEntryToday = remember(entries) { entries.any { it.date == LocalDate.now() } }
                Appear(40) {
                    SessionTrackerRow(
                        job = job,
                        activeSessionStartTime = activeSessionStartTime,
                        activeSessionPauseStartTime = activeSessionPauseStartTime,
                        activeSessionTotalPauseMs = activeSessionTotalPauseMs,
                        onStartWorkday = onStartWorkday,
                        onStartPause = onStartPause,
                        onEndPause = onEndPause,
                        onEndWorkday = onEndWorkday,
                        onCancelWorkday = onCancelWorkday,
                        hasEntryToday = hasEntryToday
                    )
                }
            }

            // Aperçu objectif — carte blanche avec jauge en arc
            item {
                Appear(80) {
                    val progress = (stats.salaireNetEstime / job.targetMonthlySalary.coerceAtLeast(1.0)).toFloat()
                    ArcGaugeCard(
                        title = stringResource(R.string.dash_goal_overview),
                        leftLabel = stringResource(R.string.dash_earned),
                        leftValue = euro(stats.salaireNetEstime),
                        rightLabel = stringResource(R.string.dash_remaining),
                        rightValue = euro((job.targetMonthlySalary - stats.salaireNetEstime).coerceAtLeast(0.0)),
                        centerLabel = stringResource(R.string.dash_goal),
                        centerValue = fmtMoney(job.targetMonthlySalary),
                        progress = progress,
                        onAction = { showTargetDialog = true },
                        dark = false,
                        modifier = Modifier.fillMaxWidth(),
                        bottomContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = targetDateText,
                                    color = targetColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (targetWarningText != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = targetWarningText!!,
                                        color = if (targetColor == Color(0xFFEF4444)) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                Appear(160) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MiniCard(stringResource(R.string.dash_base_gross), euro(stats.salaireBrutBase), Modifier.weight(1f))
                        MiniCard(stringResource(R.string.dash_hours_bank), fmtHours(stats.soldeLivretTotal), Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dash_history), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showCalendarView = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = stringResource(R.string.dash_list),
                                tint = if (!showCalendarView) MaterialTheme.colorScheme.primary else InkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { showCalendarView = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = stringResource(R.string.dash_calendar),
                                tint = if (showCalendarView) MaterialTheme.colorScheme.primary else InkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (entries.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSeeAll() }.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.dash_see_all), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (showCalendarView) {
                item {
                    CalendarView(
                        entries = entries,
                        onAddEntry = { date ->
                            onEditEntry(DayEntry(
                                id = "",
                                jobId = job.id,
                                date = date,
                                startTime = LocalTime.of(8, 0),
                                endTime = LocalTime.of(17, 0),
                                pauseMinutes = 0
                            ))
                        },
                        onEditEntry = onEditEntry
                    )
                }
            } else {
                if (entries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.dash_no_entries), color = InkMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(entries.sortedByDescending { it.date }.take(7), key = { it.id }) { entry ->
                        EntryRow(entry, onClick = { onEditEntry(entry) }, hourlyRateBrut = job.hourlyRateBrut)
                    }
                }
            }
        }
    }

    if (showTargetDialog) {
        TargetDialog(
            current = job.targetMonthlySalary,
            onConfirm = { onUpdateTarget(it); showTargetDialog = false },
            onDismiss = { showTargetDialog = false }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderBar(job: Job, onSelectJob: () -> Unit, onSettings: () -> Unit, isSubscribed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dash_job), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Text(job.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        ProfileIconButton(isSubscribed = isSubscribed, onClick = onSettings)
        Spacer(Modifier.width(10.dp))
        SquareIconButton(Icons.Default.Work, onClick = onSelectJob, active = true)
    }
}

// ── Grand nombre + mini graphe ───────────────────────────────────────────────

@Composable
private fun BigNumberCard(label: String, value: Double, values: List<Float>) {
    AppCard(padding = 20.dp) {
        Text(label, color = InkMuted, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        AnimatedEuro(
            amount = value,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            color = Ink
        )
        Spacer(Modifier.height(18.dp))
        MiniAreaChart(
            values = values,
            modifier = Modifier.fillMaxWidth().height(90.dp)
        )
    }
}

@Composable
private fun MiniAreaChart(values: List<Float>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.15f
        val stepX = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxV * size.height * 0.9f)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo((values.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Carte contrat (ticks) ─────────────────────────────────────────────────────

@Composable
private fun ContractCard(start: LocalDate?, end: LocalDate, contrat: SalaryCalculator.ContractSummary, onAdd: () -> Unit) {
    val today = LocalDate.now()
    val daysLeft = ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)
    val elapsedDays = if (start != null) ChronoUnit.DAYS.between(start, today).coerceAtLeast(0) else 0L
    val elapsedWeeks = elapsedDays / 7
    val progress = contrat.progressionPct ?: 0f

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WidgetGray)
            .padding(20.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    SectionLabelMini(stringResource(R.string.dash_contract), color = OnWidgetMut)
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()},${((progress * 1000).toInt() % 10)} %", color = OnWidget, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$daysLeft", color = OnWidget, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.dash_days_left), color = OnWidgetMut, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            TickBarDark(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dash_contract_progress),
                color = OnWidgetMut,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendDot(Color.White, "$elapsedDays", stringResource(R.string.dash_days_elapsed))
                LegendDot(MaterialTheme.colorScheme.primary, "$elapsedWeeks", stringResource(R.string.dash_weeks))
                LegendDot(OnWidgetMut, fmtHours(contrat.totalHeuresReelles), stringResource(R.string.dash_hours_label))
            }
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = stringResource(R.string.dash_add_day),
                onClick = onAdd,
                leading = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TickBarDark(progress: Float, modifier: Modifier = Modifier, ticks: Int = 44) {
    val filled = (ticks * progress.coerceIn(0f, 1f)).toInt()
    Row(modifier = modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(ticks) { i ->
            val color = if (i < filled) {
                val fraction = if (filled > 1) i.toFloat() / (filled - 1).toFloat() else 0f
                androidx.compose.ui.graphics.lerp(
                    start = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    stop = MaterialTheme.colorScheme.primary,
                    fraction = fraction
                )
            } else {
                WidgetTrack
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
private fun LegendDot(color: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, size = 9.dp)
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = OnWidget, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(label, color = OnWidgetMut, fontSize = 11.sp)
        }
    }
}

// ── Mini cartes ──────────────────────────────────────────────────────────────

@Composable
private fun MiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, padding = 16.dp) {
        Dot()
        Spacer(Modifier.height(10.dp))
        Text(value, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = InkMuted, fontSize = 12.sp)
    }
}

// ── Ligne historique ─────────────────────────────────────────────────────────



// ── Dialog objectif ──────────────────────────────────────────────────────────

@Composable
private fun TargetDialog(current: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(current.toInt().toString()) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
        ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(28.dp).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
        ) {}) {
            AppCard(padding = 22.dp) {
                Text(stringResource(R.string.dash_monthly_goal), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                AppTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    placeholder = stringResource(R.string.dash_amount),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(stringResource(R.string.common_cancel), onClick = onDismiss, filled = false, modifier = Modifier.weight(1f))
                    AppButton(stringResource(R.string.common_confirm), onClick = { input.toDoubleOrNull()?.let { if (it > 0) onConfirm(it) } }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun euro(v: Double): String = fmtMoney(v)

@Composable
private fun CalendarView(
    entries: List<DayEntry>,
    onAddEntry: (LocalDate) -> Unit,
    onEditEntry: (DayEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value // 1 = Lun, 7 = Dim

    val entriesByDate = remember(entries) {
        entries.associateBy { it.date }
    }

    AppCard(modifier = modifier, padding = 16.dp) {
        Column {
            // Header Month Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Ink)
                }
                Text(
                    text = currentMonth.format(formatter).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - (firstDayOfWeek - 2)
                        
                        if (dayNum in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayNum)
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
                                    .aspectRatio(1f)
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
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNum",
                                        color = cellTextColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (isToday || entry != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (entry != null) {
                                        val hrs = entry.totalHours
                                        val hrsStr = if (hrs == hrs.toLong().toDouble()) "${hrs.toLong()}" else String.format(Locale.getDefault(), "%.1f", hrs)
                                        Text(
                                            text = if (entry.isLeave) stringResource(R.string.common_leave) else "${hrsStr}h",
                                            color = cellTextColor.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ImportStatusCard(
    status: ImportStatus,
    onDismiss: () -> Unit
) {
    // Auto-fermeture après succès / erreur
    androidx.compose.runtime.LaunchedEffect(status) {
        if (status is ImportStatus.Success || status is ImportStatus.Error) {
            kotlinx.coroutines.delay(4000)
            onDismiss()
        }
    }

    val (bg, fg) = when (status) {
        is ImportStatus.Success -> androidx.compose.ui.graphics.Color(0xFFD1FAE5) to androidx.compose.ui.graphics.Color(0xFF047857)
        is ImportStatus.Error -> androidx.compose.ui.graphics.Color(0xFFFEE2E2) to androidx.compose.ui.graphics.Color(0xFFB91C1C)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer to androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    }

    androidx.compose.material3.Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = bg),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                is ImportStatus.InProgress -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.size(20.dp), strokeWidth = 2.dp, color = fg
                )
                is ImportStatus.Success -> androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.CheckCircle, null, tint = fg, modifier = androidx.compose.ui.Modifier.size(22.dp)
                )
                is ImportStatus.Error -> androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.ErrorOutline, null, tint = fg, modifier = androidx.compose.ui.Modifier.size(22.dp)
                )
                else -> {}
            }
            Spacer(androidx.compose.ui.Modifier.width(12.dp))
            androidx.compose.material3.Text(
                text = when (status) {
                    is ImportStatus.InProgress -> status.message
                    is ImportStatus.Success -> stringResource(R.string.dash_imported, status.count)
                    is ImportStatus.Error -> status.message
                    else -> ""
                },
                color = fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = androidx.compose.ui.Modifier.weight(1f)
            )
            if (status is ImportStatus.Success || status is ImportStatus.Error) {
                androidx.compose.material3.IconButton(onClick = onDismiss, modifier = androidx.compose.ui.Modifier.size(24.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, stringResource(R.string.common_close), tint = fg, modifier = androidx.compose.ui.Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionTrackerRow(
    job: Job,
    activeSessionStartTime: Long,
    activeSessionPauseStartTime: Long,
    activeSessionTotalPauseMs: Long,
    onStartWorkday: () -> Unit,
    onStartPause: () -> Unit,
    onEndPause: () -> Unit,
    onEndWorkday: () -> Unit,
    onCancelWorkday: () -> Unit,
    hasEntryToday: Boolean
) {
    val isWorkdayRunning = activeSessionStartTime > 0L
    val isBreakRunning = activeSessionPauseStartTime > 0L

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    LaunchedEffect(isWorkdayRunning) {
        if (!isWorkdayRunning) {
            confirmCancel = false
            confirmEnd = false
        }
    }

    LaunchedEffect(isWorkdayRunning, isBreakRunning) {
        if (isWorkdayRunning || isBreakRunning) {
            while (true) {
                currentTime = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val totalPauseMs = if (isBreakRunning) {
        activeSessionTotalPauseMs + (currentTime - activeSessionPauseStartTime)
    } else {
        activeSessionTotalPauseMs
    }

    val workMs = if (isWorkdayRunning) {
        if (isBreakRunning) {
            activeSessionPauseStartTime - activeSessionStartTime - activeSessionTotalPauseMs
        } else {
            currentTime - activeSessionStartTime - activeSessionTotalPauseMs
        }
    } else {
        0L
    }.coerceAtLeast(0L)

    fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, secs)
    }

    val workText = formatDuration(workMs)
    val earningsBrut = (workMs / 3600000.0) * job.hourlyRateBrut
    val earningsNet = earningsBrut * 0.78

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Workday
        Box(
            modifier = Modifier
                .weight(1f)
                .height(200.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WidgetGray)
                .border(
                    width = 1.dp,
                    color = if (isWorkdayRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dash_my_day),
                        color = OnWidgetMut,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    // Active indicator dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isWorkdayRunning) Color(0xFF4CAF50)
                                else if (hasEntryToday) Color(0xFF4CAF50).copy(alpha = 0.5f)
                                else OnWidgetMut.copy(alpha = 0.4f)
                            )
                    )
                }

                if (!isWorkdayRunning) {
                    if (hasEntryToday) {
                        // Recorded state (unclickable)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.dash_recorded),
                                color = OnWidget,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.common_today),
                                color = OnWidgetMut,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Start Workday Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onStartWorkday() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.dash_start_day),
                                color = OnWidget,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Running state display
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = workText,
                            color = OnWidget,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dash_plus_gross, euro(earningsBrut)),
                            color = OnWidgetMut,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.dash_plus_net, euro(earningsNet)),
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action buttons with double confirmation
                    if (!confirmCancel && !confirmEnd) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cancel button
                            OutlinedButton(
                                onClick = { confirmCancel = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = OnWidgetMut
                                ),
                                border = BorderStroke(1.dp, OnWidgetMut.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(0.35f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }

                            // Terminer button
                            Button(
                                onClick = { confirmEnd = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(0.65f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.dash_finish), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (confirmCancel) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Confirm cancel
                            Button(
                                onClick = {
                                    confirmCancel = false
                                    onCancelWorkday()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.common_sure), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Keep workday
                            Button(
                                onClick = { confirmCancel = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OnWidgetMut.copy(alpha = 0.15f),
                                    contentColor = OnWidget
                                ),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.common_no), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (confirmEnd) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cancel ending / Back
                            Button(
                                onClick = { confirmEnd = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OnWidgetMut.copy(alpha = 0.15f),
                                    contentColor = OnWidget
                                ),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.common_no), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Confirm ending / Save
                            Button(
                                onClick = {
                                    confirmEnd = false
                                    onEndWorkday()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.common_sure), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Break
        val cardAlpha = if (isWorkdayRunning) 1f else if (hasEntryToday) 0.3f else 0.5f
        Box(
            modifier = Modifier
                .weight(1f)
                .height(200.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(WidgetGray)
                .border(
                    width = 1.dp,
                    color = if (isBreakRunning) Color(0xFFFF9800).copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(enabled = isWorkdayRunning) {
                    if (isBreakRunning) onEndPause() else onStartPause()
                }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dash_break),
                        color = OnWidgetMut.copy(alpha = cardAlpha),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isBreakRunning) Color(0xFFFF9800)
                                else OnWidgetMut.copy(alpha = 0.4f * cardAlpha)
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!isWorkdayRunning) {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = null,
                            tint = OnWidgetMut.copy(alpha = if (hasEntryToday) 0.15f else 0.3f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dash_break_label),
                            color = OnWidgetMut.copy(alpha = if (hasEntryToday) 0.25f else 0.5f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isBreakRunning) {
                        val activePauseMs = currentTime - activeSessionPauseStartTime
                        val totalPauseMin = totalPauseMs / 60000

                        Text(
                            text = formatDuration(activePauseMs),
                            color = OnWidget,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dash_break_active),
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.dash_break_cumulated, totalPauseMin),
                            color = OnWidgetMut,
                            fontSize = 11.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dash_start_break),
                            color = OnWidget,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (totalPauseMs > 0) {
                            Text(
                                text = stringResource(R.string.dash_break_taken, totalPauseMs / 60000),
                                color = OnWidgetMut,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (isWorkdayRunning && isBreakRunning) {
                    Button(
                        onClick = onEndPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dash_resume), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isWorkdayRunning) {
                    Button(
                        onClick = onStartPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalCafe, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dash_take_break), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
