package com.benjamin.salarytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    connectionStatus: ConnectionStatus,
    userSession: UserSession?,
    geminiApiKey: String,
    dailyReminderEnabled: Boolean,
    dailyReminderHour: Int,
    dailyReminderMinute: Int,
    onThemeChange: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onToggleDailyReminder: (Boolean) -> Unit,
    onUpdateDailyReminderTime: (Int, Int) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 20.dp, bottom = 120.dp)
        ) {
            ConnectionTag(
                status = connectionStatus,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("PRÉFÉRENCES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text("Paramètres", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (userSession != null) {
                SectionLabel("Profil & Compte")
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userSession.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userSession.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Se déconnecter", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Intelligence Artificielle")
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Text(
                        text = "Clé API Gemini",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Pour importer vos notes libres et relevés d'heures manuscrits grâce à l'IA, renseignez votre propre clé API Gemini. Elle reste confidentielle et stockée sur votre compte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(
                        text = "Obtenir une clé API sur Google AI Studio ↗",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                try {
                                    uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                } catch (_: Exception) {}
                            }
                            .padding(vertical = 4.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    var apiKeyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
                    var showSavedFeedback by remember { mutableStateOf(false) }

                    if (showSavedFeedback) {
                        LaunchedEffect(showSavedFeedback) {
                            kotlinx.coroutines.delay(2000)
                            showSavedFeedback = false
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Clé API Gemini") },
                        singleLine = true,
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onSaveApiKey(apiKeyInput.trim())
                            showSavedFeedback = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showSavedFeedback) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            contentColor = if (showSavedFeedback) Color.White else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (showSavedFeedback) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clé enregistrée !", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Enregistrer la clé", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Notifications")
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rappel quotidien de saisie",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Recevez un rappel en fin de journée si aucune heure n'a été saisie aujourd'hui.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = onToggleDailyReminder
                        )
                    }

                    if (dailyReminderEnabled) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(14.dp))

                        var showTimePicker by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Heure de notification",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val timeStr = String.format(Locale.FRANCE, "%02d:%02d", dailyReminderHour, dailyReminderMinute)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { showTimePicker = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeStr,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (showTimePicker) {
                            val timeState = rememberTimePickerState(
                                initialHour = dailyReminderHour,
                                initialMinute = dailyReminderMinute,
                                is24Hour = true
                            )
                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onUpdateDailyReminderTime(timeState.hour, timeState.minute)
                                        showTimePicker = false
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) { Text("Annuler") }
                                },
                                title = { Text("Choisir l'heure", style = MaterialTheme.typography.titleMedium) },
                                text = {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        TimePicker(state = timeState)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            SectionLabel("Apparence")
            Spacer(Modifier.height(12.dp))
            
            AppCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Couleur d'accent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(20.dp))
                
                var showRestartDialog by remember { mutableStateOf<String?>(null) }
                
                val themes = listOf(
                    Triple("purple", "Violet", Color(0xFF8B7CF6)),
                    Triple("blue", "Bleu", Color(0xFF3B82F6)),
                    Triple("green", "Vert", Color(0xFF10B981)),
                    Triple("orange", "Orange", Color(0xFFF59E0B)),
                    Triple("red", "Rouge", Color(0xFFEF4444)),
                    Triple("pink", "Rose", Color(0xFFEC4899))
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    themes.forEach { (id, label, color) ->
                        ThemeOption(
                            label = label,
                            color = color,
                            isSelected = currentTheme == id,
                            onClick = { showRestartDialog = id }
                        )
                    }
                }

                if (showRestartDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showRestartDialog = null },
                        title = { Text("Changer d'icône ?") },
                        text = { Text("L'application va se fermer pour mettre à jour l'icône sur votre écran d'accueil.") },
                        confirmButton = {
                            Button(onClick = { 
                                val target = showRestartDialog!!
                                showRestartDialog = null
                                onThemeChange(target)
                            }) { Text("Confirmer") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartDialog = null }) { Text("Annuler") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Preview de l'utilisation
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 15.sp
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
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
