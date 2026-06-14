package com.benjamin.salarytracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDayScreen(
    job: Job,
    templates: List<DayTemplate>,
    existingEntry: DayEntry? = null,
    onAddEntry: (DayEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onManageTemplates: () -> Unit,
    onImportFile: (Uri, onSuccess: (Int) -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<DayTemplate?>(null) }
    var selectedDate by remember { mutableStateOf(existingEntry?.date ?: LocalDate.now()) }
    var startTime by remember { mutableStateOf(existingEntry?.startTime ?: LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(existingEntry?.endTime ?: LocalTime.of(17, 0)) }
    val pauseBlocks = remember {
        mutableStateListOf<Long>().apply {
            if (existingEntry != null) add(existingEntry.pauseMinutes)
        }
    }

    val context = LocalContext.current
    var isLeave by remember { mutableStateOf(existingEntry?.isLeave ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (existingEntry != null && existingEntry.id.isNotEmpty()) "MODIFIER" else "ENREGISTRER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = if (existingEntry != null && existingEntry.id.isNotEmpty()) "Journée" else "Nouvelle journée",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (existingEntry == null) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        onImportFile(
                            it,
                            { count ->
                                Toast.makeText(context, "Import réussi : $count journées ajoutées.", Toast.LENGTH_LONG).show()
                            },
                            { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }

                AppButton(
                    text = "Importer l'historique (CSV/Texte/Image)",
                    onClick = { launcher.launch("*/*") },
                    leading = Icons.Default.ArrowUpward,
                    filled = false,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )
            }

            // Date picker section
            SectionLabel("Date de la journée")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Choisir une date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                enabled = false,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Type de journée : travaillée ou congé/absence
            SectionLabel("Type de journée")
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedToggle(
                options = listOf("Travaillée", "Congé / absence"),
                selected = if (isLeave) 1 else 0,
                onSelect = { isLeave = it == 1 },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isLeave) {
                Spacer(modifier = Modifier.height(20.dp))

                // Templates
                SectionLabel("Templates rapides")
                TemplateChips(
                    templates = templates,
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

                // Time inputs
                SectionLabel("Horaires")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimePickerField(
                        label = "Début",
                        time = startTime,
                        onTimeChange = { startTime = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerField(
                        label = "Fin",
                        time = endTime,
                        onTimeChange = { endTime = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel("Pauses")
                Spacer(modifier = Modifier.height(8.dp))
                PauseManager(pauseBlocks)
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Journée non travaillée : elle ne sera pas comptée comme un déficit dans le livret.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (!isLeave && !endTime.isAfter(startTime)) {
                        Toast.makeText(context, "L'heure de fin doit être après le début", Toast.LENGTH_LONG).show()
                    } else {
                        onAddEntry(
                            DayEntry(
                                id = if (existingEntry != null && existingEntry.id.isNotEmpty()) existingEntry.id else java.util.UUID.randomUUID().toString(),
                                jobId = job.id,
                                date = selectedDate,
                                startTime = if (isLeave) java.time.LocalTime.MIDNIGHT else startTime,
                                endTime = if (isLeave) java.time.LocalTime.MIDNIGHT else endTime,
                                pauseMinutes = if (isLeave) 0L else pauseBlocks.sum().toLong(),
                                isLeave = isLeave
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                 Text(
                    if (existingEntry != null && existingEntry.id.isNotEmpty()) "Mettre à jour" else "Valider la journée",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (existingEntry != null && existingEntry.id.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onDeleteEntry(existingEntry.id)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Supprimer cette journée", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = onManageTemplates,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "Gérer mes horaires types",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp
    )
}
