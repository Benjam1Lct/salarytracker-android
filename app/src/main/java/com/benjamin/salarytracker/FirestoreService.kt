package com.benjamin.salarytracker

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/**
 * Couche de persistance basée sur Firebase **Realtime Database**.
 * (Le nom de la classe reste FirestoreService pour compatibilité avec le ViewModel.)
 *
 * Arborescence :
 *   users/{userId}/jobs/{jobId}                → champs du job
 *   users/{userId}/jobs/{jobId}/days/{entryId} → journées
 *   users/{userId}/jobs/{jobId}/templates/{id} → horaires types
 */
class FirestoreService {
    companion object {
        val forcedStatus = MutableStateFlow<ConnectionStatus?>(null)

        fun reconnect(dbUrl: String) {
            forcedStatus.value = ConnectionStatus.CONNECTING
            try {
                FirebaseDatabase.getInstance(dbUrl).goOnline()
            } catch (_: Exception) {}
            // Reset après 5 secondes pour laisser le listener Firebase prendre le relais
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                delay(5000)
                forcedStatus.value = null
            }
        }
    }

    private val db = FirebaseDatabase.getInstance(SalaryApp.DB_URL)
    private var _userId = "user_benjamin"
    val userId: String get() = _userId
    var userRef = db.getReference("users").child(_userId)

    fun setUserId(newUserId: String) {
        _userId = newUserId
        userRef = db.getReference("users").child(newUserId)
        try { userRef.keepSynced(true) } catch (_: Exception) {}
    }

    /** Supprime définitivement toutes les données de l'utilisateur courant (users/{uid}). */
    suspend fun deleteAllUserData() {
        userRef.removeValue().await()
    }

    suspend fun transferOldDataIfNeeded(newUserId: String) {
        if (newUserId == "user_benjamin") return
        val oldRef = db.getReference("users").child("user_benjamin")
        val newRef = db.getReference("users").child(newUserId)
        try {
            val snapshot = oldRef.get().await()
            if (snapshot.exists()) {
                // Copy data to new path
                newRef.setValue(snapshot.value).await()
                // Remove old path to avoid re-copying later
                oldRef.removeValue().await()
                android.util.Log.d("FirestoreService", "Data successfully migrated from user_benjamin to $newUserId")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Migration of old data failed", e)
        }
    }

    init {
        // Garde le sous-arbre de l'utilisateur en cache local et synchronisé,
        // même sans listener actif → données dispo hors-ligne immédiatement.
        try { userRef.keepSynced(true) } catch (_: Exception) {}
    }

    /**
     * Surveille l'état de connexion via le chemin spécial `.info/connected`.
     * Paliers si la connexion tarde :
     *   CONNECTING → (slowTimeoutMs) SLOW → (offlineTimeoutMs) OFFLINE.
     * Dès que le serveur répond → CONNECTED (les timers sont annulés).
     */
    fun connectionStatus(
        slowTimeoutMs: Long = 5000,
        offlineTimeoutMs: Long = 12000
    ): Flow<ConnectionStatus> = callbackFlow {
        // Initial state
        trySend(ConnectionStatus.CONNECTING)

        // Paliers du PREMIER démarrage : Connexion → (slow) instable → (offline) hors-ligne
        val initialSlow = launch { delay(slowTimeoutMs); trySend(ConnectionStatus.SLOW) }
        val initialOffline = launch { delay(offlineTimeoutMs); trySend(ConnectionStatus.OFFLINE) }

        var wasConnected = false
        var currentStatus = ConnectionStatus.CONNECTING
        var dropJob: kotlinx.coroutines.Job? = null

        fun updateStatus(newStatus: ConnectionStatus) {
            currentStatus = newStatus
            trySend(newStatus)
        }

        val forcedJob = launch {
            forcedStatus.collect { forced ->
                if (forced != null) {
                    if (forced == ConnectionStatus.CONNECTING) {
                        initialSlow.cancel()
                        initialOffline.cancel()
                        dropJob?.cancel()
                    }
                    updateStatus(forced)
                }
            }
        }

        val ref = db.getReference(".info/connected")
        val firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    initialSlow.cancel(); initialOffline.cancel(); dropJob?.cancel()
                    wasConnected = true
                    updateStatus(ConnectionStatus.CONNECTED)
                } else if (wasConnected) {
                    // La connexion a chuté → étape "instable", puis hors-ligne si ça persiste
                    if (currentStatus == ConnectionStatus.CONNECTED) {
                        updateStatus(ConnectionStatus.SLOW)
                        dropJob?.cancel()
                        dropJob = launch { delay(6000); updateStatus(ConnectionStatus.OFFLINE) }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                updateStatus(ConnectionStatus.OFFLINE)
            }
        }

        val connectivityManager = SalaryApp.instance.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // Si le réseau revient alors qu'on était hors-ligne ou instable, on affiche "Connexion en cours..."
                // pendant que Firebase négocie sa reconnexion en tâche de fond.
                if (currentStatus == ConnectionStatus.OFFLINE || currentStatus == ConnectionStatus.SLOW) {
                    initialSlow.cancel(); initialOffline.cancel(); dropJob?.cancel()
                    updateStatus(ConnectionStatus.CONNECTING)
                }
            }
            override fun onLost(network: android.net.Network) {
                // Dès que le système d'exploitation signale la perte totale de réseau, on bascule direct en offline
                initialSlow.cancel(); initialOffline.cancel(); dropJob?.cancel()
                updateStatus(ConnectionStatus.OFFLINE)
            }
        }

        ref.addValueEventListener(firebaseListener)
        try {
            val builder = android.net.NetworkRequest.Builder()
            connectivityManager?.registerNetworkCallback(builder.build(), networkCallback)
        } catch (_: Exception) {}

        awaitClose {
            initialSlow.cancel(); initialOffline.cancel(); dropJob?.cancel()
            forcedJob.cancel()
            ref.removeEventListener(firebaseListener)
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }

    fun getCompanies(): Flow<List<Company>> = callbackFlow {
        val ref = userRef.child("companies")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    Company(id = id, name = child.child("name").getValue(String::class.java) ?: "")
                })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addCompany(company: Company, updatedAt: Long = System.currentTimeMillis()) {
        userRef.child("companies").child(company.id)
            .setValue(mapOf("name" to company.name, "updatedAt" to updatedAt)).await()
    }

    suspend fun deleteCompany(companyId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("companies").child(companyId).removeValue().await()
        userRef.child("deleted").child(companyId).setValue(timestamp).await()
    }

    fun getJobs(): Flow<List<Job>> = callbackFlow {
        val ref = userRef.child("jobs")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toJob() })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getEntries(jobId: String): Flow<List<DayEntry>> = callbackFlow {
        val ref = userRef.child("jobs").child(jobId).child("days")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.toDayEntry(jobId) }
                    .sortedByDescending { it.date }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getTemplates(jobId: String): Flow<List<DayTemplate>> = callbackFlow {
        val ref = userRef.child("jobs").child(jobId).child("templates")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toDayTemplate() })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addJob(job: Job, updatedAt: Long = System.currentTimeMillis()) {
        val map = job.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(job.id).setValue(map).await()
    }

    suspend fun updateJob(job: Job, updatedAt: Long = System.currentTimeMillis()) {
        val map = job.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(job.id).updateChildren(map).await()
    }

    /** Supprime un contrat et toutes ses sous-données (journées, templates, etc.). */
    suspend fun deleteJob(jobId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("jobs").child(jobId).removeValue().await()
        userRef.child("deleted").child(jobId).setValue(timestamp).await()
    }

    suspend fun updateJobFields(jobId: String, fields: Map<String, Any?>, updatedAt: Long = System.currentTimeMillis()) {
        val map = fields.toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(jobId).updateChildren(map).await()
    }

    /** Force une lecture pour réconcilier (Realtime DB est déjà live via les listeners). */
    suspend fun forceRefresh(jobId: String?) {
        userRef.child("jobs").get().await()
        if (jobId != null) {
            userRef.child("jobs").child(jobId).child("days").get().await()
        }
    }

    suspend fun setMainJob(jobId: String, updatedAt: Long = System.currentTimeMillis()) {
        val snapshot = userRef.child("jobs").get().await()
        val updates = mutableMapOf<String, Any?>()
        for (child in snapshot.children) {
            child.key?.let { id ->
                updates["$id/isMainJob"] = (id == jobId)
                updates["$id/updatedAt"] = updatedAt
            }
        }
        userRef.child("jobs").updateChildren(updates).await()
    }

    suspend fun addDayEntry(jobId: String, entry: DayEntry, updatedAt: Long = System.currentTimeMillis()) {
        val map = entry.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(jobId).child("days")
            .child(entry.id).setValue(map).await()
    }

    suspend fun addDayEntries(jobId: String, entries: List<DayEntry>, updatedAt: Long = System.currentTimeMillis()) {
        val updates = mutableMapOf<String, Any?>()
        entries.forEach { entry ->
            val map = entry.toMap().toMutableMap()
            map["updatedAt"] = updatedAt
            updates[entry.id] = map
        }
        userRef.child("jobs").child(jobId).child("days").updateChildren(updates).await()
    }

    suspend fun deleteDayEntry(jobId: String, entryId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("jobs").child(jobId).child("days")
            .child(entryId).removeValue().await()
        userRef.child("deleted").child(entryId).setValue(timestamp).await()
    }

    /** Supprime toutes les journées d'un contrat. */
    suspend fun deleteAllEntries(jobId: String) {
        val snapshot = userRef.child("jobs").child(jobId).child("days").get().await()
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any?>()
        for (child in snapshot.children) {
            child.key?.let { id -> updates["deleted/$id"] = now }
        }
        if (updates.isNotEmpty()) {
            userRef.updateChildren(updates).await()
        }
        userRef.child("jobs").child(jobId).child("days").removeValue().await()
    }

    suspend fun addTemplate(jobId: String, template: DayTemplate, updatedAt: Long = System.currentTimeMillis()) {
        val map = template.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(jobId).child("templates")
            .child(template.id).setValue(map).await()
    }

    suspend fun deleteTemplate(jobId: String, templateId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("jobs").child(jobId).child("templates")
            .child(templateId).removeValue().await()
        userRef.child("deleted").child(templateId).setValue(timestamp).await()
    }

    // ─── Règles de saisie automatique ─────────────────────────────────────────

    fun getAutoRules(jobId: String): Flow<List<AutoEntryRule>> = callbackFlow {
        val ref = userRef.child("jobs").child(jobId).child("autoRules")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toAutoEntryRule() })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addAutoRule(jobId: String, rule: AutoEntryRule, updatedAt: Long = System.currentTimeMillis()) {
        val map = rule.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(jobId).child("autoRules")
            .child(rule.id).setValue(map).await()
    }

    suspend fun deleteAutoRule(jobId: String, ruleId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("jobs").child(jobId).child("autoRules")
            .child(ruleId).removeValue().await()
        userRef.child("deleted").child(ruleId).setValue(timestamp).await()
    }

    // ─── Paramètres de l'application ─────────────────────────────────────────

    fun getAppTheme(): Flow<String> = callbackFlow {
        val ref = userRef.child("settings").child("appTheme")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java) ?: "purple")
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateAppTheme(theme: String, updatedAt: Long = System.currentTimeMillis()) {
        val updates = mapOf(
            "appTheme" to theme,
            "appThemeUpdatedAt" to updatedAt
        )
        userRef.child("settings").updateChildren(updates).await()
    }

    suspend fun saveGeminiApiKey(key: String, updatedAt: Long = System.currentTimeMillis()) {
        val updates = mapOf(
            "geminiApiKey" to key,
            "geminiApiKeyUpdatedAt" to updatedAt
        )
        userRef.child("settings").updateChildren(updates).await()
    }

    suspend fun getGeminiApiKey(): String? {
        return try {
            userRef.child("settings").child("geminiApiKey").get().await().getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ─── Bulletins de salaire ─────────────────────────────────────────────────

    fun getPayslips(jobId: String): Flow<List<Payslip>> = callbackFlow {
        val ref = userRef.child("jobs").child(jobId).child("payslips")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toPayslip() }.sortedByDescending { it.year * 100 + it.month })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addPayslip(jobId: String, payslip: Payslip, updatedAt: Long = System.currentTimeMillis()) {
        val map = payslip.toMap().toMutableMap()
        map["updatedAt"] = updatedAt
        userRef.child("jobs").child(jobId).child("payslips")
            .child(payslip.id).setValue(map).await()
    }

    suspend fun deletePayslip(jobId: String, payslipId: String, timestamp: Long = System.currentTimeMillis()) {
        userRef.child("jobs").child(jobId).child("payslips")
            .child(payslipId).removeValue().await()
        userRef.child("deleted").child(payslipId).setValue(timestamp).await()
    }
}

