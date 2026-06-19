package com.benjamin.salarytracker

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Implémentation locale de [DataService] : toutes les données sont stockées
 * **sur l'appareil** (fichier sérialisé dans filesDir), jamais sur Firebase.
 * Utilisée pour les comptes locaux. Toujours "connecté" (pas de réseau).
 */
class LocalDataService(private val context: Context) : DataService {

    private data class LocalDb(
        val companies: MutableList<Company> = mutableListOf(),
        val jobs: MutableList<Job> = mutableListOf(),
        val entries: MutableMap<String, MutableList<DayEntry>> = mutableMapOf(),
        val templates: MutableMap<String, MutableList<DayTemplate>> = mutableMapOf(),
        val autoRules: MutableMap<String, MutableList<AutoEntryRule>> = mutableMapOf(),
        val payslips: MutableMap<String, MutableList<Payslip>> = mutableMapOf(),
        var appTheme: String = "purple",
        var geminiKey: String? = null
    ) : java.io.Serializable

    private var uid: String = "local"
    private var db = LocalDb()

    private val companiesFlow = MutableStateFlow<List<Company>>(emptyList())
    private val jobsFlow = MutableStateFlow<List<Job>>(emptyList())
    private val themeFlow = MutableStateFlow("purple")
    private val entriesFlows = mutableMapOf<String, MutableStateFlow<List<DayEntry>>>()
    private val templatesFlows = mutableMapOf<String, MutableStateFlow<List<DayTemplate>>>()
    private val autoRulesFlows = mutableMapOf<String, MutableStateFlow<List<AutoEntryRule>>>()
    private val payslipsFlows = mutableMapOf<String, MutableStateFlow<List<Payslip>>>()

    private fun file(): File = File(context.filesDir, "local_data_$uid.dat")

    private fun persist() {
        try {
            ObjectOutputStream(file().outputStream().buffered()).use { it.writeObject(db) }
        } catch (_: Exception) {}
    }

    private fun load() {
        db = try {
            if (file().exists())
                ObjectInputStream(file().inputStream().buffered()).use { it.readObject() as LocalDb }
            else LocalDb()
        } catch (_: Exception) {
            LocalDb()
        }
        var modified = false
        
        // Assainir les doublons par nom (fusionner les entreprises de même nom)
        val companiesByName = db.companies.groupBy { it.name.trim().lowercase() }
        var companyDeduplicated = false
        val newCompaniesList = mutableListOf<Company>()
        
        companiesByName.forEach { (_, group) ->
            if (group.size > 1) {
                val mainCompany = group.first()
                newCompaniesList.add(mainCompany)
                val duplicateIds = group.drop(1).map { it.id }.toSet()
                
                // Mettre à jour les jobs rattachés aux doublons
                for (i in db.jobs.indices) {
                    val job = db.jobs[i]
                    if (job.companyId in duplicateIds) {
                        db.jobs[i] = job.copy(companyId = mainCompany.id)
                        modified = true
                    }
                }
                companyDeduplicated = true
            } else if (group.isNotEmpty()) {
                newCompaniesList.add(group.first())
            }
        }
        
        if (companyDeduplicated) {
            db.companies.clear()
            db.companies.addAll(newCompaniesList)
            modified = true
        }

        // Assainir également les doublons éventuels par ID restants
        val uniqueCompanies = db.companies.distinctBy { it.id }.toMutableList()
        if (uniqueCompanies.size != db.companies.size) {
            db.companies.clear()
            db.companies.addAll(uniqueCompanies)
            modified = true
        }
        val uniqueJobs = db.jobs.distinctBy { it.id }.toMutableList()
        if (uniqueJobs.size != db.jobs.size) {
            db.jobs.clear()
            db.jobs.addAll(uniqueJobs)
            modified = true
        }

        db.entries.forEach { (_, list) ->
            for (i in list.indices) {
                if (list[i].id.isEmpty()) {
                    list[i] = list[i].copy(id = java.util.UUID.randomUUID().toString())
                    modified = true
                }
            }
        }
        if (modified) {
            persist()
        }
        emitAll()
    }

