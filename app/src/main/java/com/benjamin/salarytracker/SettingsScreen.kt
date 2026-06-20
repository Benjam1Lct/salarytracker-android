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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    isSubscribed: Boolean = false,
    onOpenSubscription: () -> Unit = {},
    appLanguage: String = "system",
    onLanguageChange: (String) -> Unit = {},
    dailyReminderEnabled: Boolean,
    dailyReminderHour: Int,
    dailyReminderMinute: Int,
    onThemeChange: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onToggleDailyReminder: (Boolean) -> Unit,
    onUpdateDailyReminderTime: (Int, Int) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDeleteAllEntries: () -> Unit = {},
    linkedProviders: List<String> = emptyList(),
    onLinkGoogle: () -> Unit = {},
    onLinkEmailAndPassword: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onUnlinkProvider: (String) -> Unit = {},
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteEntriesConfirm by remember { mutableStateOf(false) }
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
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.set_prefs_header), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(stringResource(R.string.set_title), color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Carte SalaryTracker Pro ───────────────────────────────────
            if (userSession != null && !userSession.isLocal) {
                AppCard(padding = 0.dp, onClick = onOpenSubscription) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sub_settings_entry), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (isSubscribed) stringResource(R.string.sub_active) else stringResource(R.string.sub_settings_entry_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSubscribed) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (userSession != null) {
                SectionLabel(stringResource(R.string.set_profile_account))
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
                        Text(stringResource(R.string.set_logout), fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.set_delete_account), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text(stringResource(R.string.set_delete_confirm_title)) },
                        text = {
                            Text(stringResource(R.string.set_delete_confirm_text))
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDeleteAccount()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) { Text(stringResource(R.string.set_delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.set_data))
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Text(stringResource(R.string.set_delete_all_days), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.set_delete_all_days_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteEntriesConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.set_delete_all_days), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showDeleteEntriesConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteEntriesConfirm = false },
                        icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text(stringResource(R.string.set_delete_all_days_q)) },
                        text = { Text(stringResource(R.string.set_delete_all_days_confirm)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteEntriesConfirm = false
                                    onDeleteAllEntries()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) { Text(stringResource(R.string.set_delete_all)) }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteEntriesConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.set_login_methods))
                Spacer(Modifier.height(12.dp))
                LinkedAccountsSection(
                    linkedProviders = linkedProviders,
                    onLinkGoogle = onLinkGoogle,
                    onLinkEmailAndPassword = onLinkEmailAndPassword,
                    onUnlinkProvider = onUnlinkProvider
                )

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.set_ai))
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Text(
                        text = stringResource(R.string.set_gemini_key),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.set_gemini_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(
                        text = stringResource(R.string.set_get_api_key),
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
                        label = { Text(stringResource(R.string.set_gemini_key)) },
                        singleLine = true,
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Clé déjà enregistrée et champ inchangé → mode suppression.
                    val isExistingKey = geminiApiKey.isNotBlank() && apiKeyInput == geminiApiKey

                    Button(
                        onClick = {
                            if (showSavedFeedback) return@Button
                            if (isExistingKey) {
                                onSaveApiKey("")
                                apiKeyInput = ""
                            } else {
                                onSaveApiKey(apiKeyInput.trim())
                                showSavedFeedback = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                showSavedFeedback -> Color(0xFF10B981)
                                isExistingKey -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.primary
                            },
                            contentColor = when {
                                showSavedFeedback -> Color.White
                                isExistingKey -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        when {
                            showSavedFeedback -> {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.set_key_saved), fontWeight = FontWeight.SemiBold)
                            }
                            isExistingKey -> {
                                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.set_delete_key), fontWeight = FontWeight.SemiBold)
                            }
                            else -> Text(stringResource(R.string.set_save_key), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.set_notifications))
                Spacer(Modifier.height(12.dp))
                AppCard(padding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.set_daily_reminder),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.set_daily_reminder_desc),
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
                                text = stringResource(R.string.set_notif_time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", dailyReminderHour, dailyReminderMinute)
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
                                    }) { Text(stringResource(R.string.common_ok)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
                                },
                                title = { Text(stringResource(R.string.comp_pick_time), style = MaterialTheme.typography.titleMedium) },
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

            // ─── Langue ────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.set_language))
            Spacer(Modifier.height(12.dp))
            AppCard(padding = 18.dp) {
                val langCodes = listOf("system", "fr", "en")
                SegmentedToggle(
                    options = listOf(
                        stringResource(R.string.set_lang_system),
                        stringResource(R.string.set_lang_fr),
                        stringResource(R.string.set_lang_en)
                    ),
                    selected = langCodes.indexOf(appLanguage).coerceAtLeast(0),
                    onSelect = { idx ->
                        val newLang = langCodes[idx]
                        if (newLang != appLanguage) onLanguageChange(newLang)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.set_appearance))
            Spacer(Modifier.height(12.dp))
            
            AppCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.set_accent_color), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(20.dp))
                
                var showRestartDialog by remember { mutableStateOf<String?>(null) }
                
                val themes = listOf(
                    Triple("purple", stringResource(R.string.set_theme_purple), Color(0xFF8B7CF6)),
                    Triple("blue", stringResource(R.string.set_theme_blue), Color(0xFF3B82F6)),
                    Triple("green", stringResource(R.string.set_theme_green), Color(0xFF10B981)),
                    Triple("orange", stringResource(R.string.set_theme_orange), Color(0xFFF59E0B)),
                    Triple("red", stringResource(R.string.set_theme_red), Color(0xFFEF4444)),
                    Triple("pink", stringResource(R.string.set_theme_pink), Color(0xFFEC4899))
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
                        title = { Text(stringResource(R.string.set_change_icon_q)) },
                        text = { Text(stringResource(R.string.set_change_icon_text)) },
                        confirmButton = {
                            Button(onClick = {
                                val target = showRestartDialog!!
                                showRestartDialog = null
                                onThemeChange(target)
                            }) { Text(stringResource(R.string.common_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartDialog = null }) { Text(stringResource(R.string.common_cancel)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedAccountsSection(
    linkedProviders: List<String>,
    onLinkGoogle: () -> Unit,
    onLinkEmailAndPassword: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onUnlinkProvider: (String) -> Unit
) {
    var showEmailPasswordDialog by remember { mutableStateOf(false) }

    data class ProviderRow(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onLink: () -> Unit)
    val rows = listOf(
        ProviderRow("google.com", "Google", Icons.Default.AccountCircle, onLinkGoogle),
        ProviderRow("password", "E-mail", Icons.Default.Email) { showEmailPasswordDialog = true }
    )

    AppCard(padding = 18.dp) {
        Text(
            stringResource(R.string.set_link_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        rows.forEachIndexed { index, row ->
            val linked = row.id in linkedProviders
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(row.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (linked) {
                    Icon(Icons.Default.CheckCircle, stringResource(R.string.set_linked), tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    if (linkedProviders.size > 1) {
                        TextButton(onClick = { onUnlinkProvider(row.id) }) {
                            Text(stringResource(R.string.set_unlink), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    OutlinedButton(onClick = row.onLink, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(R.string.set_link), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (index < rows.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    // Dialog : lier un e-mail et mot de passe
    if (showEmailPasswordDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val genericError = stringResource(R.string.set_generic_error)

        AlertDialog(
            onDismissRequest = { if (!loading) showEmailPasswordDialog = false },
            icon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.set_link_email_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.set_email_addr)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.set_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )
                    if (loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.set_linking), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        loading = true
                        errorMessage = null
                        onLinkEmailAndPassword(email.trim(), password) { success, err ->
                            loading = false
                            if (success) {
                                showEmailPasswordDialog = false
                            } else {
                                errorMessage = err ?: genericError
                            }
                        }
                    },
                    enabled = email.isNotBlank() && password.length >= 6 && !loading
                ) {
                    Text(stringResource(R.string.set_link))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEmailPasswordDialog = false },
                    enabled = !loading
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
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
