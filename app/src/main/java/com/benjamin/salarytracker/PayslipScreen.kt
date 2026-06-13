package com.benjamin.salarytracker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PayslipScreen(
    job: Job,
    entries: List<DayEntry>,
    payslips: List<Payslip>,
    connectionStatus: ConnectionStatus,
    geminiApiKey: String,
    onAddPayslip: (Payslip) -> Unit,
    onDeletePayslip: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocr = remember(geminiApiKey) { OcrService(context, geminiApiKey) }
    var analyzing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            analyzing = true
            scope.launch {
                when (val res = ocr.extractPayslipData(uris) { status = it }) {
                    is PayslipAnalysis.Success -> {
                        onAddPayslip(res.payslip)
                        Toast.makeText(context, "Bulletin ajouté ✓", Toast.LENGTH_SHORT).show()
                    }
                    is PayslipAnalysis.Failure ->
                        Toast.makeText(context, "Échec : ${res.reason}", Toast.LENGTH_LONG).show()
                }
                analyzing = false; status = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 20.dp, bottom = 110.dp)
        ) {
            ConnectionTag(status = connectionStatus, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("BULLETINS DE PAIE", color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text("Estimé vs Réel", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                SquareIconButton(Icons.Default.Close, onClick = onBack)
            }

            Spacer(Modifier.height(20.dp))

            // Import
            if (analyzing) {
                AppCard(padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(status.ifBlank { "Analyse en cours…" }, color = Ink, fontSize = 14.sp)
                    }
                }
            } else {
                AppButton(
                    text = "Importer un bulletin",
                    onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    leading = Icons.Default.UploadFile,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            if (payslips.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Aucun bulletin", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Importe une photo ou un PDF de ta fiche de paie pour la comparer à l'estimation.", color = InkMuted, fontSize = 14.sp)
                }
            } else {
                Text("${payslips.size} bulletin(s)", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                payslips.forEachIndexed { index, p ->
                    val ym = p.yearMonth
                    val monthEntries = remember(entries, ym) { entries.filter { YearMonth.from(it.date) == ym } }
                    val stats = remember(job, monthEntries, ym) { SalaryCalculator.calculateMonthStats(job, monthEntries, ym) }
                    Appear(delayMillis = 40 * index) {
                        PayslipCard(p, stats, onDeletePayslip)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PayslipCard(
    p: Payslip,
    stats: SalaryCalculator.MonthDashboardStats,
    onDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val estNet = stats.salaireNetEstime
    val estBrut = stats.salaireBrutBase + stats.salaireBrutHeuresSupPayees
    val estHours = stats.totalHeuresReellesMois

    val ecart = p.net - estNet
    val pct = if (estNet > 0) (ecart / estNet * 100) else 0.0
    val ecartColor = if (ecart >= 0) PosGreen else NegRed
    val monthName = Month.of(p.month).getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }

    AppCard(
        padding = 16.dp,
        onClick = { expanded = !expanded }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$monthName ${p.year}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Net réel ${fmtMoney(p.net)}", color = InkMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(NegRed.copy(alpha = 0.10f)).clickable { onDelete(p.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Supprimer", tint = NegRed, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        AppDivider()
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniCol("Estimé (App)", fmtMoney(estNet), InkMuted)
            MiniCol("Réel (Bulletin)", fmtMoney(p.net), Ink)
            MiniCol(
                if (ecart >= 0) "Écart +" else "Écart",
                "${if (ecart >= 0) "+" else ""}${fmtMoney(ecart)} (${String.format(Locale.FRANCE, "%+.0f", pct)}%)",
                ecartColor
            )
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            AppDivider()
            Spacer(Modifier.height(12.dp))

            Text("COMPARAISON DÉTAILLÉE", color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonRow("Heures travaillées", fmtHours(estHours), fmtHours(p.heuresPayees), p.heuresPayees - estHours, isHours = true)
                ComparisonRow("Salaire Brut", fmtMoney(estBrut), fmtMoney(p.brut), p.brut - estBrut)
                ComparisonRow("Salaire Net", fmtMoney(estNet), fmtMoney(p.net), p.net - estNet)
            }

            val potentialLoss = estNet - p.net
            if (potentialLoss > 1.0) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NegRed.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = NegRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Sous-paiement potentiel de ${fmtMoney(potentialLoss)} sur ce bulletin.",
                            color = NegRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (ecart >= -1.0) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PosGreen.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = PosGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Bulletin de paie conforme ou supérieur aux estimations ✓",
                            color = PosGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Toucher pour voir les détails ▾", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    estim: String,
    reel: String,
    diff: Double,
    isHours: Boolean = false
) {
    val diffColor = when {
        diff > 0.01 -> PosGreen
        diff < -0.01 -> NegRed
        else -> InkMuted
    }
    val diffStr = when {
        diff > 0.01 -> if (isHours) "+${fmtHours(diff)}" else "+${fmtMoney(diff)}"
        diff < -0.01 -> if (isHours) fmtHours(diff) else fmtMoney(diff)
        else -> "Conforme"
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(diffStr, color = diffColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("App : $estim", color = InkMuted, fontSize = 11.sp)
            Text("Réel : $reel", color = InkMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MiniCol(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = InkMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
