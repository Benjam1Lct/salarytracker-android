package com.benjamin.salarytracker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Écran de création ET d'édition d'un emploi.
 * - [existingJob] == null → mode création
 * - [existingJob] != null → mode édition (champs pré-remplis, bouton "Enregistrer les modifications")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJobScreen(
    existingJob: Job? = null,
    geminiApiKey: String,
    onJobCreated: (Job) -> Unit,
    onJobUpdated: (Job) -> Unit = {},
    onBack: () -> Unit
) {
    val isEditing = existingJob != null

    var name by remember { mutableStateOf(existingJob?.name ?: "") }
    var rate by remember { mutableStateOf(existingJob?.hourlyRateBrut?.toString() ?: "") }
    var hours by remember { mutableStateOf(existingJob?.weeklyContractHours?.toString() ?: "35") }
    var quota by remember { mutableStateOf(existingJob?.annualOvertimeQuota?.toString() ?: "220") }
    var overtimeMode by remember { mutableStateOf(existingJob?.overtimeMode ?: OvertimeMode.PAYEE) }
    var startDate by remember { mutableStateOf(existingJob?.startDate) }
    var endDate by remember { mutableStateOf(existingJob?.endDate) }
    var isArchived by remember { mutableStateOf(existingJob?.isArchived ?: false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var analysisStatus by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ocrService = remember(geminiApiKey) { OcrService(context, geminiApiKey) }

    // Noms lisibles des fichiers sélectionnés (pour l'affichage)
    val selectedNames = remember(selectedUris) {
        selectedUris.map { ocrService.resolveFileName(it) }
    }

    fun runAnalysis() {
        if (selectedUris.isEmpty() || isAnalyzing) return
        isAnalyzing = true
        analysisStatus = "Préparation des documents…"
        scope.launch {
            when (val result = ocrService.extractMultiContractData(selectedUris) { status ->
                analysisStatus = status
            }) {
                is ContractAnalysis.Success -> {
                    val job = result.job
                    name = job.name
                    rate = job.hourlyRateBrut.toString()
                    hours = job.weeklyContractHours.toString()
                    quota = job.annualOvertimeQuota.toString()
                    overtimeMode = job.overtimeMode
                    job.startDate?.let { startDate = it }
                    job.endDate?.let { endDate = it }
                    Toast.makeText(context, "Analyse terminée ✓", Toast.LENGTH_SHORT).show()
                }
                is ContractAnalysis.Failure -> {
                    Toast.makeText(context, "Échec : ${result.reason}", Toast.LENGTH_LONG).show()
                }
            }
            // Reset de la liste une fois l'analyse terminée (succès comme échec)
            selectedUris = emptyList()
            isAnalyzing = false
            analysisStatus = ""
        }
    }

    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    if (pickingStartDate || pickingEndDate) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false; pickingEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (pickingStartDate) startDate = date else endDate = date
                    }
                    pickingStartDate = false; pickingEndDate = false
                }) { Text("Confirmer") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // Sélection multi-format : images ET/OU PDF simultanément
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUris = (selectedUris + uris).distinct()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isEditing) "MODIFICATION" else "NOUVEAU CONTRAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            if (isEditing) existingJob!!.name else "Paramétrage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Fermer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            // OCR scan (uniquement en création)
            if (!isEditing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Analyse automatique (IA)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Importez plusieurs images et/ou PDF de votre contrat. Toutes les pages seront analysées ensemble.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))

                        when {
                            // ── Analyse en cours : barre de progression explicite ──
                            isAnalyzing -> {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    Text(
                                        analysisStatus.ifBlank { "Préparation et analyse de ${selectedUris.size} document(s) en cours…" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // ── Fichiers sélectionnés : liste + bouton d'analyse ──
                            selectedUris.isNotEmpty() -> {
                                Text(
                                    "${selectedUris.size} fichier(s) sélectionné(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(6.dp))
                                selectedNames.forEachIndexed { index, fileName ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (fileName.endsWith(".pdf", true)) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { selectedUris = selectedUris.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Retirer", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { pickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ajouter", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { runAnalysis() },
                                        modifier = Modifier.weight(1.4f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Analyser", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }

                            // ── État initial : bouton d'import ──
                            else -> {
                                Button(
                                    onClick = { pickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.DocumentScanner, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Importer images / PDF", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            FormSection("Informations générales")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Entreprise / Métier") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text("Taux brut (€/h)") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it },
                    label = { Text("H./semaine") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startDate?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "",
                    onValueChange = {},
                    label = { Text("Début contrat") },
                    readOnly = true, enabled = false,
                    modifier = Modifier.weight(1f).clickable { pickingStartDate = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = endDate?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "",
                    onValueChange = {},
                    label = { Text("Fin (optionnel)") },
                    readOnly = true, enabled = false,
                    modifier = Modifier.weight(1f).clickable { pickingEndDate = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(24.dp))
            FormSection("Heures supplémentaires")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = quota, onValueChange = { quota = it },
                label = { Text("Contingent annuel (h)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    OvertimeOption(
                        selected = overtimeMode == OvertimeMode.PAYEE,
                        title = "Toutes payées (majorées)",
                        subtitle = "Les heures sup sont rémunérées avec majoration",
                        onClick = { overtimeMode = OvertimeMode.PAYEE }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    OvertimeOption(
                        selected = overtimeMode == OvertimeMode.CAPITALISEE,
                        title = "Mise sur livret (modulation)",
                        subtitle = "35–43h/sem créditées au livret +25%, au-delà payées +50%",
                        onClick = { overtimeMode = OvertimeMode.CAPITALISEE }
                    )
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(20.dp))
                FormSection("Statut du contrat")
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Archiver ce contrat", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Le contrat apparaîtra dans l'historique passé", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isArchived,
                            onCheckedChange = { isArchived = it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val job = Job(
                        id = existingJob?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.ifBlank { "Nouveau Job" },
                        hourlyRateBrut = rate.toDoubleOrNull() ?: 0.0,
                        weeklyContractHours = hours.toDoubleOrNull() ?: 35.0,
                        annualOvertimeQuota = quota.toIntOrNull() ?: 220,
                        overtimeMode = overtimeMode,
                        livretThreshold = 43.0,
                        soldeLivretHeures = existingJob?.soldeLivretHeures ?: 0.0,
                        targetMonthlySalary = existingJob?.targetMonthlySalary ?: 3000.0,
                        startDate = startDate,
                        endDate = endDate,
                        isMainJob = existingJob?.isMainJob ?: false,
                        isArchived = isArchived
                    )
                    if (isEditing) onJobUpdated(job) else onJobCreated(job)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = name.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isEditing) "Enregistrer les modifications" else "Enregistrer le contrat",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FormSection(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun OvertimeOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
