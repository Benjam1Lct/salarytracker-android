package com.benjamin.salarytracker

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.salarytracker.ui.theme.*

@Composable
fun JobSelectionScreen(
    jobs: List<Job>,
    selectedJobId: String,
    onJobSelect: (Job) -> Unit,
    onToggleMainJob: (String) -> Unit,
    onAddJob: () -> Unit,
    onEditJob: (Job) -> Unit,
    onBack: () -> Unit
) {
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
                    Text("MES EMPLOIS", color = InkMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text("Sélection", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                SquareIconButton(Icons.Default.Close, onClick = onBack)
            }

            Spacer(Modifier.height(22.dp))

            if (jobs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WorkOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(18.dp))
                    Text("Aucun emploi", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Ajoute ton premier contrat pour commencer.", color = InkMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                val activeJobs = jobs.filter { !it.isArchived }
                val archivedJobs = jobs.filter { it.isArchived }

                if (activeJobs.isNotEmpty()) {
                    Text("Contrats actifs", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(8.dp))
                    activeJobs.forEachIndexed { index, job ->
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

                if (archivedJobs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Contrats passés / Archivés", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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

            Spacer(Modifier.height(8.dp))
            AppButton(
                text = "Nouveau job",
                onClick = onAddJob,
                leading = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun JobRow(
    job: Job,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleMain: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .then(if (isSelected) Modifier.border(BorderStroke(2.dp, accent), RoundedCornerShape(20.dp)) else Modifier.border(BorderStroke(1.dp, OutlineLt), RoundedCornerShape(20.dp)))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) accent else accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.WorkOutline, null, tint = if (isSelected) Color.White else accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(job.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${fmtMoneyNum(job.hourlyRateBrut)} €/h · ${fmtMoneyNum(job.weeklyContractHours)} h/sem",
                color = InkMuted, fontSize = 12.sp
            )
        }
        // Éditer
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onEdit() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Edit, "Modifier", tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        // Emploi principal (étoile)
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { onToggleMain() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (job.isMainJob) Icons.Default.Star else Icons.Default.StarBorder,
                "Emploi principal",
                tint = if (job.isMainJob) Color(0xFFFFC107) else InkMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
