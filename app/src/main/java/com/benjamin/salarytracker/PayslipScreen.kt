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
import androidx.compose.ui.res.stringResource
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
    useLocalAi: Boolean = false,
    isSubscribed: Boolean = false,
    onNeedGeminiKey: () -> Unit = {},
    onAddPayslip: (Payslip) -> Unit,
    onDeletePayslip: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocr = remember(geminiApiKey, isSubscribed) { OcrService(context, geminiApiKey, useBackend = isSubscribed) }
    val localOcr = remember { LocalOcrService(context) }
    var analyzing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // Aucun mode IA choisi → on ouvre la modal au lieu d'analyser (sauf abonnés).
            if (!isSubscribed && !useLocalAi && geminiApiKey.isBlank()) {
                onNeedGeminiKey()
                return@rememberLauncherForActivityResult
            }
            val analyzeLocally = !isSubscribed && (useLocalAi || geminiApiKey.isBlank())
            analyzing = true
            scope.launch {
                val res = if (analyzeLocally) localOcr.extractPayslipData(uris) { status = it }
                          else ocr.extractPayslipData(uris) { status = it }
                when (res) {
                    is PayslipAnalysis.Success -> {
                        onAddPayslip(res.payslip)
                        Toast.makeText(context, context.getString(R.string.ps_added), Toast.LENGTH_SHORT).show()
                    }
                    is PayslipAnalysis.Failure ->
                        Toast.makeText(context, context.getString(R.string.ps_failed, res.reason), Toast.LENGTH_LONG).show()
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
                    Text(stringResource(R.string.ps_header), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(stringResource(R.string.ps_subtitle), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                        Text(status.ifBlank { stringResource(R.string.ps_analyzing) }, color = Ink, fontSize = 14.sp)
                    }
                }
            } else {
                AppButton(
                    text = stringResource(R.string.ps_import),
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
                    Text(stringResource(R.string.ps_none), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ps_none_hint), color = InkMuted, fontSize = 14.sp)
                }
            } else {
                Text(stringResource(R.string.ps_count, payslips.size), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    val monthName = Month.of(p.month).getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }

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
                Text(stringResource(R.string.ps_real_net, fmtMoney(p.net)), color = InkMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(NegRed.copy(alpha = 0.10f)).clickable { onDelete(p.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.set_delete), tint = NegRed, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        AppDivider()
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniCol(stringResource(R.string.ps_estimated_app), fmtMoney(estNet), InkMuted)
            MiniCol(stringResource(R.string.ps_real_slip), fmtMoney(p.net), Ink)
            MiniCol(
                if (ecart >= 0) stringResource(R.string.ps_gap_plus) else stringResource(R.string.ps_gap),
                "${if (ecart >= 0) "+" else ""}${fmtMoney(ecart)} (${String.format(Locale.getDefault(), "%+.0f", pct)}%)",
                ecartColor
            )
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            AppDivider()
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.ps_detailed), color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonRow(stringResource(R.string.ps_hours_worked), fmtHours(estHours), fmtHours(p.heuresPayees), p.heuresPayees - estHours, isHours = true)
                ComparisonRow(stringResource(R.string.ps_gross), fmtMoney(estBrut), fmtMoney(p.brut), p.brut - estBrut)
                ComparisonRow(stringResource(R.string.ps_net), fmtMoney(estNet), fmtMoney(p.net), p.net - estNet)
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
                            text = stringResource(R.string.ps_underpay, fmtMoney(potentialLoss)),
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
                            text = stringResource(R.string.ps_compliant),
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
                Text(stringResource(R.string.ps_tap_details), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
        else -> stringResource(R.string.ps_compliant_short)
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
            Text(stringResource(R.string.ps_app_val, estim), color = InkMuted, fontSize = 11.sp)
            Text(stringResource(R.string.ps_real_val, reel), color = InkMuted, fontSize = 11.sp)
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
