package com.benjamin.salarytracker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
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
    useLocalAi: Boolean = false,
    isSubscribed: Boolean = false,
    presetCompanyId: String? = null,
    presetCompanyName: String? = null,
    onNeedGeminiKey: () -> Unit = {},
    onJobCreated: (Job) -> Unit,
    onJobUpdated: (Job) -> Unit = {},
    onDeleteJob: (Job) -> Unit = {},
    onBack: () -> Unit
) {
    val isEditing = existingJob != null
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Mode "ajout d'un contrat rattaché à une entreprise existante"
    val isAddingToCompany = !isEditing && presetCompanyName != null

    var name by remember { mutableStateOf(existingJob?.name ?: "") }
    var companyNameInput by remember { mutableStateOf(existingJob?.companyName ?: "") }
    var rate by remember { mutableStateOf(existingJob?.hourlyRateBrut?.toString() ?: "") }
    var hours by remember { mutableStateOf(existingJob?.weeklyContractHours?.toString() ?: "35") }
    var includedOvertimeHours by remember { mutableStateOf(existingJob?.includedOvertimeHours?.toString() ?: "0") }
    var includedOvertimeRatePercent by remember { mutableStateOf(existingJob?.includedOvertimeRatePercent ?: 25.0) }
    var quota by remember { mutableStateOf(existingJob?.annualOvertimeQuota?.toString() ?: "220") }
    var overtimeMode by remember { mutableStateOf(existingJob?.overtimeMode ?: OvertimeMode.PAYEE) }
    var startDate by remember { mutableStateOf(existingJob?.startDate) }
    var endDate by remember { mutableStateOf(existingJob?.endDate) }
    var isArchived by remember { mutableStateOf(existingJob?.isArchived ?: false) }
    var contractType by remember { mutableStateOf(existingJob?.contractType ?: ContractType.CDI) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var analysisStatus by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ocrService = remember(geminiApiKey, isSubscribed) { OcrService(context, geminiApiKey, useBackend = isSubscribed) }

    // Noms lisibles des fichiers sélectionnés (pour l'affichage)
    val selectedNames = remember(selectedUris) {
        selectedUris.map { ocrService.resolveFileName(it) }
    }

    val localOcrService = remember { LocalOcrService(context) }

    fun runAnalysis() {
        if (selectedUris.isEmpty() || isAnalyzing) return
        // Aucun mode IA choisi → on ouvre la modal de configuration (sauf abonnés).
        if (!isSubscribed && !useLocalAi && geminiApiKey.isBlank()) {
            onNeedGeminiKey()
            return
        }
        val analyzeLocally = !isSubscribed && (useLocalAi || geminiApiKey.isBlank())
        isAnalyzing = true
        analysisStatus = context.getString(R.string.cj_preparing)
        scope.launch {
            val result = if (analyzeLocally) {
                localOcrService.extractMultiContractData(selectedUris) { status -> analysisStatus = status }
            } else {
                ocrService.extractMultiContractData(selectedUris) { status -> analysisStatus = status }
            }
            when (result) {
                is ContractAnalysis.Success -> {
                    val job = result.job
                    name = job.name
                    if (!isAddingToCompany) {
                        companyNameInput = job.companyName.ifBlank { job.name }
                    }
                    contractType = job.contractType
                    rate = job.hourlyRateBrut.toString()
                    hours = job.weeklyContractHours.toString()
                    includedOvertimeHours = job.includedOvertimeHours.toString()
                    includedOvertimeRatePercent = job.includedOvertimeRatePercent
                    quota = job.annualOvertimeQuota.toString()
                    overtimeMode = job.overtimeMode
                    job.startDate?.let { startDate = it }
                    job.endDate?.let { endDate = it }
                    Toast.makeText(context, context.getString(R.string.cj_analysis_done), Toast.LENGTH_SHORT).show()
                }
                is ContractAnalysis.Failure -> {
                    Toast.makeText(context, context.getString(R.string.ps_failed, result.reason), Toast.LENGTH_LONG).show()
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
                }) { Text(stringResource(R.string.common_confirm)) }
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
                            if (isEditing) stringResource(R.string.cj_edit_header) else stringResource(R.string.cj_new_header),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            when {
                                isEditing -> existingJob!!.name
                                isAddingToCompany -> presetCompanyName!!
                                else -> stringResource(R.string.cj_setup)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_close))
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(stringResource(R.string.cj_auto_analysis), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cj_import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                        analysisStatus.ifBlank { stringResource(R.string.cj_analyzing, selectedUris.size) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // ── Fichiers sélectionnés : liste + bouton d'analyse ──
                            selectedUris.isNotEmpty() -> {
                                Text(
                                    stringResource(R.string.cj_files_selected, selectedUris.size),
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
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { selectedUris = selectedUris.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, stringResource(R.string.cj_remove), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                                        Text(stringResource(R.string.common_add), style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { runAnalysis() },
                                        modifier = Modifier.weight(1.4f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.cj_analyze), style = MaterialTheme.typography.labelLarge)
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
                                    Text(stringResource(R.string.cj_import_btn), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            FormSection(stringResource(R.string.cj_general_info))
            Spacer(Modifier.height(12.dp))

            if (isAddingToCompany) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Business, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.cj_attached_to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            Text(presetCompanyName!!, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Nom de l'entreprise — masqué en mode "ajout à une entreprise" (déjà fixé par le bandeau)
            if (!isAddingToCompany) {
                OutlinedTextField(
                    value = companyNameInput,
                    onValueChange = { companyNameInput = it },
                    label = { Text(stringResource(R.string.ma_company_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cj_job_name)) },
                placeholder = { Text(stringResource(R.string.cj_job_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.cj_gross_rate)) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it },
                    label = { Text(stringResource(R.string.cj_hours_week)) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startDate?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.cj_start_contract)) },
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
                    label = { Text(stringResource(R.string.cj_end_optional)) },
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
            FormSection(stringResource(R.string.cj_contract_type))
            Spacer(Modifier.height(12.dp))

            // Type de contrat — chips horizontaux
            val contractTypes = listOf(
                ContractType.CDI to stringResource(R.string.ct_cdi),
                ContractType.CDD to stringResource(R.string.ct_cdd),
                ContractType.INTERIM to stringResource(R.string.ct_interim),
                ContractType.MISSION to stringResource(R.string.ct_mission),
                ContractType.ALTERNANCE to stringResource(R.string.ct_alternance),
                ContractType.STAGE to stringResource(R.string.ct_stage)
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                items(contractTypes) { (type, label) ->
                    FilterChip(
                        selected = contractType == type,
                        onClick = { contractType = type },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            FormSection(stringResource(R.string.cj_overtime))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = includedOvertimeHours, onValueChange = { includedOvertimeHours = it },
                label = { Text(stringResource(R.string.cj_included_ot)) },
                supportingText = { Text(stringResource(R.string.cj_included_ot_hint)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))

            if ((includedOvertimeHours.toDoubleOrNull() ?: 0.0) > 0.0) {
                Text(stringResource(R.string.cj_ot_rate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0.0 to stringResource(R.string.cj_rate_normal), 25.0 to stringResource(R.string.cj_rate_plus25)).forEach { (valeur, label) ->
                        FilterChip(
                            selected = includedOvertimeRatePercent == valeur,
                            onClick = { includedOvertimeRatePercent = valeur },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = quota, onValueChange = { quota = it },
                label = { Text(stringResource(R.string.cj_annual_quota)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))

            val hVal = hours.toDoubleOrNull() ?: 35.0
            val ioVal = includedOvertimeHours.toDoubleOrNull() ?: 0.0
            val baseThreshold = hVal + ioVal
            val threshold = existingJob?.livretThreshold ?: 43.0

            val fmtBaseThreshold = if (baseThreshold % 1.0 == 0.0) baseThreshold.toInt().toString() else baseThreshold.toString()
            val fmtThreshold = if (threshold % 1.0 == 0.0) threshold.toInt().toString() else threshold.toString()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp)
            ) {
                val overtimeOptions = listOf(
                    Triple(
                        OvertimeMode.PAYEE,
                        stringResource(R.string.cj_ot_payee_t),
                        stringResource(R.string.cj_ot_payee_d, fmtBaseThreshold, fmtThreshold)
                    ),
                    Triple(
                        OvertimeMode.CAPITALISEE,
                        stringResource(R.string.cj_ot_cap_t),
                        stringResource(R.string.cj_ot_cap_d, fmtBaseThreshold, fmtThreshold)
                    ),
                    Triple(
                        OvertimeMode.RECUPERATION,
                        stringResource(R.string.cj_ot_recup_t),
                        stringResource(R.string.cj_ot_recup_d)
                    ),
                    Triple(
                        OvertimeMode.CET,
                        stringResource(R.string.cj_ot_cet_t),
                        stringResource(R.string.cj_ot_cet_d, fmtBaseThreshold, fmtThreshold)
                    ),
                    Triple(
                        OvertimeMode.MIXTE,
                        stringResource(R.string.cj_ot_mixte_t),
                        stringResource(R.string.cj_ot_mixte_d)
                    ),
                    Triple(
                        OvertimeMode.FORFAIT_JOURS,
                        stringResource(R.string.cj_ot_forfait_t),
                        stringResource(R.string.cj_ot_forfait_d)
                    )
                )
                Column(modifier = Modifier.padding(4.dp)) {
                    overtimeOptions.forEachIndexed { index, (mode, title, subtitle) ->
                        OvertimeOption(
                            selected = overtimeMode == mode,
                            title = title,
                            subtitle = subtitle,
                            onClick = { overtimeMode = mode }
                        )
                        if (index < overtimeOptions.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(20.dp))
                FormSection(stringResource(R.string.cj_contract_status))
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
                            Text(stringResource(R.string.cj_archive), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.cj_archive_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val hVal = hours.toDoubleOrNull() ?: 35.0
                    val ioVal = includedOvertimeHours.toDoubleOrNull() ?: 0.0
                    val job = Job(
                        id = existingJob?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.ifBlank { (if (isAddingToCompany) presetCompanyName else companyNameInput) ?: context.getString(R.string.cj_new_job_fallback) },
                        companyName = if (isAddingToCompany) presetCompanyName!! else companyNameInput.ifBlank { name.ifBlank { context.getString(R.string.cj_new_job_fallback) } },
                        companyId = presetCompanyId ?: existingJob?.companyId,
                        contractType = contractType,
                        hourlyRateBrut = rate.toDoubleOrNull() ?: 0.0,
                        weeklyContractHours = hVal,
                        includedOvertimeHours = ioVal,
                        includedOvertimeRatePercent = includedOvertimeRatePercent,
                        annualOvertimeQuota = quota.toIntOrNull() ?: 220,
                        overtimeMode = overtimeMode,
                        livretThreshold = existingJob?.livretThreshold ?: 43.0,
                        soldeLivretHeures = existingJob?.soldeLivretHeures ?: 0.0,
                        targetMonthlySalary = existingJob?.targetMonthlySalary ?: 3000.0,
                        startDate = startDate,
                        endDate = endDate,
                        isMainJob = existingJob?.isMainJob ?: false,
                        isArchived = isArchived
                    )
                    if (isEditing) {
                        onJobUpdated(job)
                        onBack()
                    } else {
                        // La navigation vers "Mes emplois" est gérée par onJobCreated côté parent.
                        onJobCreated(job)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = if (isAddingToCompany) name.isNotBlank() else companyNameInput.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isEditing) stringResource(R.string.cj_save_changes) else stringResource(R.string.cj_save_contract),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (isEditing) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cj_delete_contract), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDeleteConfirm && existingJob != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.cj_delete_q)) },
            text = { Text(stringResource(R.string.cj_delete_text, existingJob.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteJob(existingJob)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.set_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
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