// ─── Helpers de lecture (Realtime DB renvoie Long/Double indifféremment) ──────

private fun DataSnapshot.str(key: String): String? =
    child(key).getValue(String::class.java)

private fun DataSnapshot.dbl(key: String): Double? =
    child(key).getValue(Double::class.java)
        ?: child(key).getValue(Long::class.java)?.toDouble()

private fun DataSnapshot.lng(key: String): Long? =
    child(key).getValue(Long::class.java)
        ?: child(key).getValue(Double::class.java)?.toLong()

private fun DataSnapshot.bool(key: String): Boolean? =
    child(key).getValue(Boolean::class.java)

// ─── Conversions ─────────────────────────────────────────────────────────────

fun DataSnapshot.toJob(): Job? {
    return try {
        val overtimeModeStr = str("overtimeMode") ?: "PAYEE"
        val overtimeMode = try { OvertimeMode.valueOf(overtimeModeStr) } catch (_: Exception) { OvertimeMode.PAYEE }
        val contractTypeStr = str("contractType") ?: "CDI"
        val contractType = try { ContractType.valueOf(contractTypeStr) } catch (_: Exception) { ContractType.CDI }
        Job(
            id = key ?: return null,
            name = str("name") ?: "",
            companyName = str("companyName") ?: "",
            companyId = str("companyId"),
            contractType = contractType,
            hourlyRateBrut = dbl("hourlyRateBrut") ?: 0.0,
            weeklyContractHours = dbl("weeklyContractHours") ?: 35.0,
            includedOvertimeHours = dbl("includedOvertimeHours") ?: 0.0,
            includedOvertimeRatePercent = dbl("includedOvertimeRatePercent") ?: 25.0,
            annualOvertimeQuota = lng("annualOvertimeQuota")?.toInt() ?: 220,
            overtimeMode = overtimeMode,
            livretThreshold = dbl("livretThreshold") ?: 43.0,
            soldeLivretHeures = dbl("soldeLivretHeures") ?: 0.0,
            targetMonthlySalary = dbl("targetMonthlySalary") ?: 3000.0,
            startDate = str("startDate")?.let { LocalDate.parse(it) },
            endDate = str("endDate")?.let { LocalDate.parse(it) },
            isMainJob = bool("isMainJob") ?: false,
            isArchived = bool("isArchived") ?: false
        )
    } catch (e: Exception) { null }
}

