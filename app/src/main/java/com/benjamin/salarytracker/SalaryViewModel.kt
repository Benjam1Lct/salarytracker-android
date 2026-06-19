package com.benjamin.salarytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** État de l'import de journées (lecture, analyse, succès, erreur). */
sealed interface ImportStatus {
    object Idle : ImportStatus
    data class InProgress(val message: String) : ImportStatus
    data class Success(val count: Int) : ImportStatus
    data class Error(val message: String) : ImportStatus
}

class SalaryViewModel(
    private val remoteService: DataService = RemoteDataService()
) : ViewModel() {

    // Stockage local sur l'appareil (comptes locaux).
    private val localService = LocalDataService(SalaryApp.instance)
    private val syncService = SyncDataService(localService, remoteService as RemoteDataService)

    // Blocage de version : true si l'app est trop ancienne (config Firebase config/minVersionCode).
    private val _updateRequired = MutableStateFlow(false)
    val updateRequired: StateFlow<Boolean> = _updateRequired.asStateFlow()

    private val _userSession = MutableStateFlow<UserSession?>(null)
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    /** Backend actif selon le type de session : local (sur l'appareil) ou distant (Firebase). */
    private val data: DataService
        get() = if (_userSession.value?.isLocal == true) localService else syncService

    val geminiApiKey = MutableStateFlow<String>("")

    /** true = l'utilisateur a choisi d'utiliser l'IA locale (ML Kit + regex) au lieu de Gemini */
    val useLocalAi = MutableStateFlow(false)

    /** Verifier verification ID pour Phone Auth Firebase */
    var phoneVerificationId: String? = null

    val activeSessionStartTime = MutableStateFlow<Long>(0L)
    val activeSessionPauseStartTime = MutableStateFlow<Long>(0L)
    val activeSessionTotalPauseMs = MutableStateFlow<Long>(0L)

    private val prefs = SalaryApp.instance.getSharedPreferences("salary_tracker_prefs", android.content.Context.MODE_PRIVATE)

    val dailyReminderEnabled = MutableStateFlow(false)
    val dailyReminderHour = MutableStateFlow(18)
    val dailyReminderMinute = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val jobs: StateFlow<List<Job>> = _userSession.flatMapLatest { session ->
        if (session != null) {
            data.setUserId(session.uid)
            flow {
                data.transferOldDataIfNeeded(session.uid)
                emitAll(data.getJobs().map { list -> list.distinctBy { it.id } })
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val companies: StateFlow<List<Company>> = _userSession.flatMapLatest { session ->
        if (session != null) data.getCompanies().map { list -> list.distinctBy { it.name.trim().lowercase() } } else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentJobId = MutableStateFlow<String?>(null)
    val currentJobId: StateFlow<String?> = _currentJobId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // État de l'import de journées (affiché sur le tableau de bord).
    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()
    fun clearImportStatus() { _importStatus.value = ImportStatus.Idle }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStatus: StateFlow<ConnectionStatus> = _userSession.flatMapLatest { session ->
        if (session != null) {
            data.connectionStatus()
        } else {
            flowOf(ConnectionStatus.OFFLINE)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionStatus.CONNECTING)

    val currentJob: StateFlow<Job?> = combine(jobs, currentJobId) { jobsList, id ->
        jobsList.find { it.id == id } ?: jobsList.find { it.isMainJob } ?: jobsList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<DayEntry>> = currentJobId.flatMapLatest { id ->
        if (id != null) data.getEntries(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<DayTemplate>> = currentJobId.flatMapLatest { id ->
        if (id != null) data.getTemplates(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val autoRules: StateFlow<List<AutoEntryRule>> = currentJobId.flatMapLatest { id ->
        if (id != null) data.getAutoRules(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val payslips: StateFlow<List<Payslip>> = currentJobId.flatMapLatest { id ->
        if (id != null) data.getPayslips(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val appTheme: StateFlow<String> = _userSession.flatMapLatest { session ->
        if (session != null) data.getAppTheme() else flowOf("purple")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "purple")

    init {
        activeSessionStartTime.value = prefs.getLong("active_session_start_time", 0L)
        activeSessionPauseStartTime.value = prefs.getLong("active_session_pause_start_time", 0L)
        activeSessionTotalPauseMs.value = prefs.getLong("active_session_total_pause_ms", 0L)

        dailyReminderEnabled.value = prefs.getBoolean("daily_reminder_enabled", false)
        dailyReminderHour.value = prefs.getInt("daily_reminder_hour", 18)
        dailyReminderMinute.value = prefs.getInt("daily_reminder_minute", 0)
        useLocalAi.value = prefs.getBoolean("use_local_ai", false)

        val savedUid = prefs.getString("user_uid", null)
        val savedName = prefs.getString("user_name", null)
        val savedEmail = prefs.getString("user_email", null)
        val savedPhoto = prefs.getString("user_photo", null)
        val savedIsLocal = prefs.getBoolean("user_is_local", false)

        if (savedUid != null && savedName != null && savedEmail != null) {
            val session = UserSession(savedUid, savedName, savedEmail, savedPhoto, savedIsLocal)
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
                    isLocal = false
                )
                _userSession.value = session
                loadApiKeyForUser(firebaseUser.uid)
            }
        }

        // Vérifie la version minimale requise (config/minVersionCode dans Firebase) à chaque fois que l'app passe en ligne.
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    checkMinVersion()
                }
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

        // Migration : crée une entité Entreprise pour chaque companyName de contrat
        // qui n'en a pas encore (ainsi les entreprises persistent indépendamment des contrats).
        viewModelScope.launch {
            combine(jobs, companies) { j, c -> j to c }.collect { (jobList, companyList) ->
                if (jobList.isEmpty()) return@collect
                val existingNames = companyList.map { it.name }.toSet()
                val missing = jobList
                    .mapNotNull { it.companyName.ifBlank { null } }
                    .distinct()
                    .filter { it !in existingNames }
                missing.forEach { name -> data.addCompany(Company(name = name)) }
            }
        }
    }

    private fun checkMinVersion() {
        viewModelScope.launch {
            try {
                val snap = com.google.firebase.database.FirebaseDatabase.getInstance(SalaryApp.DB_URL)
                    .getReference("config/minVersionCode").get().await()
                val min = when (val value = snap.value) {
                    is Number -> value.toInt()
                    is String -> value.toDoubleOrNull()?.toInt() ?: 0
                    else -> 0
                }
                if (BuildConfig.VERSION_CODE < min) {
                    _updateRequired.value = true
                }
            } catch (_: Exception) {}
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
                    data.forceRefresh(_currentJobId.value)
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
        viewModelScope.launch { data.setMainJob(jobId) }
    }

    fun addJob(job: Job) {
        viewModelScope.launch { data.addJob(job) }
    }

    /** Supprime un contrat. Si c'était le contrat sélectionné, en resélectionne un autre. */
    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            data.deleteJob(jobId)
            if (_currentJobId.value == jobId) {
                _currentJobId.value = jobs.value.firstOrNull { it.id != jobId }?.id
            }
        }
    }

    /** Crée une entreprise (entité indépendante). [onCreated] reçoit l'entreprise créée. */
    fun createCompany(name: String, onCreated: (Company) -> Unit = {}) {
        val trimmedName = name.trim()
        val existing = companies.value.find { it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (existing != null) {
            onCreated(existing)
            return
        }
        val company = Company(name = trimmedName)
        viewModelScope.launch { data.addCompany(company) }
        onCreated(company)
    }

    /** Supprime une entreprise ET tous ses contrats (avec leurs sous-données). */
    fun deleteCompany(company: Company) {
        viewModelScope.launch {
            jobs.value
                .filter { it.companyId == company.id || (it.companyId == null && it.companyName == company.name) }
                .forEach { data.deleteJob(it.id) }
            data.deleteCompany(company.id)
        }
    }

    /** Supprime toutes les journées de tous les contrats et remet le livret à zéro. */
    fun deleteAllEntries(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val total = entries.value.size
            jobs.value.forEach { job ->
                data.deleteAllEntries(job.id)
                if (job.soldeLivretHeures != 0.0) {
                    data.updateJobFields(job.id, mapOf("soldeLivretHeures" to 0.0))
                }
            }
            onDone(total)
        }
    }

    fun updateJob(job: Job) {
        viewModelScope.launch { data.updateJob(job) }
    }

    /** Met à jour l'objectif mensuel du job courant. */
    fun updateTargetSalary(target: Double) {
        val current = currentJob.value ?: return
        viewModelScope.launch {
            data.updateJob(current.copy(targetMonthlySalary = target))
        }
    }

    fun addDayEntry(entry: DayEntry) {
        val jobId = currentJobId.value ?: return
        val currentJobValue = currentJob.value ?: return

        viewModelScope.launch {
            data.addDayEntry(jobId, entry)
            prefs.edit().putString("last_logged_date", java.time.LocalDate.now().toString()).apply()

            // Recalcul complet du solde livret depuis toutes les entrées + la nouvelle
            val allEntries = entries.value.filter { it.id != entry.id } + entry
            val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(currentJobValue, allEntries)

            if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                data.updateJobFields(
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
            data.deleteDayEntry(jobId, entryId)

            // Recalcul après suppression
            val remainingEntries = entries.value.filter { it.id != entryId }
            val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(currentJobValue, remainingEntries)

            if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                data.updateJobFields(
                    jobId,
                    mapOf("soldeLivretHeures" to newLivretSolde)
                )
            }
        }
    }

    fun addTemplate(template: DayTemplate) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { data.addTemplate(jobId, template) }
    }

    fun importTemplatesFromEntries(onResult: (Int) -> Unit) {
        val jobId = currentJobId.value ?: return
        val currentEntries = entries.value
        val currentTemplates = templates.value

        val uniqueConfigs = currentEntries
            .filter { !it.isLeave }
            .map { Triple(it.startTime, it.endTime, it.pauseMinutes) }
            .distinct()

        viewModelScope.launch {
            var count = 0
            for ((start, end, pause) in uniqueConfigs) {
                val exists = currentTemplates.any { t ->
                    t.startTime == start &&
                    t.endTime == end &&
                    t.pauseBlocks.sumOf { it.durationMinutes } == pause
                }
                if (!exists) {
                    val name = if (pause > 0) {
                        "${start} – ${end} (${pause}m)"
                    } else {
                        "${start} – ${end}"
                    }
                    val template = DayTemplate(
                        name = name,
                        startTime = start,
                        endTime = end,
                        pauseBlocks = if (pause > 0) listOf(PauseBlock(pause)) else emptyList()
                    )
                    data.addTemplate(jobId, template)
                    count++
                }
            }
            onResult(count)
        }
    }

    // ─── Saisie automatique ──────────────────────────────────────────────────

    fun addAutoRule(rule: AutoEntryRule) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch {
            data.addAutoRule(jobId, rule)
            runAutoGeneration() // génère immédiatement le rattrapage
        }
    }

    fun deleteAutoRule(ruleId: String) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { data.deleteAutoRule(jobId, ruleId) }
    }

    // ─── Bulletins de salaire ────────────────────────────────────────────────

    fun addPayslip(payslip: Payslip) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { data.addPayslip(jobId, payslip) }
    }

    fun deletePayslip(payslipId: String) {
        val jobId = currentJobId.value ?: return
        viewModelScope.launch { data.deletePayslip(jobId, payslipId) }
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
                        data.addDayEntry(
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
        viewModelScope.launch { data.deleteTemplate(jobId, templateId) }
    }

    // ─── App Settings ────────────────────────────────────────────────────────

    fun updateTheme(context: android.content.Context, theme: String) {
        viewModelScope.launch {
            data.updateAppTheme(theme)
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
                val cloudKey = data.getGeminiApiKey()
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
        useLocalAi.value = false
        prefs.edit()
            .putString("gemini_api_key_${session.uid}", key)
            .putBoolean("use_local_ai", false)
            .apply()
        viewModelScope.launch {
            try {
                data.saveGeminiApiKey(key)
            } catch (_: Exception) {}
        }
    }

    fun loginWithGoogle(uid: String, email: String, name: String, photoUrl: String?) {
        val session = UserSession(uid, name, email, photoUrl, isLocal = false)
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    fun loginWithEmail(uid: String, email: String) {
        val session = UserSession(
            uid = uid,
            displayName = email.substringBefore("@"),
            email = email,
            photoUrl = null,
            isLocal = false
        )
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    fun signInWithEmailAndPassword(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            if (user != null) {
                                loginWithEmail(user.uid, user.email ?: email)
                                onResult(true, null)
                            } else {
                                onResult(false, "Utilisateur introuvable.")
                            }
                        } else {
                            onResult(false, task.exception?.localizedMessage ?: "Échec de la connexion.")
                        }
                    }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun signUpWithEmailAndPassword(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            if (user != null) {
                                loginWithEmail(user.uid, user.email ?: email)
                                onResult(true, null)
                            } else {
                                onResult(false, "Création réussie, mais utilisateur introuvable.")
                            }
                        } else {
                            onResult(false, task.exception?.localizedMessage ?: "Échec de l'inscription.")
                        }
                    }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    /**
     * Domaine Firebase Hosting du projet — utilisé pour le lien de connexion par e-mail.
     * Le lien généré pointe vers {domaine}/__/auth/links et est intercepté par l'App Link
     * déclaré dans le manifeste.
     */
    private val emailLinkDomain = "salarytracker-879e4.firebaseapp.com"

    /**
     * Envoie un lien e-mail. Si [forLinking] est vrai, le lien servira à *lier* l'adresse
     * au compte déjà connecté (au lieu de créer/ouvrir une session).
     */
    fun sendEmailLink(email: String, forLinking: Boolean = false, onResult: (Boolean, String?) -> Unit) {
        // url = lien de continuation (page de repli si l'app n'est pas installée).
        // NE PAS mettre /__/auth/links ici : c'est le chemin du lien généré par Firebase,
        // pas l'URL de continuation → cela produit un lien invalide.
        val acs = com.google.firebase.auth.ActionCodeSettings.newBuilder()
            .setUrl("https://$emailLinkDomain/finishSignIn")
            .setHandleCodeInApp(true)
            .setAndroidPackageName("com.benjamin.salarytracker", true, null)
            .build()
        com.google.firebase.auth.FirebaseAuth.getInstance()
            .sendSignInLinkToEmail(email, acs)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    prefs.edit()
                        .putString("email_link_pending", email)
                        .putBoolean("email_link_for_linking", forLinking)
                        .apply()
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "Échec de l'envoi du lien.")
                }
            }
    }

    /** Vrai si l'URI fournie est un lien de connexion par e-mail Firebase. */
    fun isEmailSignInLink(link: String): Boolean =
        com.google.firebase.auth.FirebaseAuth.getInstance().isSignInWithEmailLink(link)

    /** Termine le flux e-mail : soit connexion, soit liaison au compte courant. */
    fun completeEmailLinkSignIn(link: String, onResult: (Boolean, String?) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (!auth.isSignInWithEmailLink(link)) {
            onResult(false, null)
            return
        }
        val email = prefs.getString("email_link_pending", null)
        if (email == null) {
            onResult(false, "Adresse e-mail introuvable. Recommencez la demande sur cet appareil.")
            return
        }
        val forLinking = prefs.getBoolean("email_link_for_linking", false)
        val currentUser = auth.currentUser

        if (forLinking && currentUser != null) {
            // Liaison de l'adresse e-mail au compte déjà connecté
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredentialWithLink(email, link)
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener { task ->
                    prefs.edit().remove("email_link_pending").remove("email_link_for_linking").apply()
                    if (task.isSuccessful) onResult(true, null)
                    else onResult(false, mapLinkError(task.exception))
                }
        } else {
            auth.signInWithEmailLink(email, link)
                .addOnCompleteListener { task ->
                    prefs.edit().remove("email_link_pending").remove("email_link_for_linking").apply()
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) loginWithEmail(user.uid, user.email ?: email)
                        onResult(true, null)
                    } else {
                        onResult(false, task.exception?.message ?: "Lien invalide ou expiré.")
                    }
                }
        }
    }

    // ─── Liaison de comptes (account linking) ─────────────────────────────────

    /** Identifiants des fournisseurs déjà liés au compte courant (google.com, phone, password). */
    fun linkedProviderIds(): List<String> =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.providerData
            ?.map { it.providerId }
            ?.filter { it != "firebase" }
            ?: emptyList()

    /** Lie un identifiant Google (idToken) au compte connecté. */
    fun linkWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: return onResult(false, "Aucun utilisateur connecté.")
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        user.linkWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, mapLinkError(task.exception))
            }
    }

    /** Lie un numéro de téléphone (verificationId + code SMS) au compte connecté. */
    fun linkWithPhone(verificationId: String, code: String, onResult: (Boolean, String?) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: return onResult(false, "Aucun utilisateur connecté.")
        val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, code)
        user.linkWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, mapLinkError(task.exception))
            }
    }

    /** Lie un e-mail et un mot de passe au compte connecté. */
    fun linkWithEmailAndPassword(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: return onResult(false, "Aucun utilisateur connecté.")
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.linkWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, mapLinkError(task.exception))
            }
    }

    /** Délie un fournisseur du compte courant (impossible s'il n'en reste qu'un). */
    fun unlinkProvider(providerId: String, onResult: (Boolean, String?) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: return onResult(false, "Aucun utilisateur connecté.")
        if (linkedProviderIds().size <= 1) {
            onResult(false, "Impossible de délier votre seule méthode de connexion.")
            return
        }
        user.unlink(providerId)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, task.exception?.message ?: "Échec de la dissociation.")
            }
    }

    private fun mapLinkError(e: Exception?): String = when (e) {
        is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
            "Cette méthode est déjà associée à un autre compte."
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
            "Identifiant invalide ou expiré."
        else -> e?.message ?: "Échec de la liaison."
    }

    fun loginWithPhone(uid: String, phoneNumber: String) {
        val session = UserSession(
            uid = uid,
            displayName = phoneNumber,
            email = "$phoneNumber@phone.auth",
            photoUrl = null,
            isLocal = false
        )
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    /** Exporte les données du compte local courant vers [uri] (fichier de sauvegarde). */
    fun exportLocalData(context: android.content.Context, uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { localService.exportTo(it) }
                true
            } catch (e: Exception) { false }
            onResult(ok)
        }
    }

    /** Importe un fichier de sauvegarde dans un NOUVEAU compte local, puis s'y connecte. */
    fun importLocalData(context: android.content.Context, uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val uid = "local_import_${System.currentTimeMillis()}"
            localService.setUserId(uid)
            val ok = try {
                context.contentResolver.openInputStream(uri)?.use { localService.importFrom(it) } ?: false
            } catch (e: Exception) { false }
            if (ok) {
                val session = UserSession(uid, "Compte local", "$uid@local", isLocal = true)
                _userSession.value = session
                persistSession(session)
                loadApiKeyForUser(uid)
            }
            onResult(ok)
        }
    }

    /** Crée/ouvre un compte local : données stockées sur l'appareil, hors Firebase. */
    fun loginLocal(name: String) {
        val uid = "local_${name.lowercase().replace(" ", "_")}"
        val session = UserSession(uid, name, "$uid@local", isLocal = true)
        _userSession.value = session
        persistSession(session)
        loadApiKeyForUser(uid)
    }

    /**
     * L'utilisateur refuse d'utiliser Gemini et bascule définitivement en mode IA locale.
     * Ce choix est persisté dans les SharedPrefs.
     */
    fun declineGeminiForever() {
        useLocalAi.value = true
        prefs.edit().putBoolean("use_local_ai", true).apply()
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
            .remove("user_is_local")
            .apply()

        _userSession.value = null
        _currentJobId.value = null
        geminiApiKey.value = ""
    }

    /**
     * Supprime définitivement le compte : données Realtime DB + compte Firebase Auth,
     * puis vide la session locale (ce qui renvoie automatiquement à l'écran de connexion).
     * Les données sont supprimées AVANT la déconnexion pour rester autorisé par les règles.
     */
    fun deleteAccount(onError: (String) -> Unit = {}) {
        val session = _userSession.value
        viewModelScope.launch {
            // 1. Supprime les données utilisateur (tant qu'on est encore authentifié)
            try { data.deleteAllUserData() } catch (_: Exception) {}

            // 2. Supprime le compte Firebase Auth (peut échouer si la connexion n'est pas récente)
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.delete()?.await()
            } catch (e: Exception) {
                onError(e.message ?: "Suppression du compte Firebase impossible (reconnectez-vous puis réessayez).")
            }
            try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}

            // 3. Nettoie les préférences locales
            session?.let {
                prefs.edit()
                    .remove("gemini_api_key_${it.uid}")
                    .remove("has_seen_onboarding_${it.uid}")
                    .apply()
            }
            prefs.edit()
                .remove("user_uid")
                .remove("user_name")
                .remove("user_email")
                .remove("user_photo")
                .remove("user_is_local")
                .apply()

            _userSession.value = null
            _currentJobId.value = null
            geminiApiKey.value = ""
        }
    }

    private fun persistSession(session: UserSession) {
        prefs.edit()
            .putString("user_uid", session.uid)
            .putString("user_name", session.displayName)
            .putString("user_email", session.email)
            .putString("user_photo", session.photoUrl)
            .putBoolean("user_is_local", session.isLocal)
            .apply()
    }

    fun startWorkday() {
        val now = System.currentTimeMillis()
        activeSessionStartTime.value = now
        activeSessionPauseStartTime.value = 0L
        activeSessionTotalPauseMs.value = 0L
        prefs.edit()
            .putLong("active_session_start_time", now)
            .putLong("active_session_pause_start_time", 0L)
            .putLong("active_session_total_pause_ms", 0L)
            .apply()
    }

    fun startPause() {
        val now = System.currentTimeMillis()
        activeSessionPauseStartTime.value = now
        prefs.edit()
            .putLong("active_session_pause_start_time", now)
            .apply()
    }

    fun endPause() {
        val start = activeSessionPauseStartTime.value
        if (start > 0L) {
            val elapsed = System.currentTimeMillis() - start
            val total = activeSessionTotalPauseMs.value + elapsed
            activeSessionTotalPauseMs.value = total
            activeSessionPauseStartTime.value = 0L
            prefs.edit()
                .putLong("active_session_pause_start_time", 0L)
                .putLong("active_session_total_pause_ms", total)
                .apply()
        }
    }

    fun endWorkday() {
        val start = activeSessionStartTime.value
        if (start > 0L) {
            val end = System.currentTimeMillis()
            var totalPause = activeSessionTotalPauseMs.value
            val pauseStart = activeSessionPauseStartTime.value
            if (pauseStart > 0L) {
                totalPause += end - pauseStart
            }

            val zoneId = java.time.ZoneId.systemDefault()
            val startDateTime = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), zoneId)
            val endDateTime = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), zoneId)

            val pauseMinutes = totalPause / 60000

            val entry = DayEntry(
                id = java.util.UUID.randomUUID().toString(),
                jobId = currentJobId.value ?: "",
                date = startDateTime.toLocalDate(),
                startTime = startDateTime.toLocalTime(),
                endTime = endDateTime.toLocalTime(),
                pauseMinutes = pauseMinutes
            )

            addDayEntry(entry)

            activeSessionStartTime.value = 0L
            activeSessionPauseStartTime.value = 0L
            activeSessionTotalPauseMs.value = 0L
            prefs.edit()
                .putLong("active_session_start_time", 0L)
                .putLong("active_session_pause_start_time", 0L)
                .putLong("active_session_total_pause_ms", 0L)
                .apply()
        }
    }

    fun cancelWorkday() {
        activeSessionStartTime.value = 0L
        activeSessionPauseStartTime.value = 0L
        activeSessionTotalPauseMs.value = 0L
        prefs.edit()
            .putLong("active_session_start_time", 0L)
            .putLong("active_session_pause_start_time", 0L)
            .putLong("active_session_total_pause_ms", 0L)
            .apply()
    }

    fun importFile(
        context: android.content.Context,
        uri: android.net.Uri,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit,
        onNeedAiChoice: () -> Unit = {}
    ) {
        val jobId = currentJobId.value
        if (jobId == null) {
            onError("Aucun job sélectionné pour l'importation.")
            return
        }
        val currentJobValue = currentJob.value ?: return

        viewModelScope.launch {
            _isRefreshing.value = true
            _importStatus.value = ImportStatus.InProgress("Lecture du document…")
            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                val isImage = mime.startsWith("image/")

                // Image → OCR on-device (ML Kit) pour obtenir le texte ; sinon lecture directe.
                val text = if (isImage) {
                    _importStatus.value = ImportStatus.InProgress("Lecture de l'image (OCR)…")
                    LocalOcrService(context).ocrImage(uri)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } ?: ""
                }

                if (text.isBlank()) {
                    val msg = if (isImage) "Aucun texte lisible dans l'image." else "Le fichier est vide."
                    _importStatus.value = ImportStatus.Error(msg)
                    onError(msg)
                    return@launch
                }

                // Le parsing CSV ne s'applique qu'aux fichiers texte, pas aux images.
                var importedEntries = if (isImage) emptyList() else ExportService.parseLocalCsv(context, uri, jobId)

                if (importedEntries.isEmpty()) {
                    val apiKeyToUse = geminiApiKey.value
                    // Aucune donnée CSV → on a besoin de l'IA. Si aucun mode IA n'est
                    // encore choisi (pas de clé ET pas d'IA locale validée), on demande
                    // à l'utilisateur via la modal au lieu de basculer silencieusement.
                    if (apiKeyToUse.isBlank() && !useLocalAi.value) {
                        _isRefreshing.value = false
                        _importStatus.value = ImportStatus.Idle
                        onNeedAiChoice()
                        return@launch
                    }
                    _importStatus.value = ImportStatus.InProgress("Analyse des journées…")
                    if (apiKeyToUse.isBlank() || useLocalAi.value) {
                        // Mode IA locale : ML Kit + regex
                        val localOcr = LocalOcrService(context)
                        importedEntries = localOcr.extractDaysFromText(text) { status -> }
                    } else {
                        val ocr = OcrService(context, apiKeyToUse)
                        importedEntries = ocr.extractDaysFromText(text) { status -> }
                    }
                }

                if (importedEntries.isEmpty()) {
                    val msg = "Aucune journée de travail n'a pu être extraite de ce fichier."
                    _importStatus.value = ImportStatus.Error(msg)
                    onError(msg)
                    return@launch
                }

                _importStatus.value = ImportStatus.InProgress("Enregistrement…")

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

                data.addDayEntries(jobId, mergedEntries)

                val newDates = mergedEntries.map { it.date }.toSet()
                val remainingEntries = existingList.filter { it.date !in newDates }
                val allEntries = remainingEntries + mergedEntries
                val newLivretSolde = SalaryCalculator.calculateTotalLivretFromEntries(currentJobValue, allEntries)

                if (newLivretSolde != currentJobValue.soldeLivretHeures) {
                    data.updateJobFields(
                        jobId,
                        mapOf("soldeLivretHeures" to newLivretSolde)
                    )
                }

                prefs.edit().putString("last_logged_date", java.time.LocalDate.now().toString()).apply()
                _importStatus.value = ImportStatus.Success(mergedEntries.size)
                onSuccess(mergedEntries.size)
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.Error("Erreur d'importation : ${e.message}")
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
