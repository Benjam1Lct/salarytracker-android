package com.benjamin.salarytracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.net.Uri
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    job: Job,
    entries: List<DayEntry>,
    mode: String = "salary",       // "salary" | "hours"
    connectionStatus: ConnectionStatus,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenPayslips: () -> Unit = {},
    onImportFile: (Uri, onSuccess: (Int) -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    onSettings: () -> Unit,
    onSelectJob: () -> Unit
) {
    val isHours = mode == "hours"
    val stats = SalaryCalculator.calculateMonthStats(job, entries, YearMonth.now())
    val contrat = SalaryCalculator.calculateContractSummary(job, entries)

    val months = (0..5).map { YearMonth.now().minusMonths(it.toLong()) }.reversed()
    val mStats = months.map { m ->
        SalaryCalculator.calculateMonthStats(job, entries.filter { YearMonth.from(it.date) == m }, m)
    }
    val monthValues = mStats.map { if (isHours) it.totalHeuresReellesMois.toFloat() else it.salaireNetEstime.toFloat() }

    // Séries mensuelles par métrique (pour les courbes des cartes)
    val serBrut = mStats.map { (it.salaireBrutBase + it.salaireBrutHeuresSupPayees).toFloat() }
    val serLivretVal = mStats.map { (it.heuresAjouteesAuLivretCeMois * job.hourlyRateBrut * 0.78).toFloat() }
    val serNet = mStats.map { it.salaireNetEstime.toFloat() }
    val serLivretH = mStats.map { it.heuresAjouteesAuLivretCeMois.toFloat() }
    val serCrete = mStats.map { it.creteRealHoursMois.toFloat() }
    val serTotalH = mStats.map { it.totalHeuresReellesMois.toFloat() }
    val serSoldeLivret = months.map { m ->
        val entriesUpToM = entries.filter { !it.date.isAfter(m.atEndOfMonth()) }
        SalaryCalculator.calculateTotalLivretFromEntries(entriesUpToM).toFloat()
    }
    val monthLabels = months.map { it.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE).replaceFirstChar { c -> c.uppercase() } }

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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ANALYSES - ${job.name.uppercase()}", color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        Text(if (isHours) "Stats horaires" else "Stats salaire", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    SquareIconButton(Icons.Default.Settings, onClick = onSettings, active = false)
                    Spacer(Modifier.width(10.dp))
                    SquareIconButton(Icons.Default.SwapHoriz, onClick = onSelectJob, active = true)
                }
            }

            // ── Carte sombre fun : contrat / livret ──
            item {
                Appear(0) {
                    if (isHours) {
                        FunStatCard(
                            title = "LIVRET & HEURES",
                            bigValue = fmtHours(contrat.totalHeuresReelles),
                            bigLabel = "Total des heures réelles",
                            metrics = listOf(
                                StatMetric(MaterialTheme.colorScheme.primary, "Crête", fmtHours(stats.creteRealHoursMois), serCrete),
                                StatMetric(PosGreen, "Solde livret", fmtHours(stats.soldeLivretTotal), serSoldeLivret)
                            )
                        )
                    } else {
                        FunStatCard(
                            title = "REVENUS CONTRAT",
                            bigValue = euroS(contrat.netTotal),
                            bigLabel = "Net total estimé",
                            metrics = listOf(
                                StatMetric(MaterialTheme.colorScheme.primary, "Brut /mois", euroS(stats.salaireBrutBase + stats.salaireBrutHeuresSupPayees), serBrut),
                                StatMetric(PosGreen, "Livret fin", euroS(contrat.livretValeurNet), serLivretVal)
                            )
                        )
                    }
                }
            }

            // ── Graphe d'évolution ──
            item {
                Appear(100) {
                    AppCard {
                        Text(if (isHours) "Heures par mois" else "Net par mois", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))
                        if (monthValues.count { it > 0 } >= 2) {
                            AreaChart(monthValues, modifier = Modifier.fillMaxWidth().height(150.dp))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                monthLabels.forEach { Text(it, color = InkMuted, fontSize = 11.sp) }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Pas encore assez de données", color = InkMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ── Détail par semaine (mode heures) ──
            if (isHours && stats.weeklyDetails.isNotEmpty()) {
                item { Text("Détail par semaine", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                items(stats.weeklyDetails) { w -> Appear(0) { WeekCard(w) } }
            }

            // ── Indicateurs (mode salaire) ──
            if (!isHours) {
                item {
                    Appear(200) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MiniStat("Jours", "${contrat.joursTravailles}", Modifier.weight(1f))
                            MiniStat("Semaines", "${contrat.semainesTravaillees}", Modifier.weight(1f))
                            MiniStat("Moy./sem", fmtHours(contrat.moyenneHeuresParSemaine), Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Bulletins + Export ──
            if (!isHours) {
                item {
                    Appear(250) {
                        AppButton(
                            text = "Bulletins de salaire",
                            onClick = onOpenPayslips,
                            leading = Icons.Default.Description,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Appear(280) {
                    val ctx = LocalContext.current
                    var showImportSuccessToast by remember { mutableStateOf<Int?>(null) }
                    var showImportErrorToast by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(showImportSuccessToast) {
                        showImportSuccessToast?.let { count ->
                            Toast.makeText(ctx, "Import réussi : $count journées ajoutées/mises à jour.", Toast.LENGTH_LONG).show()
                            showImportSuccessToast = null
                        }
                    }

                    LaunchedEffect(showImportErrorToast) {
                        showImportErrorToast?.let { err ->
                            Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                            showImportErrorToast = null
                        }
                    }

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            onImportFile(
                                it,
                                { count -> showImportSuccessToast = count },
                                { err -> showImportErrorToast = err }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton(
                            text = "Exporter en CSV",
                            onClick = { ExportService.exportCsv(ctx, job, entries) },
                            leading = Icons.Default.IosShare,
                            filled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = "Importer depuis Excel/CSV/Texte",
                            onClick = { launcher.launch("*/*") },
                            leading = Icons.Default.ArrowUpward,
                            filled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ── Composants ────────────────────────────────────────────────────────────────

data class StatMetric(val color: Color, val label: String, val value: String, val series: List<Float>)

@Composable
private fun FunStatCard(
    title: String,
    bigValue: String,
    bigLabel: String,
    metrics: List<StatMetric>
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(WidgetGray).padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(accent)) }
                Spacer(Modifier.width(10.dp))
                Text(title, color = OnWidgetMut, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            Text(bigValue, color = OnWidget, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(bigLabel, color = OnWidgetMut, fontSize = 13.sp)

            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { m ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(m.color))
                                Spacer(Modifier.width(7.dp))
                                Text(m.label, color = OnWidgetMut, fontSize = 12.sp)
                            }
                            Text(m.value, color = m.color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        Sparkline(m.series, m.color, modifier = Modifier.fillMaxWidth().height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pts = values.filter { it.isFinite() }
        if (pts.size < 2) {
            // ligne plate si pas assez de données
            drawLine(color.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            return@Canvas
        }
        val maxV = (pts.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val minV = pts.minOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(0.001f)
        val stepX = size.width / (pts.size - 1)
        val line = Path()
        pts.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minV) / range * size.height * 0.85f) - size.height * 0.08f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line); lineTo((pts.size - 1) * stepX, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, padding = 14.dp) {
        Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = InkMuted, fontSize = 11.sp)
    }
}

@Composable
private fun WeekCard(w: SalaryCalculator.WeekStats) {
    AppCard(padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(w.weekKey, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val tag = when {
                    w.isCreteWeek -> "Semaine crête"
                    w.isOvertime -> "Heures sup → livret"
                    w.isUnderWeek -> "Sous 35h → puise"
                    else -> "Standard"
                }
                Text(tag, color = if (w.isUnderWeek) NegRed else InkMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtHours(w.realHours), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (w.livretCreditEquivalent > 0)
                    Text("+${fmtHours(w.livretCreditEquivalent)} livret", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                if (w.creteRealHours > 0)
                    Text("+${fmtHours(w.creteRealHours)} crête", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AreaChart(values: List<Float>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.18f
        val stepX = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxV * size.height * 0.9f)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo((values.size - 1) * stepX, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, Brush.horizontalGradient(listOf(secondaryColor, primaryColor)), style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxV * size.height * 0.9f)
            drawCircle(primaryColor, 4.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 2.dp.toPx(), Offset(x, y))
        }
    }
}

private fun euroS(v: Double): String = fmtMoney(v)