fun DataSnapshot.toDayEntry(jobId: String): DayEntry? {
    return try {
        DayEntry(
            id = key ?: return null,
            jobId = jobId,
            date = LocalDate.parse(str("date")),
            startTime = LocalTime.parse(str("startTime")),
            endTime = LocalTime.parse(str("endTime")),
            pauseMinutes = lng("pauseMinutes") ?: 0L,
            isLeave = bool("isLeave") ?: false
        )
    } catch (e: Exception) { null }
}

fun DataSnapshot.toDayTemplate(): DayTemplate? {
    return try {
        val pauses = child("pauseMinutesList").children.mapNotNull {
            it.getValue(Long::class.java) ?: it.getValue(Double::class.java)?.toLong()
        }
        DayTemplate(
            id = key ?: return null,
            name = str("name") ?: "",
            startTime = LocalTime.parse(str("startTime")),
            endTime = LocalTime.parse(str("endTime")),
            pauseBlocks = pauses.map { PauseBlock(it) }
        )
    } catch (e: Exception) { null }
}

fun Job.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "companyName" to companyName,
    "companyId" to companyId,
    "contractType" to contractType.name,
    "hourlyRateBrut" to hourlyRateBrut,
    "weeklyContractHours" to weeklyContractHours,
    "includedOvertimeHours" to includedOvertimeHours,
    "includedOvertimeRatePercent" to includedOvertimeRatePercent,
    "annualOvertimeQuota" to annualOvertimeQuota,
    "overtimeMode" to overtimeMode.name,
    "livretThreshold" to livretThreshold,
    "soldeLivretHeures" to soldeLivretHeures,
    "targetMonthlySalary" to targetMonthlySalary,
    "startDate" to startDate?.toString(),
    "endDate" to endDate?.toString(),
    "isMainJob" to isMainJob,
    "isArchived" to isArchived
)