    private fun entriesOf(jobId: String) =
        db.entries.getOrPut(jobId) { mutableListOf() }
    private fun templatesOf(jobId: String) =
        db.templates.getOrPut(jobId) { mutableListOf() }
    private fun rulesOf(jobId: String) =
        db.autoRules.getOrPut(jobId) { mutableListOf() }
    private fun payslipsOf(jobId: String) =
        db.payslips.getOrPut(jobId) { mutableListOf() }

    private fun emitJobs() { jobsFlow.value = db.jobs.toList() }
    private fun emitCompanies() { companiesFlow.value = db.companies.toList() }
    private fun emitEntries(jobId: String) {
        entriesFlows[jobId]?.value = entriesOf(jobId).sortedByDescending { it.date }
    }
    private fun emitTemplates(jobId: String) {
        templatesFlows[jobId]?.value = templatesOf(jobId).toList()
    }
    private fun emitRules(jobId: String) {
        autoRulesFlows[jobId]?.value = rulesOf(jobId).toList()
    }
    private fun emitPayslips(jobId: String) {
        payslipsFlows[jobId]?.value = payslipsOf(jobId).sortedByDescending { it.year * 100 + it.month }
    }
    private fun emitAll() {
        emitJobs()
        emitCompanies()
        themeFlow.value = db.appTheme
        entriesFlows.keys.forEach { emitEntries(it) }
        templatesFlows.keys.forEach { emitTemplates(it) }
        autoRulesFlows.keys.forEach { emitRules(it) }
        payslipsFlows.keys.forEach { emitPayslips(it) }
    }

    override fun setUserId(newUserId: String) {
        uid = newUserId
        load()
    }

    override suspend fun transferOldDataIfNeeded(newUserId: String) { /* local : rien à migrer */ }

    override suspend fun deleteAllUserData() {
        try { file().delete() } catch (_: Exception) {}
        db = LocalDb()
        emitAll()
    }

    override fun connectionStatus(): Flow<ConnectionStatus> = flowOf(ConnectionStatus.CONNECTED)
    override fun getCompanies(): Flow<List<Company>> = companiesFlow
    override fun getJobs(): Flow<List<Job>> = jobsFlow

    override suspend fun addCompany(company: Company) {
        db.companies.removeAll { it.id == company.id }
        db.companies.add(company)
        persist(); emitCompanies()
    }

    override suspend fun deleteCompany(companyId: String) {
        db.companies.removeAll { it.id == companyId }
        persist(); emitCompanies()
    }
    override fun getAppTheme(): Flow<String> = themeFlow

    override fun getEntries(jobId: String): Flow<List<DayEntry>> =
        entriesFlows.getOrPut(jobId) { MutableStateFlow(entriesOf(jobId).sortedByDescending { it.date }) }
    override fun getTemplates(jobId: String): Flow<List<DayTemplate>> =
        templatesFlows.getOrPut(jobId) { MutableStateFlow(templatesOf(jobId).toList()) }
    override fun getAutoRules(jobId: String): Flow<List<AutoEntryRule>> =
        autoRulesFlows.getOrPut(jobId) { MutableStateFlow(rulesOf(jobId).toList()) }
    override fun getPayslips(jobId: String): Flow<List<Payslip>> =
        payslipsFlows.getOrPut(jobId) { MutableStateFlow(payslipsOf(jobId).sortedByDescending { it.year * 100 + it.month }) }

    override suspend fun addJob(job: Job) {
        db.jobs.removeAll { it.id == job.id }
        db.jobs.add(job)
        persist(); emitJobs()
    }

    override suspend fun updateJob(job: Job) = addJob(job)

    override suspend fun deleteJob(jobId: String) {
        db.jobs.removeAll { it.id == jobId }
        db.entries.remove(jobId)
        db.templates.remove(jobId)
        db.autoRules.remove(jobId)
        db.payslips.remove(jobId)
        persist()
        emitJobs(); emitEntries(jobId); emitTemplates(jobId); emitRules(jobId); emitPayslips(jobId)
    }

