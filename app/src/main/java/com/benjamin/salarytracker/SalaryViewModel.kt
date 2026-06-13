package com.benjamin.salarytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SalaryViewModel(
    private val firestoreService: FirestoreService = FirestoreService()
) : ViewModel() {

    private val _userSession = MutableStateFlow<UserSession?>(null)
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    val geminiApiKey = MutableStateFlow<String>("")

    private val prefs = SalaryApp.instance.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)

    val dailyReminderEnabled = MutableStateFlow(false)
    val dailyReminderHour = MutableStateFlow(18)
    val dailyReminderMinute = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val jobs: StateFlow<List<Job>> = _userSession.flatMapLatest { session ->
        if (session != null) {
            firestoreService.setUserId(session.uid)
            flow {
                firestoreService.transferOldDataIfNeeded(session.uid)
                emitAll(firestoreService.getJobs())
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentJobId = MutableStateFlow<String?>(null)
    val currentJobId: StateFlow<String?> = _currentJobId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStatus: StateFlow<ConnectionStatus> = _userSession.flatMapLatest { session ->
        if (session != null) {
            firestoreService.connectionStatus()
        } else {
            flowOf(ConnectionStatus.OFFLINE)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionStatus.CONNECTING)

    val currentJob: StateFlow<Job?> = combine(jobs, currentJobId) { jobsList, id ->
        jobsList.find { it.id == id } ?: jobsList.find { it.isMainJob } ?: jobsList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<DayEntry>> = currentJobId.flatMapLatest { id ->
        if (id != null) firestoreService.getEntries(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<DayTemplate>> = currentJobId.flatMapLatest { id ->
        if (id != null) firestoreService.getTemplates(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val autoRules: StateFlow<List<AutoEntryRule>> = currentJobId.flatMapLatest { id ->
        if (id != null) firestoreService.getAutoRules(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val payslips: StateFlow<List<Payslip>> = currentJobId.flatMapLatest { id ->
        if (id != null) firestoreService.getPayslips(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val appTheme: StateFlow<String> = _userSession.flatMapLatest { session ->
        if (session != null) firestoreService.getAppTheme() else flowOf("purple")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "purple")

    init {
        dailyReminderEnabled.value = prefs.getBoolean("daily_reminder_enabled", false)
        dailyReminderHour.value = prefs.getInt("daily_reminder_hour", 18)
        dailyReminderMinute.value = prefs.getInt("daily_reminder_minute", 0)

        val savedUid = prefs.getString("user_uid", null)
        val savedName = prefs.getString("user_name", null)
        val savedEmail = prefs.getString("user_email", null)
        val savedPhoto = prefs.getString("user_photo", null)
        val savedIsMock = prefs.getBoolean("user_is_mock", false)

        if (savedUid != null && savedName != null && savedEmail != null) {
            val session = UserSession(savedUid, savedName, savedEmail, savedPhoto, savedIsMock)
            _userSession.value = session
            loadApiKeyForUser(savedUid)
        } else {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val session = UserSession(
                    uid = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "Utilisateur Google",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isMock = false
                )
                _userSession.value = session
                loadApiKeyForUser(firebaseUser.uid)
            }
        }

        viewModelScope.launch {
            jobs.collect { jobList ->
                if (jobList.isNotEmpty() && (currentJobId.value == null || jobList.none { it.id == currentJobId.value })) {
                    val mainJob = jobList.find { it.isMainJob }
                    _currentJobId.value = mainJob?.id ?: jobList.first().id
                }
            }
        }
    }

    fun selectJob(jobId: String) {
        _currentJobId.value = jobId
    }

    /**
     * Force une relecture des données depuis le serveur Firestore et affiche
     * l'indicateur de rafraîchissement. Les listeners temps-réel maintiennent
     * déjà les données à jour, ce refresh sert de réconciliation manuelle.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Borné à 4 s : si le serveur ne répond pas, on n'attend pas indéfiniment
                withTimeoutOrNull(4000) {
                    firestoreService.forceRefresh(_currentJobId.value)
                }
                // Durée minimale visible pour le retour utilisateur
                delay(500)
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setMainJob(jobId: String) {
        viewModelScope.launch { firestoreService.setMainJob(jobId) }
    }

    fun addJob(job: Job) {
        viewModelScope.launch { firestoreService.addJob(job) }
    }

    fun updateJob(job: Job) {
        viewModelScope.launch { firestoreService.updateJob(job) }
    }

    /** Met à jour l'objectif mensuel du job courant. */
    fun updateTargetSalary(target: Double) {
        val current = currentJob.value ?: return
        viewModelScope.launch {
            firestoreService.updateJob(current.copy(targetMonthlySalary = target))
        }
    }

    fun addDayEntry(entry: DayEntry) {
        val jobId = currentJobId.value ?: return
        val currentJobValue = currentJob.value ?: return

        viewModelScope.launch {
            firestoreService.addDayEntry(jobId, entry)
            prefs.edit().putString("last_logged_date", java.time.LocalDate.now().toString()).apply()

            // Recalcul complet du solde livret depuis toutes les entrées + la nouvelle
            val allEntries = entries.value.filter { it.id != entry.id } + entry
            val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(allEntries)

            if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                firestoreService.updateJobFields(
                    jobId,
                    mapOf("soldeLivretHeures" to newLivretSolde)
                )
            }
        }
    }

    fun deleteDayEntry(entryId: String) {
        val jobId = currentJobId.value ?: return
        val currentJobValue = currentJob.value ?: return

        viewModelScope.launch {
            firestoreService.deleteDayEntry(jobId, entryId)

            // Recalcul après suppression
            val remainingEntries = entries.value.filter { it.id != entryId }
            val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(remainingEntries)

            if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                firestoreService.updateJobFields(
                    jobId,
                    mapOf("soldeLivretHeures" to newLivretSolde)
                )
            }
        }
    }

    fun addTemplate(template: DayTemplate) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { firestoreService.addTemplate(jobId, template) }
    }

    // ─── Saisie automatique ──────────────────────────────────────────────────

    fun addAutoRule(rule: AutoEntryRule) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch {
            firestoreService.addAutoRule(jobId, rule)
            runAutoGeneration() // génère immédiatement le rattrapage
        }
    }

    fun deleteAutoRule(ruleId: String) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { firestoreService.deleteAutoRule(jobId, ruleId) }
    }

    // ─── Bulletins de salaire ────────────────────────────────────────────────

    fun addPayslip(payslip: Payslip) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { firestoreService.addPayslip(jobId, payslip) }
    }

    fun deletePayslip(payslipId: String) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { firestoreService.deletePayslip(jobId, payslipId) }
    }

    /**
     * Rattrapage : pour chaque règle active, crée les journées manquantes
     * (jours de travail sélectionnés, sans entrée existante) depuis startDate
     * jusqu'à aujourd'hui (ou endDate). Idempotent : ignore les dates déjà saisies.
     */
    fun runAutoGeneration() {
        val jobId = currentJobId.value ?: return
        val rules = autoRules.value.filter { it.active }
        val tmpls = templates.value
        if (rules.isEmpty() || tmpls.isEmpty()) return

        val today = java.time.LocalDate.now()
        viewModelScope.launch {
            val existingDates = entries.value.map { it.date }.toMutableSet()

            for (rule in rules) {
                // Détermine les horaires : template OU journée custom
                val start: java.time.LocalTime
                val end: java.time.LocalTime
                val pause: Long
                if (rule.templateId != null) {
                    val template = tmpls.find { it.id == rule.templateId } ?: continue
                    start = template.startTime
                    end = template.endTime
                    pause = template.pauseBlocks.sumOf { it.durationMinutes }
                } else if (rule.customStartTime != null && rule.customEndTime != null) {
                    start = rule.customStartTime
                    end = rule.customEndTime
                    pause = rule.customPauseMinutes
                } else continue

                val upperBound = when (rule.mode) {
                    AutoEntryMode.PERIOD -> (rule.endDate ?: today).let { if (it.isBefore(today)) it else today }
                    AutoEntryMode.UNTIL_DISABLED -> today
                }
                var d = rule.startDate
                while (!d.isAfter(upperBound)) {
                    if (d.dayOfWeek.value in rule.weekdays && d !in existingDates) {
                        firestoreService.addDayEntry(
                            jobId,
                            DayEntry(
                                id = "auto_$d", // id déterministe → idempotent (pas de doublon)
                                jobId = jobId,
                                date = d,
                                startTime = start,
                                endTime = end,
                                pauseMinutes = pause
                            )
                        )
                        existingDates.add(d)
                    }
                    d = d.plusDays(1)
                }
            }
            // Le solde livret affiché est recalculé depuis les entrées (réactif),
            // pas besoin de le persister ici.
        }
    }

    fun deleteTemplate(templateId: String) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { firestoreService.deleteTemplate(jobId, templateId) }
    }

    // ─── App Settings ────────────────────────────────────────────────────────

    fun updateTheme(context: android.content.Context, theme: String) {
        viewModelScope.launch {
            firestoreService.updateAppTheme(theme)
            switchAppIcon(context, theme)
            // Quitter l'application pour que le changement d'icône soit pris en compte proprement
            (context as? android.app.Activity)?.finishAffinity()
            System.exit(0)
        }
    }

    private fun switchAppIcon(context: android.content.Context, theme: String) {
        val pm = context.packageManager
        val pkgName = context.packageName
        
        val themes = listOf("purple", "blue", "green", "orange", "red", "pink")
        
        themes.forEach { t ->
            val aliasName = "$pkgName.MainActivity${t.replaceFirstChar { it.uppercase() }}"
            val component = android.content.ComponentName(pkgName, aliasName)
            val newState = if (t == theme) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                component,
                newState,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun loadApiKeyForUser(uid: String) {
        val localKey = prefs.getString("gemini_api_key_$uid", "") ?: ""
        geminiApiKey.value = localKey

        viewModelScope.launch {
            try {
                val cloudKey = firestoreService.getGeminiApiKey()
                if (!cloudKey.isNullOrBlank() && cloudKey != localKey) {
                    geminiApiKey.value = cloudKey
                    prefs.edit().putString("gemini_api_key_$uid", cloudKey).apply()
                }
            } catch (_: Exception) {}
        }
    }

    fun saveGeminiApiKey(key: String) {
        val session = _userSession.value ?: return
        geminiApiKey.value = key
        prefs.edit().putString("gemini_api_key_${session.uid}", key).apply()
        viewModelScope.launch {
            try {
                firestoreService.saveGeminiApiKey(key)
            } catch (_: Exception) {}
        }
    }

    fun loginWithGoogle(uid: String, email: String, name: String, photoUrl: String?) {
        val session = UserSession(uid, name, email, photoUrl, isMock = false)
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    fun loginMock(name: String) {
        val uid = "mock_${name.lowercase().replace(" ", "_")}"
        val session = UserSession(uid, name, "${uid}@mock.com", isMock = true)
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    fun logout() {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}

        prefs.edit()
            .remove("user_uid")
            .remove("user_name")
            .remove("user_email")
            .remove("user_photo")
            .remove("user_is_mock")
            .apply()

        _userSession.value = null
        _currentJobId.value = null
        geminiApiKey.value = ""
    }

    private fun persistSession(session: UserSession) {
        prefs.edit()
            .putString("user_uid", session.uid)
            .putString("user_name", session.displayName)
            .putString("user_email", session.email)
            .putString("user_photo", session.photoUrl)
            .putBoolean("user_is_mock", session.isMock)
            .apply()
    }

    fun importFile(
        context: android.content.Context,
        uri: android.net.Uri,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val jobId = currentJobId.value
        if (jobId == null) {
            onError("Aucun job sélectionné pour l'importation.")
            return
        }
        val currentJobValue = currentJob.value ?: return

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: ""

                if (text.isBlank()) {
                    onError("Le fichier est vide.")
                    return@launch
                }

                var importedEntries = ExportService.parseLocalCsv(context, uri, jobId)

                if (importedEntries.isEmpty()) {
                    val apiKeyToUse = geminiApiKey.value
                    if (apiKeyToUse.isBlank()) {
                        onError("Le parsing local CSV a échoué. Pour importer des notes de texte via l'IA, veuillez configurer votre clé API Gemini dans les Paramètres.")
                        return@launch
                    }
                    val ocr = OcrService(context, apiKeyToUse)
                    importedEntries = ocr.extractDaysFromText(text) { status -> }
                }

                if (importedEntries.isEmpty()) {
                    onError("Aucune journée de travail n'a pu être extraite de ce fichier.")
                    return@launch
                }

                val finalEntries = importedEntries.map { it.copy(jobId = jobId) }

                val existingList = entries.value
                val dateToExistingEntry = existingList.associateBy { it.date }

                val mergedEntries = finalEntries.map { newEntry ->
                    val existing = dateToExistingEntry[newEntry.date]
                    if (existing != null) {
                        newEntry.copy(id = existing.id)
                    } else {
                        newEntry
                    }
                }

                firestoreService.addDayEntries(jobId, mergedEntries)

                val newDates = mergedEntries.map { it.date }.toSet()
                val remainingEntries = existingList.filter { it.date !in newDates }
                val allEntries = remainingEntries + mergedEntries
                val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(allEntries)

                if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                    firestoreService.updateJobFields(
                        jobId,
                        mapOf("soldeLivretHeures" to newLivretSolde)
                    )
                }

                prefs.edit().putString("last_logged_date", java.time.LocalDate.now().toString()).apply()
                onSuccess(mergedEntries.size)
            } catch (e: Exception) {
                onError("Erreur d'importation : ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setDailyReminder(context: android.content.Context, enabled: Boolean, hour: Int = dailyReminderHour.value, minute: Int = dailyReminderMinute.value) {
        dailyReminderEnabled.value = enabled
        dailyReminderHour.value = hour
        dailyReminderMinute.value = minute
        
        prefs.edit().apply {
            putBoolean("daily_reminder_enabled", enabled)
            putInt("daily_reminder_hour", hour)
            putInt("daily_reminder_minute", minute)
        }.apply()

        if (enabled) {
            scheduleReminder(context, hour, minute)
        } else {
            cancelReminder(context)
        }
    }

    private fun scheduleReminder(context: android.content.Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, NotificationReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun cancelReminder(context: android.content.Context) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, NotificationReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