fun DayEntry.toMap(): Map<String, Any?> = mapOf(
    "date" to date.toString(),
    "startTime" to startTime.toString(),
    "endTime" to endTime.toString(),
    "pauseMinutes" to pauseMinutes,
    "isLeave" to isLeave,
    "totalHoursCalculated" to totalHours
)

fun DayTemplate.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "startTime" to startTime.toString(),
    "endTime" to endTime.toString(),
    "pauseMinutesList" to pauseBlocks.map { it.durationMinutes }
)

fun DataSnapshot.toAutoEntryRule(): AutoEntryRule? {
    return try {
        val days = child("weekdays").children.mapNotNull {
            (it.getValue(Long::class.java) ?: it.getValue(Double::class.java)?.toLong())?.toInt()
        }.toSet()
        AutoEntryRule(
            id = key ?: return null,
            templateId = str("templateId"),
            templateName = str("templateName") ?: "",
            customStartTime = str("customStartTime")?.let { LocalTime.parse(it) },
            customEndTime = str("customEndTime")?.let { LocalTime.parse(it) },
            customPauseMinutes = lng("customPauseMinutes") ?: 0L,
            active = bool("active") ?: true,
            mode = AutoEntryMode.valueOf(str("mode") ?: "UNTIL_DISABLED"),
            startDate = str("startDate")?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            endDate = str("endDate")?.let { LocalDate.parse(it) },
            weekdays = days.ifEmpty { setOf(1, 2, 3, 4, 5) }
        )
    } catch (e: Exception) { null }
}