    override suspend fun updateJobFields(jobId: String, fields: Map<String, Any?>) {
        val idx = db.jobs.indexOfFirst { it.id == jobId }
        if (idx < 0) return
        var j = db.jobs[idx]
        fields.forEach { (key, value) ->
            j = when (key) {
                "soldeLivretHeures" -> j.copy(soldeLivretHeures = (value as? Number)?.toDouble() ?: j.soldeLivretHeures)
                "targetMonthlySalary" -> j.copy(targetMonthlySalary = (value as? Number)?.toDouble() ?: j.targetMonthlySalary)
                "isMainJob" -> j.copy(isMainJob = value as? Boolean ?: j.isMainJob)
                "isArchived" -> j.copy(isArchived = value as? Boolean ?: j.isArchived)
                else -> j
            }
        }
        db.jobs[idx] = j
        persist(); emitJobs()
    }

    override suspend fun forceRefresh(jobId: String?) { /* local : données déjà à jour */ }

    override suspend fun setMainJob(jobId: String) {
        for (i in db.jobs.indices) {
            db.jobs[i] = db.jobs[i].copy(isMainJob = db.jobs[i].id == jobId)
        }
        persist(); emitJobs()
    }

    override suspend fun addDayEntry(jobId: String, entry: DayEntry) {
        val list = entriesOf(jobId)
        list.removeAll { it.id == entry.id }
        list.add(entry)
        persist(); emitEntries(jobId)
    }

    override suspend fun addDayEntries(jobId: String, entries: List<DayEntry>) {
        val list = entriesOf(jobId)
        entries.forEach { e -> list.removeAll { it.id == e.id } }
        list.addAll(entries)
        persist(); emitEntries(jobId)
    }

    override suspend fun deleteDayEntry(jobId: String, entryId: String) {
        entriesOf(jobId).removeAll { it.id == entryId }
        persist(); emitEntries(jobId)
    }

    override suspend fun deleteAllEntries(jobId: String) {
        db.entries[jobId]?.clear()
        persist(); emitEntries(jobId)
    }

    override suspend fun addTemplate(jobId: String, template: DayTemplate) {
        val list = templatesOf(jobId)
        list.removeAll { it.id == template.id }
        list.add(template)
        persist(); emitTemplates(jobId)
    }

    override suspend fun deleteTemplate(jobId: String, templateId: String) {
        templatesOf(jobId).removeAll { it.id == templateId }
        persist(); emitTemplates(jobId)
    }

    override suspend fun addAutoRule(jobId: String, rule: AutoEntryRule) {
        val list = rulesOf(jobId)
        list.removeAll { it.id == rule.id }
        list.add(rule)
        persist(); emitRules(jobId)
    }

    override suspend fun deleteAutoRule(jobId: String, ruleId: String) {
        rulesOf(jobId).removeAll { it.id == ruleId }
        persist(); emitRules(jobId)
    }

    override suspend fun addPayslip(jobId: String, payslip: Payslip) {
        val list = payslipsOf(jobId)
        list.removeAll { it.id == payslip.id }
        list.add(payslip)
        persist(); emitPayslips(jobId)
    }

    override suspend fun deletePayslip(jobId: String, payslipId: String) {
        payslipsOf(jobId).removeAll { it.id == payslipId }
        persist(); emitPayslips(jobId)
    }

    override suspend fun updateAppTheme(theme: String) {
        db.appTheme = theme
        persist(); themeFlow.value = theme
    }

    override suspend fun saveGeminiApiKey(key: String) {
        db.geminiKey = key
        persist()
    }

    override suspend fun getGeminiApiKey(): String? = db.geminiKey

    // ─── Sauvegarde / restauration (compte local) ─────────────────────────────

    /** Écrit toutes les données locales courantes dans [out] (format sérialisé). */
    fun exportTo(out: java.io.OutputStream) {
        ObjectOutputStream(out.buffered()).use { it.writeObject(db) }
    }

    /** Restaure les données depuis [input]. Renvoie true si réussi. */
    fun importFrom(input: java.io.InputStream): Boolean {
        return try {
            val loaded = ObjectInputStream(input.buffered()).use { it.readObject() as LocalDb }
            db = loaded
            persist(); emitAll()
            true
        } catch (e: Exception) {
            false
        }
    }
}
