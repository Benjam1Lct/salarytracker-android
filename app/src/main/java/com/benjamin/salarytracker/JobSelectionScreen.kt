package com.benjamin.salarytracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import java.time.LocalDate
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*

/** Label lisible du type de contrat (localisé) */
@Composable
fun ContractType.label(): String = when (this) {
    ContractType.CDI -> stringResource(R.string.ct_cdi)
    ContractType.CDD -> stringResource(R.string.ct_cdd)
    ContractType.INTERIM -> stringResource(R.string.ct_interim)
    ContractType.MISSION -> stringResource(R.string.ct_mission)
    ContractType.ALTERNANCE -> stringResource(R.string.ct_alternance)
    ContractType.STAGE -> stringResource(R.string.ct_stage)
}

@Composable
fun JobSelectionScreen(
    jobs: List<Job>,
    companies: List<Company>,
    selectedJobId: String,
    onJobSelect: (Job) -> Unit,
    onToggleMainJob: (String) -> Unit,
    onAddJob: () -> Unit,
    onEditJob: (Job) -> Unit,
    onAddContractToCompany: (companyId: String, companyName: String) -> Unit,
    onDeleteJob: (String) -> Unit = {},
    onAddCompany: () -> Unit = {},
    onDeleteCompany: (Company) -> Unit = {},
    onBack: () -> Unit
) {
    // Renvoie les contrats rattachés à une entreprise (par id, ou par nom pour les contrats hérités).
    fun jobsOfCompany(company: Company, source: List<Job>) = source.filter {
        it.companyId == company.id || (it.companyName.isNotBlank() && it.companyName.trim().equals(company.name.trim(), ignoreCase = true))
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 110.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.js_my_jobs), color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(stringResource(R.string.js_selection), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                SquareIconButton(Icons.Default.Close, onClick = onBack)
            }

            Spacer(Modifier.height(22.dp))

            val today = LocalDate.now()
            val activeJobs = jobs.filter { !it.isArchived && (it.endDate == null || !it.endDate.isBefore(today)) }
            val archivedJobs = jobs.filter { it.isArchived || (it.endDate != null && it.endDate.isBefore(today)) }
            var showArchives by remember { mutableStateOf(false) }

            if (jobs.isEmpty() && companies.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WorkOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.js_no_company), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.js_create_company_hint), color = InkMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                // Une carte par entreprise (même sans contrat), dédupliquée par nom
                val uniqueCompanies = companies.distinctBy { it.name.trim().lowercase() }
                uniqueCompanies.forEachIndexed { index, company ->
                    Appear(delayMillis = 40 * index) {
                        CompanyGroup(
                            company = company,
                            jobs = jobsOfCompany(company, activeJobs),
                            selectedJobId = selectedJobId,
                            onJobSelect = onJobSelect,
                            onEditJob = onEditJob,
                            onToggleMainJob = onToggleMainJob,
                            onAddContractToCompany = onAddContractToCompany,
                            onDeleteJob = onDeleteJob,
                            onDeleteCompany = onDeleteCompany
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // Contrats actifs sans entreprise rattachée (cas résiduel)
                val orphanActive = activeJobs.filter { j -> uniqueCompanies.none { c -> j in jobsOfCompany(c, activeJobs) } }
                orphanActive.forEach { job ->
                    JobRow(
                        job = job,
                        isSelected = job.id == selectedJobId,
                        onClick = { onJobSelect(job) },
                        onEdit = { onEditJob(job) },
                        onToggleMain = { onToggleMainJob(job.id) }
                    )
                    Spacer(Modifier.height(12.dp))
                }

            }

            Spacer(Modifier.height(8.dp))
            AppButton(
                text = stringResource(R.string.js_new_company),
                onClick = onAddCompany,
                leading = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )

            if (archivedJobs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showArchives = !showArchives },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, OutlineLt)
                ) {
                    Icon(
                        imageVector = if (showArchives) Icons.Default.KeyboardArrowUp else Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (showArchives) stringResource(R.string.js_hide_archives, archivedJobs.size) else stringResource(R.string.js_show_archives, archivedJobs.size),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (showArchives) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.js_archived), color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(8.dp))
                    archivedJobs.forEachIndexed { index, job ->
                        Appear(delayMillis = 40 * index) {
                            JobRow(
                                job = job,
                                isSelected = job.id == selectedJobId,
                                onClick = { onJobSelect(job) },
                                onEdit = { onEditJob(job) },
                                onToggleMain = { onToggleMainJob(job.id) }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyGroup(
    company: Company,
    jobs: List<Job>,
    selectedJobId: String,
    onJobSelect: (Job) -> Unit,
    onEditJob: (Job) -> Unit,
    onToggleMainJob: (String) -> Unit,
    onAddContractToCompany: (companyId: String, companyName: String) -> Unit,
    onDeleteJob: (String) -> Unit,
    onDeleteCompany: (Company) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val companyName = company.name
    val companyAnchorId = company.id
    var showDeleteCompany by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val hasSelected = jobs.any { it.id == selectedJobId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .then(
                if (hasSelected) Modifier.border(BorderStroke(2.dp, accent), RoundedCornerShape(20.dp))
                else Modifier.border(BorderStroke(1.dp, OutlineLt), RoundedCornerShape(20.dp))
            )
            .animateContentSize()
    ) {
        // Header entreprise
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (hasSelected) accent else accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Business, null, tint = if (hasSelected) Color.White else accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(companyName, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    stringResource(R.string.js_contracts_count, jobs.size),
                    color = InkMuted, fontSize = 12.sp
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(color = OutlineLt, modifier = Modifier.padding(horizontal = 14.dp))
            if (jobs.isEmpty()) {
                Text(
                    stringResource(R.string.js_no_contract),
                    color = InkMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                jobs.forEach { job ->
                    JobRow(
                        job = job,
                        isSelected = job.id == selectedJobId,
                        onClick = { onJobSelect(job) },
                        onEdit = { onEditJob(job) },
                        onToggleMain = { onToggleMainJob(job.id) },
                        isCompact = true
                    )
                }
            }
            HorizontalDivider(color = OutlineLt, modifier = Modifier.padding(horizontal = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).clickable { onAddContractToCompany(companyAnchorId, companyName) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.js_add_contract), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { showDeleteCompany = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.js_delete_company), tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDeleteCompany) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteCompany = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFEF4444)) },
            title = { androidx.compose.material3.Text(stringResource(R.string.js_delete_company_q)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.js_delete_company_text, companyName, jobs.size)) },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showDeleteCompany = false
                        onDeleteCompany(company)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) { androidx.compose.material3.Text(stringResource(R.string.set_delete)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showDeleteCompany = false }) { androidx.compose.material3.Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun JobRow(
    job: Job,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleMain: () -> Unit,
    isCompact: Boolean = false,
    onAddContract: (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isCompact) Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .then(
                        if (isSelected) Modifier.border(BorderStroke(2.dp, accent), RoundedCornerShape(20.dp))
                        else Modifier.border(BorderStroke(1.dp, OutlineLt), RoundedCornerShape(20.dp))
                    )
                else Modifier.background(
                    if (isSelected) accent.copy(alpha = 0.05f) else Color.Transparent
                )
            )
            .clickable { onClick() }
            .padding(if (isCompact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isCompact) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) accent else accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WorkOutline, null, tint = if (isSelected) Color.White else accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(8.dp))
            // Badge type de contrat
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) accent else accent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    job.contractType.label(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else accent
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        val totalHours = job.weeklyContractHours + job.includedOvertimeHours
        val ctLabel = job.contractType.label()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Column(Modifier.weight(1f)) {
            Text(
                if (isCompact) stringResource(R.string.js_rate_hours, fmtMoneyNum(job.hourlyRateBrut), fmtMoneyNum(totalHours))
                else job.name,
                color = if (isCompact) InkMuted else Ink,
                fontSize = if (isCompact) 13.sp else 15.sp,
                fontWeight = if (isCompact) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1
            )
            if (!isCompact) {
                Text(
                    stringResource(R.string.js_contract_detail, ctLabel, fmtMoneyNum(job.hourlyRateBrut), fmtMoneyNum(totalHours)),
                    color = InkMuted, fontSize = 12.sp
                )
            }
            val dateText = remember(job.startDate, job.endDate) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                when {
                    job.startDate != null && job.endDate != null -> ctx.getString(R.string.js_date_range, job.startDate.format(formatter), job.endDate.format(formatter))
                    job.startDate != null -> ctx.getString(R.string.js_date_from, job.startDate.format(formatter))
                    job.endDate != null -> ctx.getString(R.string.js_date_until, job.endDate.format(formatter))
                    else -> null
                }
            }
            if (dateText != null) {
                Text(
                    text = dateText,
                    color = InkMuted,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // Ajouter un contrat lié (entreprise)
        if (onAddContract != null && !isCompact) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onAddContract() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PostAdd, stringResource(R.string.js_add_contract), tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        // Éditer
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onEdit() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        // Emploi principal (étoile)
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { onToggleMain() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (job.isMainJob) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(R.string.comp_main_job),
                tint = if (job.isMainJob) Color(0xFFFFC107) else InkMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