fun AutoEntryRule.toMap(): Map<String, Any?> = mapOf(
    "templateId" to templateId,
    "templateName" to templateName,
    "customStartTime" to customStartTime?.toString(),
    "customEndTime" to customEndTime?.toString(),
    "customPauseMinutes" to customPauseMinutes,
    "active" to active,
    "mode" to mode.name,
    "startDate" to startDate.toString(),
    "endDate" to endDate?.toString(),
    "weekdays" to weekdays.sorted().map { it.toLong() }
)

fun DataSnapshot.toPayslip(): Payslip? {
    return try {
        Payslip(
            id = key ?: return null,
            month = lng("month")?.toInt() ?: return null,
            year = lng("year")?.toInt() ?: return null,
            brut = dbl("brut") ?: 0.0,
            net = dbl("net") ?: 0.0,
            netImposable = dbl("netImposable") ?: 0.0,
            heuresPayees = dbl("heuresPayees") ?: 0.0,
            cotisations = dbl("cotisations") ?: 0.0,
            uploadedAt = lng("uploadedAt") ?: 0L
        )
    } catch (e: Exception) { null }
}

fun Payslip.toMap(): Map<String, Any?> = mapOf(
    "month" to month,
    "year" to year,
    "brut" to brut,
    "net" to net,
    "netImposable" to netImposable,
    "heuresPayees" to heuresPayees,
    "cotisations" to cotisations,
    "uploadedAt" to uploadedAt
)
