package com.benjamin.salarytracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.benjamin.salarytracker.ui.theme.CardWhite
import com.benjamin.salarytracker.ui.theme.Ink
import com.benjamin.salarytracker.ui.theme.InkMuted
import com.benjamin.salarytracker.ui.theme.LavenderAlt
import com.benjamin.salarytracker.ui.theme.OutlineLt
import com.benjamin.salarytracker.ui.theme.Purple
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Animations réutilisables
// ─────────────────────────────────────────────────────────────────────────────

/** Apparition en fondu + glissement vers le haut (avec délai pour effet cascade). */
@Composable
fun Appear(delayMillis: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "AppearAlpha"
    )
    val ty by animateFloatAsState(
        targetValue = if (shown) 0f else 36f,
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "AppearY"
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = ty }) { content() }
}

/** Montant € qui s'incrémente (count-up) jusqu'à sa valeur. */
@Composable
fun AnimatedEuro(
    amount: Double,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    val anim by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "EuroCount"
    )
    Text(
        text = fmtMoney(anim.toDouble()),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Champ de sélection d'heure (ouvre le TimePicker Android)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    OutlinedTextField(
        value = time.format(formatter),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = false,
        trailingIcon = {
            Icon(Icons.Default.Schedule, contentDescription = "Choisir l'heure", tint = MaterialTheme.colorScheme.primary)
        },
        modifier = modifier.clickable { showDialog = true },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface
        )
    )

    if (showDialog) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Annuler") }
            },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Bottom Navigation Bar (pill sombre, style Notivo)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onAuto: () -> Unit,
    onStatsHours: () -> Unit,
    onStatsSalary: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Barre blanche, item actif = pilule (icône + texte + fond lavande)
    Column(modifier = modifier.fillMaxWidth().background(CardWhite)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineLt))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Outlined.GridView, "Accueil", currentRoute == "dashboard", onHome)
            NavItem(Icons.Outlined.History, "Historique", currentRoute == "history", onHistory)
            NavItem(Icons.Outlined.Sync, "Auto", currentRoute == "auto_entry", onAuto)
            NavItem(Icons.Outlined.Schedule, "Heures", currentRoute == "stats_hours", onStatsHours)
            NavItem(Icons.Outlined.AccountBalanceWallet, "Salaire", currentRoute == "stats_salary", onStatsSalary)
        }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    // Animations : fond + teinte de l'icône
    val bg by animateColorAsState(
        targetValue = if (active) primary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(300),
        label = "navBg"
    )
    val tint by animateColorAsState(
        targetValue = if (active) primary else InkMuted,
        animationSpec = tween(300),
        label = "navTint"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(23.dp))
        // Le label apparaît/disparaît en glissant horizontalement
        androidx.compose.animation.AnimatedVisibility(
            visible = active,
            enter = androidx.compose.animation.expandHorizontally(tween(280)) + androidx.compose.animation.fadeIn(tween(280)),
            exit = androidx.compose.animation.shrinkHorizontally(tween(220)) + androidx.compose.animation.fadeOut(tween(160))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connection status tag
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectionTag(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (label, dotColor, bgColor, txtColor) = when (status) {
        ConnectionStatus.CONNECTED -> Quad(
            "En ligne",
            Color(0xFF10B981),
            Color(0xFF10B981).copy(alpha = 0.12f),
            Color(0xFF0F9D6E)
        )
        ConnectionStatus.CONNECTING -> Quad(
            "Connexion en cours…",
            Color(0xFFF59E0B),
            Color(0xFFF59E0B).copy(alpha = 0.14f),
            Color(0xFFB46A00)
        )
        ConnectionStatus.SLOW -> Quad(
            "Connexion instable…",
            Color(0xFFEA580C),
            Color(0xFFEA580C).copy(alpha = 0.14f),
            Color(0xFFC2410C)
        )
        ConnectionStatus.OFFLINE -> Quad(
            "Hors connexion",
            Color(0xFF9CA3AF),
            Color(0xFF9CA3AF).copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Petit pulse sur le point quand on cherche à se connecter / connexion lente
    val alpha = if (status == ConnectionStatus.CONNECTING || status == ConnectionStatus.SLOW) {
        val transition = rememberInfiniteTransition(label = "ConnPulse")
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "ConnPulseAlpha"
        ).value
    } else 1f

    // Centré horizontalement
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alpha))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = txtColor
            )
        }
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: Color)

