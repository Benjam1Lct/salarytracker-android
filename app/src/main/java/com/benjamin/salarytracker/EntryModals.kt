package com.benjamin.salarytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterDaySheet(
    job: Job,
    onAddEntry: (DayEntry) -> Unit,
    onManageTemplates: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<DayTemplate?>(null) }
    var startTime by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(17, 0)) }
    val pauseBlocks = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.modal_register_day),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = job.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            SheetSectionLabel(stringResource(R.string.add_quick_templates))
            TemplateChips(
                templates = job.dayTemplates,
                selectedTemplate = selectedTemplate,
                onTemplateSelect = {
                    selectedTemplate = it
                    startTime = it.startTime
                    endTime = it.endTime
                    pauseBlocks.clear()
                    it.pauseBlocks.forEach { p -> pauseBlocks.add(p.durationMinutes) }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SheetSectionLabel(stringResource(R.string.add_schedule))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimePickerField(label = stringResource(R.string.add_start), time = startTime, onTimeChange = { startTime = it }, modifier = Modifier.weight(1f))
                TimePickerField(label = stringResource(R.string.add_end), time = endTime, onTimeChange = { endTime = it }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))
            SheetSectionLabel(stringResource(R.string.add_breaks))
            Spacer(modifier = Modifier.height(8.dp))
            PauseManager(pauseBlocks)

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = {
                    val entry = DayEntry(
                        jobId = job.id,
                        date = LocalDate.now(),
                        startTime = startTime,
                        endTime = endTime,
                        pauseMinutes = pauseBlocks.sum()
                    )
                    onAddEntry(entry)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.modal_save_day), style = MaterialTheme.typography.labelLarge)
            }

            TextButton(
                onClick = onManageTemplates,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    stringResource(R.string.modal_manage_templates),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTemplateSheet(
    onAddTemplate: (DayTemplate) -> Unit,
    onBackToRegister: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(17, 0)) }
    val pauseBlocks = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToRegister) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    stringResource(R.string.modal_new_template),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.modal_template_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            SheetSectionLabel(stringResource(R.string.add_schedule))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimePickerField(label = stringResource(R.string.add_start), time = startTime, onTimeChange = { startTime = it }, modifier = Modifier.weight(1f))
                TimePickerField(label = stringResource(R.string.add_end), time = endTime, onTimeChange = { endTime = it }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            SheetSectionLabel(stringResource(R.string.add_breaks))
            Spacer(modifier = Modifier.height(8.dp))
            PauseManager(pauseBlocks)

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAddTemplate(
                            DayTemplate(
                                name = name,
                                startTime = startTime,
                                endTime = endTime,
                                pauseBlocks = pauseBlocks.map { PauseBlock(it) }
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.modal_save_template), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.4.sp
    )
}

@Composable
fun PauseManager(pauseBlocks: MutableList<Long>) {
    var newPauseStr by remember { mutableStateOf("") }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = newPauseStr,
                onValueChange = { if (it.length <= 3) newPauseStr = it },
                label = { Text(stringResource(R.string.modal_min)) },
                modifier = Modifier.width(110.dp),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = {
                    val p = newPauseStr.toLongOrNull()
                    if (p != null && p > 0) {
                        pauseBlocks.add(p)
                        newPauseStr = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.common_add),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (pauseBlocks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pauseBlocks.forEachIndexed { index, minutes ->
                    AssistChip(
                        onClick = { pauseBlocks.removeAt(index) },
                        label = { Text(stringResource(R.string.modal_pause_min, minutes)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }
}
