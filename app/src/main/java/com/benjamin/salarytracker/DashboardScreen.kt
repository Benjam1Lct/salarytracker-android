package com.benjamin.salarytracker

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
    onSeeAll: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val stats = SalaryCalculator.calculateMonthStats(job, entries, YearMonth.now())
    val contrat = SalaryCalculator.calculateContractSummary(job, entries)

    var showTargetDialog by remember { mutableStateOf(false) }
    var showCalendarView by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConnectionTag(
                    status = connectionStatus,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item { HeaderBar(job, onSelectJob, onSettings) }

            // Carte contrat — tout en haut, fond sombre
            if (job.endDate != null) {
                item { Appear(0) { ContractCard(job.startDate, job.endDate, contrat, onAddEntry) } }
            }

            // Aperçu objectif — carte blanche avec jauge en arc
            item {
                Appear(80) {
                    val progress = (stats.salaireNetEstime / job.targetMonthlySalary.coerceAtLeast(1.0)).toFloat()
                    ArcGaugeCard(
                        title = "APERÇU OBJECTIF",
                        leftLabel = "Gagné",
                        leftValue = euro(stats.salaireNetEstime),
                        rightLabel = "Restant",
                        rightValue = euro((job.targetMonthlySalary - stats.salaireNetEstime).coerceAtLeast(0.0)),
                        centerLabel = "Objectif",
                        centerValue = fmtMoney(job.targetMonthlySalary),
                        progress = progress,
                        onAction = { showTargetDialog = true },
                        dark = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Appear(160) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MiniCard("Brut base", euro(stats.salaireBrutBase), Modifier.weight(1f))
                        MiniCard("Livret", fmtHours(stats.soldeLivretTotal), Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Historique", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showCalendarView = false }) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "Liste",
                                tint = if (!showCalendarView) MaterialTheme.colorScheme.primary else InkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { showCalendarView = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendrier",
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
                                Text("Voir tout", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                            Text("Aucune journée. Touchez + pour commencer.", color = InkMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(entries.sortedByDescending { it.date }.take(15), key = { it.id }) { entry ->
                        EntryRow(entry, onClick = { onEditEntry(entry) })
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
private fun HeaderBar(job: Job, onSelectJob: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("EMPLOI", color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Text(job.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        SquareIconButton(Icons.Default.Settings, onClick = onSettings, active = false)
        Spacer(Modifier.width(10.dp))
        SquareIconButton(Icons.Default.SwapHoriz, onClick = onSelectJob, active = true)
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
                    SectionLabelMini("CONTRAT", color = OnWidgetMut)
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()},${((progress * 1000).toInt() % 10)} %", color = OnWidget, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$daysLeft", color = OnWidget, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("jours restants", color = OnWidgetMut, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            TickBarDark(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendDot(Color.White, "${contrat.joursTravailles}", "Jours")
                LegendDot(MaterialTheme.colorScheme.primary, "${contrat.semainesTravaillees}", "Semaines")
                LegendDot(OnWidgetMut, fmtHours(contrat.totalHeuresReelles), "Heures")
            }
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = "AJOUTER UNE JOURNÉE",
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
            val color = when {
                i < filled - 4 -> Color.White
                i < filled -> MaterialTheme.colorScheme.primary
                else -> WidgetTrack
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
                Text("Objectif mensuel", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                AppTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    placeholder = "Montant (€)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton("Annuler", onClick = onDismiss, filled = false, modifier = Modifier.weight(1f))
                    AppButton("Confirmer", onClick = { input.toDoubleOrNull()?.let { if (it > 0) onConfirm(it) } }, modifier = Modifier.weight(1f))
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
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE) }

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
                val headers = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")
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
                                        val hrsStr = if (hrs == hrs.toLong().toDouble()) "${hrs.toLong()}" else String.format(Locale.FRANCE, "%.1f", hrs)
                                        Text(
                                            text = if (entry.isLeave) "Congé" else "${hrsStr}h",
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
