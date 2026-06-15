package com.benjamin.salarytracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTemplatesScreen(
    existingTemplates: List<DayTemplate>,
    onAddTemplate: (DayTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRetrieveFromHistory: ((Int) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var editingTemplate by remember { mutableStateOf<DayTemplate?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isAddingNew || editingTemplate != null) "TEMPLATES" else "MES HORAIRES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = if (isAddingNew || editingTemplate != null) "Édition" else "Horaires types",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isAddingNew || editingTemplate != null) {
                            isAddingNew = false
                            editingTemplate = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                },
                actions = {
                    if (!isAddingNew && editingTemplate == null) {
                        IconButton(onClick = {
                            onRetrieveFromHistory { count ->
                                val msg = if (count > 0) "$count templates récupérés !" else "Aucun nouveau template trouvé dans l'historique."
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.History, contentDescription = "Récupérer depuis l'historique")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isAddingNew && editingTemplate == null) {
                ExtendedFloatingActionButton(
                    onClick = { isAddingNew = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nouveau template", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isAddingNew || editingTemplate != null) {
            TemplateEditor(
                initialTemplate = editingTemplate,
                onSave = {
                    onAddTemplate(it)
                    isAddingNew = false
                    editingTemplate = null
                },
                onDelete = { id ->
                    onDeleteTemplate(id)
                    isAddingNew = false
                    editingTemplate = null
                },
                onCancel = {
                    isAddingNew = false
                    editingTemplate = null
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingTemplates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Aucun template enregistré.\nAppuyez sur + pour en créer un.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        onRetrieveFromHistory { count ->
                                            val msg = if (count > 0) "$count templates récupérés !" else "Aucun nouveau template trouvé dans l'historique."
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Récupérer depuis l'historique")
                                }
                            }
                        }
                    }
                }
                items(existingTemplates) { template ->
                    TemplateItem(template, onClick = { editingTemplate = template })
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun TemplateItem(template: DayTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${template.startTime} – ${template.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (template.pauseBlocks.isNotEmpty()) {
                Text(
                    text = "${template.pauseBlocks.sumOf { it.durationMinutes }}min pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TemplateEditor(
    initialTemplate: DayTemplate?,
    onSave: (DayTemplate) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialTemplate?.name ?: "") }
    var startTime by remember { mutableStateOf(initialTemplate?.startTime ?: LocalTime.of(8, 0)) }
    var endTime by remember { mutableStateOf(initialTemplate?.endTime ?: LocalTime.of(17, 0)) }
    val pauseBlocks = remember {
        mutableStateListOf<Long>().apply {
            initialTemplate?.pauseBlocks?.forEach { add(it.durationMinutes) }
        }
    }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nom du template") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Pauses",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        PauseManager(pauseBlocks)

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (name.isBlank()) return@Button
                if (!endTime.isAfter(startTime)) {
                    Toast.makeText(context, "L'heure de fin doit être après le début", Toast.LENGTH_LONG).show()
                } else {
                    onSave(
                        DayTemplate(
                            id = initialTemplate?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            startTime = startTime,
                            endTime = endTime,
                            pauseBlocks = pauseBlocks.map { PauseBlock(it) }
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = name.isNotBlank()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sauvegarder", style = MaterialTheme.typography.labelLarge)
        }

        if (initialTemplate != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onDelete(initialTemplate.id) },
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
                Text("Supprimer", style = MaterialTheme.typography.labelLarge)
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