// ─────────────────────────────────────────────────────────────────────────────
// Salary Gauge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SalaryGauge(
    targetAmount: Double,
    currentAmount: Double,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (currentAmount / targetAmount.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "GaugeProgress"
    )
    val remaining = (targetAmount - currentAmount).coerceAtLeast(0.0)
    // Couleurs sombres : la jauge est posée sur la carte jaune (accent)
    val primaryColor = MaterialTheme.colorScheme.onPrimaryContainer
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                val strokeWidth = 14.dp.toPx()
                val inset = strokeWidth / 2f + 4.dp.toPx()
                val gaugeSize = minOf(size.width, size.height * 1.3f) - inset * 2
                val left = (size.width - gaugeSize) / 2f
                val top = inset
                val startAngle = 150f
                val sweepAngle = 240f

                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(left, top),
                    size = Size(gaugeSize, gaugeSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (animatedProgress > 0.01f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(left, top),
                        size = Size(gaugeSize, gaugeSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    val cx = left + gaugeSize / 2f
                    val cy = top + gaugeSize / 2f
                    val r = gaugeSize / 2f
                    val angleRad = Math.toRadians((startAngle + sweepAngle * animatedProgress).toDouble())
                    val dotX = cx + r * cos(angleRad).toFloat()
                    val dotY = cy + r * sin(angleRad).toFloat()
                    drawCircle(color = primaryColor, radius = strokeWidth / 2f + 4.dp.toPx(), center = Offset(dotX, dotY))
                    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 4.dp.toPx(), center = Offset(dotX, dotY))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Objectif",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onEditTarget() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifier l'objectif",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = fmtMoney(currentAmount),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "/ ${targetAmount.toInt()}€",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = fmtMoney(currentAmount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("Gagné", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = fmtMoney(remaining),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Restant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Job Card (with edit button)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun JobCard(
    job: Job,
    isSelected: Boolean,
    onToggleMainJob: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val starColor by animateColorAsState(
        if (job.isMainJob) Color(0xFFFFD700)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        label = "StarColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 3.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                               else Color.Black.copy(alpha = 0.08f)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Work,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${fmtMoneyNum(job.hourlyRateBrut)} €/h · ${fmtMoneyNum(job.weeklyContractHours)} h/sem",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pencil edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Modifier",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Star main job
            IconButton(
                onClick = onToggleMainJob,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (job.isMainJob) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Emploi principal",
                    tint = starColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Template Chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemplateChips(
    templates: List<DayTemplate>,
    selectedTemplate: DayTemplate?,
    onTemplateSelect: (DayTemplate) -> Unit
) {
    if (templates.isEmpty()) {
        Text(
            "Aucun template disponible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(templates) { template ->
            val isSelected = template == selectedTemplate
            val bgColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "ChipBg"
            )
            val txtColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTemplateSelect(template) },
                color = bgColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = template.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = txtColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SpeedDial FAB (kept for back-compat)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeedDialFab(
    onRegisterDay: () -> Unit,
    onCreateTemplate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val transition = updateTransition(expanded, label = "SpeedDial")
    val rotation by transition.animateFloat(label = "Rotation") { if (it) 45f else 0f }
    val scale by transition.animateFloat(label = "Scale") { if (it) 1f else 0f }
    val alpha by transition.animateFloat(label = "Alpha") { if (it) 1f else 0f }

    Column(horizontalAlignment = Alignment.End) {
        if (expanded) {
            SpeedDialItem("Créer un template", Icons.Default.Settings, scale, alpha) {
                expanded = false; onCreateTemplate()
            }
            Spacer(Modifier.height(12.dp))
            SpeedDialItem("Enregistrer une journée", Icons.Default.History, scale, alpha) {
                expanded = false; onRegisterDay()
            }
            Spacer(Modifier.height(12.dp))
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun SpeedDialItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    scale: Float,
    alpha: Float,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(end = 10.dp)) {
            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) { Icon(icon, null) }
    }
}

@Composable
fun EntryRow(entry: DayEntry, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
    AppCard(padding = 0.dp, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (entry.isLeave) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.isLeave) Icons.Default.BeachAccess else Icons.Default.CalendarMonth,
                    null,
                    tint = if (entry.isLeave) InkMuted else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.date.format(fmt).replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (entry.isLeave) "Congé / absence" else "${entry.startTime} – ${entry.endTime}", color = InkMuted, fontSize = 12.sp)
            }
            if (entry.isLeave) {
                Text("Congé", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(fmtHours(entry.totalHours), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
