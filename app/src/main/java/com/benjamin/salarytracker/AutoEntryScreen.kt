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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
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
    isSubscribed: Boolean = false,
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
                        Text(stringResource(R.string.ae_header), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        Text(stringResource(R.string.ae_title), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    ProfileIconButton(isSubscribed = isSubscribed, onClick = onSettings)
                    Spacer(Modifier.width(10.dp))
                    SquareIconButton(Icons.Default.Work, onClick = onSelectJob, active = true)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Règles actives (TOUT EN HAUT) ──
            Appear(80) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ae_active_rules), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.ae_no_active), color = InkMuted, fontSize = 14.sp)
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
                    AppButton(stringResource(R.string.ae_add_rule), onClick = { expanded = true }, leading = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
                } else {
                    Column {
                        Text(stringResource(R.string.ae_new_rule), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                val wl = stringArrayResource(R.array.weekday_letters)
                Text(rule.templateName.ifBlank { stringResource(R.string.ae_rule_fallback) }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val days = rule.weekdays.sorted().joinToString(" ") { wl[it - 1] }
                val periodTxt = when (rule.mode) {
                    AutoEntryMode.UNTIL_DISABLED -> stringResource(R.string.ae_since, rule.startDate.format(dateFmt))
                    AutoEntryMode.PERIOD -> stringResource(R.string.ae_period, rule.startDate.format(dateFmt), rule.endDate?.format(dateFmt) ?: "?")
                }
                Text(periodTxt, color = InkMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(days, color = PurpleDeep, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(NegRed.copy(alpha = 0.10f)).clickable { onDeleteRule(rule.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.set_delete), tint = NegRed, modifier = Modifier.size(18.dp))
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
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_cancel)) } }
        ) { DatePicker(state = state) }
    }

    Column {
        // Source
        FormLabel(stringResource(R.string.ae_source))
        if (templates.isNotEmpty()) {
            SegmentedToggle(
                options = listOf(stringResource(R.string.ae_template), stringResource(R.string.ae_custom_day)),
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
                TimePickerField(stringResource(R.string.add_start), customStart, { customStart = it }, Modifier.weight(1f))
                TimePickerField(stringResource(R.string.add_end), customEnd, { customEnd = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = customPause,
                onValueChange = { customPause = it.filter { c -> c.isDigit() } },
                placeholder = stringResource(R.string.ae_pause_min),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
        FormLabel(stringResource(R.string.ae_recurrence))
        SegmentedToggle(
            options = listOf(stringResource(R.string.ae_endless), stringResource(R.string.ae_period_opt)),
            selected = if (mode == AutoEntryMode.PERIOD) 1 else 0,
            onSelect = { mode = if (it == 1) AutoEntryMode.PERIOD else AutoEntryMode.UNTIL_DISABLED },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickerField(stringResource(R.string.add_start), startDate.format(dateFmt), Icons.Default.CalendarMonth, Modifier.weight(1f)) { picking = "start" }
            if (mode == AutoEntryMode.PERIOD) {
                PickerField(stringResource(R.string.add_end), endDate.format(dateFmt), Icons.Default.EventBusy, Modifier.weight(1f)) { picking = "end" }
            }
        }

        Spacer(Modifier.height(18.dp))
        FormLabel(stringResource(R.string.ae_workdays))
        val weekdayLetters = stringArrayResource(R.array.weekday_letters)
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
                    Text(weekdayLetters[day - 1], color = if (selected) CardWhite else InkMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(stringResource(R.string.common_cancel), onClick = onCancel, filled = false, modifier = Modifier.weight(1f))
            val customNameTpl = stringResource(R.string.ae_custom_name)
            AppButton(stringResource(R.string.ae_activate), onClick = {
                val tmpl = selectedTemplate
                when {
                    !useCustom && tmpl == null -> Toast.makeText(context, context.getString(R.string.ae_pick_template), Toast.LENGTH_SHORT).show()
                    useCustom && !customEnd.isAfter(customStart) -> Toast.makeText(context, context.getString(R.string.ae_end_after_start), Toast.LENGTH_SHORT).show()
                    weekdays.isEmpty() -> Toast.makeText(context, context.getString(R.string.ae_pick_day), Toast.LENGTH_SHORT).show()
                    mode == AutoEntryMode.PERIOD && !endDate.isAfter(startDate) -> Toast.makeText(context, context.getString(R.string.ae_end_after_start), Toast.LENGTH_SHORT).show()
                    else -> {
                        val rule = if (useCustom) AutoEntryRule(
                            templateId = null,
                            templateName = String.format(customNameTpl, customStart.toString(), customEnd.toString()),
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
                        Toast.makeText(context, context.getString(R.string.ae_activated), Toast.LENGTH_SHORT).show()
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
