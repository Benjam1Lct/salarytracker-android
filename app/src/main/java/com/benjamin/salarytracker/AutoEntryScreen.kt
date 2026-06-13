package com.benjamin.salarytracker

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEntryScreen(
    job: Job,
    templates: List<DayTemplate>,
    autoRules: List<AutoEntryRule>,
    connectionStatus: ConnectionStatus,
    onAddRule: (AutoEntryRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onSettings: () -> Unit,
    onSelectJob: () -> Unit,
    onBack: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeRules = autoRules.filter { it.active }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 20.dp, bottom = 120.dp)
        ) {
            ConnectionTag(status = connectionStatus, modifier = Modifier.padding(bottom = 12.dp))

            Appear(0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AUTOMATISATION", color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        Text("Saisie automatique", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    SquareIconButton(Icons.Default.Settings, onClick = onSettings, active = false)
                    Spacer(Modifier.width(10.dp))
                    SquareIconButton(Icons.Default.SwapHoriz, onClick = onSelectJob, active = true)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Règles actives (TOUT EN HAUT) ──
            Appear(80) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Règles en cours", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(PurpleTint).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("${activeRules.size}", color = PurpleDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (activeRules.isEmpty()) {
                Appear(140) {
                    AppCard(padding = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Autorenew, null, tint = InkMuted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Aucune règle active pour le moment.", color = InkMuted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                activeRules.forEachIndexed { index, rule ->
                    Appear(delayMillis = 140 + 50 * index) {
                        ActiveRuleCard(rule, onDeleteRule)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Ajouter une règle ──
            Appear(220) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically { it / 3 } togetherWith
                            fadeOut(tween(200)) + slideOutVertically { it / 3 }
                },
                label = "FormTransition"
            ) { isExpanded ->
                if (!isExpanded) {
                    AppButton("Ajouter une règle", onClick = { expanded = true }, leading = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
                } else {
                    Column {
                        Text("Nouvelle règle", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        AppCard(padding = 18.dp) {
                            AutoEntryForm(
                                templates = templates,
                                onAddRule = { onAddRule(it); expanded = false },
                                onCancel = { expanded = false }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

// ── Carte d'une règle active (Notivo) ────────────────────────────────────────

@Composable
private fun ActiveRuleCard(rule: AutoEntryRule, onDeleteRule: (String) -> Unit) {
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yy")
    AppCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(PurpleTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Autorenew, null, tint = PurpleDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.templateName.ifBlank { "Règle" }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val days = rule.weekdays.sorted().joinToString(" ") { WEEKDAY_LABELS[it - 1] }
                val periodTxt = when (rule.mode) {
                    AutoEntryMode.UNTIL_DISABLED -> "Depuis ${rule.startDate.format(dateFmt)}"
                    AutoEntryMode.PERIOD -> "${rule.startDate.format(dateFmt)} → ${rule.endDate?.format(dateFmt) ?: "?"}"
                }
                Text("$periodTxt", color = InkMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(days, color = PurpleDeep, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(NegRed.copy(alpha = 0.10f)).clickable { onDeleteRule(rule.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Supprimer", tint = NegRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Formulaire (Notivo) ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoEntryForm(
    templates: List<DayTemplate>,
    onAddRule: (AutoEntryRule) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yy")

    var useCustom by remember { mutableStateOf(templates.isEmpty()) }
    var selectedTemplate by remember { mutableStateOf<DayTemplate?>(templates.firstOrNull()) }
    var customStart by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var customEnd by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var customPause by remember { mutableStateOf("60") }
    var mode by remember { mutableStateOf(AutoEntryMode.UNTIL_DISABLED) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var picking by remember { mutableStateOf<String?>(null) }

    if (picking != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (picking == "start") startDate else endDate)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (picking == "start") startDate = d else endDate = d
                    }
                    picking = null
                }) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }

    Column {
        // Source
        FormLabel("Source")
        if (templates.isNotEmpty()) {
            SegmentedToggle(
                options = listOf("Template", "Journée custom"),
                selected = if (useCustom) 1 else 0,
                onSelect = { useCustom = it == 1 },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))

        if (!useCustom && templates.isNotEmpty()) {
            TemplateChips(templates = templates, selectedTemplate = selectedTemplate, onTemplateSelect = { selectedTemplate = it })
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimePickerField("Début", customStart, { customStart = it }, Modifier.weight(1f))
                TimePickerField("Fin", customEnd, { customEnd = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = customPause,
                onValueChange = { customPause = it.filter { c -> c.isDigit() } },
                placeholder = "Pause (min)",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
        FormLabel("Récurrence")
        SegmentedToggle(
            options = listOf("Sans fin", "Période"),
            selected = if (mode == AutoEntryMode.PERIOD) 1 else 0,
            onSelect = { mode = if (it == 1) AutoEntryMode.PERIOD else AutoEntryMode.UNTIL_DISABLED },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickerField("Début", startDate.format(dateFmt), Icons.Default.CalendarMonth, Modifier.weight(1f)) { picking = "start" }
            if (mode == AutoEntryMode.PERIOD) {
                PickerField("Fin", endDate.format(dateFmt), Icons.Default.EventBusy, Modifier.weight(1f)) { picking = "end" }
            }
        }

        Spacer(Modifier.height(18.dp))
        FormLabel("Jours de travail")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { day ->
                val selected = day in weekdays
                Box(
                    modifier = Modifier
                        .weight(1f).height(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (selected) Purple else LavenderAlt)
                        .clickable { weekdays = if (selected) weekdays - day else weekdays + day },
                    contentAlignment = Alignment.Center
                ) {
                    Text(WEEKDAY_LABELS[day - 1], color = if (selected) CardWhite else InkMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Annuler", onClick = onCancel, filled = false, modifier = Modifier.weight(1f))
            AppButton("Activer", onClick = {
                val tmpl = selectedTemplate
                when {
                    !useCustom && tmpl == null -> Toast.makeText(context, "Choisis un template", Toast.LENGTH_SHORT).show()
                    useCustom && !customEnd.isAfter(customStart) -> Toast.makeText(context, "La fin doit être après le début", Toast.LENGTH_SHORT).show()
                    weekdays.isEmpty() -> Toast.makeText(context, "Sélectionne au moins un jour", Toast.LENGTH_SHORT).show()
                    mode == AutoEntryMode.PERIOD && !endDate.isAfter(startDate) -> Toast.makeText(context, "La fin doit être après le début", Toast.LENGTH_SHORT).show()
                    else -> {
                        val rule = if (useCustom) AutoEntryRule(
                            templateId = null,
                            templateName = "Journée perso ($customStart–$customEnd)",
                            customStartTime = customStart, customEndTime = customEnd,
                            customPauseMinutes = customPause.toLongOrNull() ?: 0L,
                            active = true, mode = mode, startDate = startDate,
                            endDate = if (mode == AutoEntryMode.PERIOD) endDate else null, weekdays = weekdays
                        ) else AutoEntryRule(
                            templateId = tmpl!!.id, templateName = tmpl.name,
                            active = true, mode = mode, startDate = startDate,
                            endDate = if (mode == AutoEntryMode.PERIOD) endDate else null, weekdays = weekdays
                        )
                        onAddRule(rule)
                        Toast.makeText(context, "Saisie automatique activée ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }, leading = Icons.Default.Bolt, modifier = Modifier.weight(1.6f))
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text, color = InkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun PickerField(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LavenderAlt)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PurpleDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = InkMuted, fontSize = 11.sp)
            Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private val WEEKDAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")
