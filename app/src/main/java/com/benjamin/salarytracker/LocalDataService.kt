package com.benjamin.salarytracker

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        var appLanguage: String = "system",
        var geminiKey: String? = null
    ) : java.io.Serializable

    private var uid: String = "local"
    private var db = LocalDb()

    private val companiesFlow = MutableStateFlow<List<Company>>(emptyList())
    private val jobsFlow = MutableStateFlow<List<Job>>(emptyList())
    private val themeFlow = MutableStateFlow("purple")
    private val languageFlow = MutableStateFlow("system")
    private val entriesFlows = mutableMapOf<String, MutableStateFlow<List<DayEntry>>>()
    private val templatesFlows = mutableMapOf<String, MutableStateFlow<List<DayTemplate>>>()
    private val autoRulesFlows = mutableMapOf<String, MutableStateFlow<List<AutoEntryRule>>>()
    private val payslipsFlows = mutableMapOf<String, MutableStateFlow<List<Payslip>>>()

    private fun file(): File = File(context.filesDir, "local_data_$uid.dat")

    private fun serializeDb(database: LocalDb): org.json.JSONObject {
        val json = org.json.JSONObject()
        
        json.put("appTheme", database.appTheme)
        json.put("appLanguage", database.appLanguage)
        json.put("geminiKey", database.geminiKey ?: org.json.JSONObject.NULL)
        
        val companiesArray = org.json.JSONArray()
        database.companies.forEach { company ->
            val compObj = org.json.JSONObject()
            compObj.put("id", company.id)
            compObj.put("name", company.name)
            companiesArray.put(compObj)
        }
        json.put("companies", companiesArray)
        
        val jobsArray = org.json.JSONArray()
        database.jobs.forEach { job ->
            val jobObj = org.json.JSONObject()
            jobObj.put("id", job.id)
            jobObj.put("name", job.name)
            jobObj.put("companyName", job.companyName)
            jobObj.put("companyId", job.companyId ?: org.json.JSONObject.NULL)
            jobObj.put("contractType", job.contractType.name)
            jobObj.put("hourlyRateBrut", job.hourlyRateBrut)
            jobObj.put("weeklyContractHours", job.weeklyContractHours)
            jobObj.put("includedOvertimeHours", job.includedOvertimeHours)
            jobObj.put("includedOvertimeRatePercent", job.includedOvertimeRatePercent)
            jobObj.put("annualOvertimeQuota", job.annualOvertimeQuota)
            jobObj.put("overtimeMode", job.overtimeMode.name)
            jobObj.put("livretThreshold", job.livretThreshold)
            jobObj.put("soldeLivretHeures", job.soldeLivretHeures)
            jobObj.put("targetMonthlySalary", job.targetMonthlySalary)
            jobObj.put("startDate", job.startDate?.toString() ?: org.json.JSONObject.NULL)
            jobObj.put("endDate", job.endDate?.toString() ?: org.json.JSONObject.NULL)
            jobObj.put("isMainJob", job.isMainJob)
            jobObj.put("isArchived", job.isArchived)
            jobsArray.put(jobObj)
        }
        json.put("jobs", jobsArray)
        
        val entriesObj = org.json.JSONObject()
        database.entries.forEach { (jobId, list) ->
            val jobEntriesArray = org.json.JSONArray()
            list.forEach { entry ->
                val entryObj = org.json.JSONObject()
                entryObj.put("id", entry.id)
                entryObj.put("jobId", entry.jobId)
                entryObj.put("date", entry.date.toString())
                entryObj.put("startTime", entry.startTime.toString())
                entryObj.put("endTime", entry.endTime.toString())
                entryObj.put("pauseMinutes", entry.pauseMinutes)
                entryObj.put("isLeave", entry.isLeave)
                jobEntriesArray.put(entryObj)
            }
            entriesObj.put(jobId, jobEntriesArray)
        }
        json.put("entries", entriesObj)
        
        val templatesObj = org.json.JSONObject()
        database.templates.forEach { (jobId, list) ->
            val jobTemplatesArray = org.json.JSONArray()
            list.forEach { template ->
                val templateObj = org.json.JSONObject()
                templateObj.put("id", template.id)
                templateObj.put("name", template.name)
                templateObj.put("startTime", template.startTime.toString())
                templateObj.put("endTime", template.endTime.toString())
                
                val pausesArray = org.json.JSONArray()
                template.pauseBlocks.forEach { pausesArray.put(it.durationMinutes) }
                templateObj.put("pauseMinutesList", pausesArray)
                
                jobTemplatesArray.put(templateObj)
            }
            templatesObj.put(jobId, jobTemplatesArray)
        }
        json.put("templates", templatesObj)
        
        val autoRulesObj = org.json.JSONObject()
        database.autoRules.forEach { (jobId, list) ->
            val jobRulesArray = org.json.JSONArray()
            list.forEach { rule ->
                val ruleObj = org.json.JSONObject()
                ruleObj.put("id", rule.id)
                ruleObj.put("templateId", rule.templateId ?: org.json.JSONObject.NULL)
                ruleObj.put("templateName", rule.templateName)
                ruleObj.put("customStartTime", rule.customStartTime?.toString() ?: org.json.JSONObject.NULL)
                ruleObj.put("customEndTime", rule.customEndTime?.toString() ?: org.json.JSONObject.NULL)
                ruleObj.put("customPauseMinutes", rule.customPauseMinutes)
                ruleObj.put("active", rule.active)
                ruleObj.put("mode", rule.mode.name)
                ruleObj.put("startDate", rule.startDate.toString())
                ruleObj.put("endDate", rule.endDate?.toString() ?: org.json.JSONObject.NULL)
                
                val weekdaysArray = org.json.JSONArray()
                rule.weekdays.forEach { weekdaysArray.put(it) }
                ruleObj.put("weekdays", weekdaysArray)
                
                jobRulesArray.put(ruleObj)
            }
            autoRulesObj.put(jobId, jobRulesArray)
        }
        json.put("autoRules", autoRulesObj)
        
        val payslipsObj = org.json.JSONObject()
        database.payslips.forEach { (jobId, list) ->
            val jobPayslipsArray = org.json.JSONArray()
            list.forEach { payslip ->
                val payslipObj = org.json.JSONObject()
                payslipObj.put("id", payslip.id)
                payslipObj.put("month", payslip.month)
                payslipObj.put("year", payslip.year)
                payslipObj.put("brut", payslip.brut)
                payslipObj.put("net", payslip.net)
                payslipObj.put("netImposable", payslip.netImposable)
                payslipObj.put("heuresPayees", payslip.heuresPayees)
                payslipObj.put("cotisations", payslip.cotisations)
                payslipObj.put("uploadedAt", payslip.uploadedAt)
                jobPayslipsArray.put(payslipObj)
            }
            payslipsObj.put(jobId, jobPayslipsArray)
        }
        json.put("payslips", payslipsObj)
        
        return json
    }

    private fun deserializeDb(json: org.json.JSONObject): LocalDb {
        val loadedDb = LocalDb()
        
        loadedDb.appTheme = json.optString("appTheme", "purple")
        loadedDb.appLanguage = json.optString("appLanguage", "system")
        loadedDb.geminiKey = if (json.isNull("geminiKey")) null else json.optString("geminiKey")
        
        val companiesArray = json.optJSONArray("companies")
        if (companiesArray != null) {
            for (i in 0 until companiesArray.length()) {
                val obj = companiesArray.getJSONObject(i)
                loadedDb.companies.add(Company(
                    id = obj.getString("id"),
                    name = obj.getString("name")
                ))
            }
        }
        
        val jobsArray = json.optJSONArray("jobs")
        if (jobsArray != null) {
            for (i in 0 until jobsArray.length()) {
                val obj = jobsArray.getJSONObject(i)
                val contractTypeStr = obj.optString("contractType", "CDI")
                val contractType = try { ContractType.valueOf(contractTypeStr) } catch (_: Exception) { ContractType.CDI }
                val overtimeModeStr = obj.optString("overtimeMode", "PAYEE")
                val overtimeMode = try { OvertimeMode.valueOf(overtimeModeStr) } catch (_: Exception) { OvertimeMode.PAYEE }
                
                loadedDb.jobs.add(Job(
                    id = obj.getString("id"),
                    name = obj.optString("name", ""),
                    companyName = obj.optString("companyName", ""),
                    companyId = if (obj.isNull("companyId")) null else obj.optString("companyId"),
                    contractType = contractType,
                    hourlyRateBrut = obj.optDouble("hourlyRateBrut", 0.0),
                    weeklyContractHours = obj.optDouble("weeklyContractHours", 35.0),
                    includedOvertimeHours = obj.optDouble("includedOvertimeHours", 0.0),
                    includedOvertimeRatePercent = obj.optDouble("includedOvertimeRatePercent", 25.0),
                    annualOvertimeQuota = obj.optInt("annualOvertimeQuota", 220),
                    overtimeMode = overtimeMode,
                    livretThreshold = obj.optDouble("livretThreshold", 43.0),
                    soldeLivretHeures = obj.optDouble("soldeLivretHeures", 0.0),
                    targetMonthlySalary = obj.optDouble("targetMonthlySalary", 3000.0),
                    startDate = if (obj.isNull("startDate")) null else safeParseDate(obj.getString("startDate")),
                    endDate = if (obj.isNull("endDate")) null else safeParseDate(obj.getString("endDate")),
                    isMainJob = obj.optBoolean("isMainJob", false),
                    isArchived = obj.optBoolean("isArchived", false)
                ))
            }
        }
        
        val entriesObj = json.optJSONObject("entries")
        if (entriesObj != null) {
            val keys = entriesObj.keys()
            while (keys.hasNext()) {
                val jobId = keys.next()
                val arr = entriesObj.getJSONArray(jobId)
                val list = mutableListOf<DayEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(DayEntry(
                        id = obj.getString("id"),
                        jobId = obj.optString("jobId", jobId),
                        date = safeParseDate(obj.getString("date")) ?: LocalDate.now(),
                        startTime = safeParseTime(obj.getString("startTime")) ?: LocalTime.of(8, 0),
                        endTime = safeParseTime(obj.getString("endTime")) ?: LocalTime.of(17, 0),
                        pauseMinutes = obj.optLong("pauseMinutes", 0L),
                        isLeave = obj.optBoolean("isLeave", false)
                    ))
                }
                loadedDb.entries[jobId] = list
            }
        }
        
        val templatesObj = json.optJSONObject("templates")
        if (templatesObj != null) {
            val keys = templatesObj.keys()
            while (keys.hasNext()) {
                val jobId = keys.next()
                val arr = templatesObj.getJSONArray(jobId)
                val list = mutableListOf<DayTemplate>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pauses = mutableListOf<PauseBlock>()
                    val pausesArr = obj.optJSONArray("pauseMinutesList")
                    if (pausesArr != null) {
                        for (j in 0 until pausesArr.length()) {
                            pauses.add(PauseBlock(pausesArr.getLong(j)))
                        }
                    }
                    list.add(DayTemplate(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        startTime = safeParseTime(obj.getString("startTime")) ?: LocalTime.of(8, 0),
                        endTime = safeParseTime(obj.getString("endTime")) ?: LocalTime.of(17, 0),
                        pauseBlocks = pauses
                    ))
                }
                loadedDb.templates[jobId] = list
            }
        }
        
        val autoRulesObj = json.optJSONObject("autoRules")
        if (autoRulesObj != null) {
            val keys = autoRulesObj.keys()
            while (keys.hasNext()) {
                val jobId = keys.next()
                val arr = autoRulesObj.getJSONArray(jobId)
                val list = mutableListOf<AutoEntryRule>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val weekdaysSet = mutableSetOf<Int>()
                    val weekdaysArr = obj.optJSONArray("weekdays")
                    if (weekdaysArr != null) {
                        for (j in 0 until weekdaysArr.length()) {
                            weekdaysSet.add(weekdaysArr.getInt(j))
                        }
                    }
                    val modeStr = obj.optString("mode", "UNTIL_DISABLED")
                    val mode = try { AutoEntryMode.valueOf(modeStr) } catch (_: Exception) { AutoEntryMode.UNTIL_DISABLED }
                    
                    list.add(AutoEntryRule(
                        id = obj.getString("id"),
                        templateId = if (obj.isNull("templateId")) null else obj.optString("templateId"),
                        templateName = obj.optString("templateName", ""),
                        customStartTime = if (obj.isNull("customStartTime")) null else safeParseTime(obj.getString("customStartTime")),
                        customEndTime = if (obj.isNull("customEndTime")) null else safeParseTime(obj.getString("customEndTime")),
                        customPauseMinutes = obj.optLong("customPauseMinutes", 0L),
                        active = obj.optBoolean("active", true),
                        mode = mode,
                        startDate = safeParseDate(obj.getString("startDate")) ?: LocalDate.now(),
                        endDate = if (obj.isNull("endDate")) null else safeParseDate(obj.getString("endDate")),
                        weekdays = weekdaysSet.ifEmpty { setOf(1, 2, 3, 4, 5) }
                    ))
                }
                loadedDb.autoRules[jobId] = list
            }
        }
        
        val payslipsObj = json.optJSONObject("payslips")
        if (payslipsObj != null) {
            val keys = payslipsObj.keys()
            while (keys.hasNext()) {
                val jobId = keys.next()
                val arr = payslipsObj.getJSONArray(jobId)
                val list = mutableListOf<Payslip>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Payslip(
                        id = obj.getString("id"),
                        month = obj.getInt("month"),
                        year = obj.getInt("year"),
                        brut = obj.optDouble("brut", 0.0),
                        net = obj.optDouble("net", 0.0),
                        netImposable = obj.optDouble("netImposable", 0.0),
                        heuresPayees = obj.optDouble("heuresPayees", 0.0),
                        cotisations = obj.optDouble("cotisations", 0.0),
                        uploadedAt = obj.optLong("uploadedAt", 0L)
                    ))
                }
                loadedDb.payslips[jobId] = list
            }
        }
        
        return loadedDb
    }

    private fun loadBinaryFallback(): LocalDb {
        return try {
            if (file().exists()) {
                ObjectInputStream(file().inputStream().buffered()).use { it.readObject() as LocalDb }
            } else {
                LocalDb()
            }
        } catch (_: Exception) {
            LocalDb()
        }
    }

    private fun persist() {
        try {
            val json = serializeDb(db)
            file().writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("LocalDataService", "Error persisting data", e)
        }
    }

    /** Force l'écriture immédiate des données sur le disque (sauvegarde locale manuelle). */
    fun forcePersist() = persist()

    private fun load() {
        db = try {
            if (file().exists()) {
                val text = file().readText()
                if (text.trim().startsWith("{")) {
                    deserializeDb(org.json.JSONObject(text))
                } else {
                    loadBinaryFallback()
                }
            } else {
                LocalDb()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDataService", "Error loading JSON local data, trying binary fallback", e)
            loadBinaryFallback()
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
        languageFlow.value = db.appLanguage
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
    override fun getAppLanguage(): Flow<String> = languageFlow

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
                "name" -> j.copy(name = value as? String ?: j.name)
                "companyName" -> j.copy(companyName = value as? String ?: j.companyName)
                "companyId" -> j.copy(companyId = value as? String ?: j.companyId)
                "contractType" -> j.copy(contractType = (value as? String)?.let { try { ContractType.valueOf(it) } catch(_: Exception) { null } } ?: j.contractType)
                "hourlyRateBrut" -> j.copy(hourlyRateBrut = (value as? Number)?.toDouble() ?: j.hourlyRateBrut)
                "weeklyContractHours" -> j.copy(weeklyContractHours = (value as? Number)?.toDouble() ?: j.weeklyContractHours)
                "includedOvertimeHours" -> j.copy(includedOvertimeHours = (value as? Number)?.toDouble() ?: j.includedOvertimeHours)
                "includedOvertimeRatePercent" -> j.copy(includedOvertimeRatePercent = (value as? Number)?.toDouble() ?: j.includedOvertimeRatePercent)
                "annualOvertimeQuota" -> j.copy(annualOvertimeQuota = (value as? Number)?.toInt() ?: j.annualOvertimeQuota)
                "overtimeMode" -> j.copy(overtimeMode = (value as? String)?.let { try { OvertimeMode.valueOf(it) } catch(_: Exception) { null } } ?: j.overtimeMode)
                "livretThreshold" -> j.copy(livretThreshold = (value as? Number)?.toDouble() ?: j.livretThreshold)
                "soldeLivretHeures" -> j.copy(soldeLivretHeures = (value as? Number)?.toDouble() ?: j.soldeLivretHeures)
                "targetMonthlySalary" -> j.copy(targetMonthlySalary = (value as? Number)?.toDouble() ?: j.targetMonthlySalary)
                "startDate" -> j.copy(startDate = (value as? String)?.let { try { LocalDate.parse(it) } catch(_: Exception) { null } } ?: j.startDate)
                "endDate" -> j.copy(endDate = (value as? String)?.let { try { LocalDate.parse(it) } catch(_: Exception) { null } } ?: j.endDate)
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

    override suspend fun updateAppLanguage(lang: String) {
        db.appLanguage = lang
        persist(); languageFlow.value = lang
    }

    override suspend fun saveGeminiApiKey(key: String) {
        db.geminiKey = key
        persist()
    }

    override suspend fun getGeminiApiKey(): String? = db.geminiKey

    // ─── Sauvegarde / restauration (compte local) ─────────────────────────────

    /** Écrit toutes les données locales courantes dans [out] (format JSON). */
    fun exportTo(out: java.io.OutputStream) {
        try {
            val json = serializeDb(db)
            out.write(json.toString().toByteArray(Charsets.UTF_8))
        } catch (_: Exception) {}
    }

    /** Restaure les données depuis [input]. Renvoie true si réussi. */
    fun importFrom(input: java.io.InputStream): Boolean {
        return try {
            val bytes = input.readBytes()
            val text = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
            if (text.trim().startsWith("{")) {
                val json = org.json.JSONObject(text)
                db = deserializeDb(json)
            } else {
                // Fallback binaire pour la rétrocompatibilité
                val bis = java.io.ByteArrayInputStream(bytes)
                db = ObjectInputStream(bis.buffered()).use { it.readObject() as LocalDb }
            }
            persist(); emitAll()
            true
        } catch (e: Exception) {
            false
        }
    }
}

private fun safeParseDate(str: String?): LocalDate? {
    if (str.isNullOrBlank() || str == "null") return null
    try {
        return LocalDate.parse(str)
    } catch (_: Exception) {}
    try {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        return LocalDate.parse(str, formatter)
    } catch (_: Exception) {}
    try {
        val formatter = DateTimeFormatter.ofPattern("d/M/yyyy")
        return LocalDate.parse(str, formatter)
    } catch (_: Exception) {}
    return null
}

private fun safeParseTime(str: String?): LocalTime? {
    if (str.isNullOrBlank() || str == "null") return null
    try {
        return LocalTime.parse(str)
    } catch (_: Exception) {}
    try {
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        return LocalTime.parse(str, formatter)
    } catch (_: Exception) {}
    try {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return LocalTime.parse(str, formatter)
    } catch (_: Exception) {}
    try {
        val formatter = DateTimeFormatter.ofPattern("H:m")
        return LocalTime.parse(str, formatter)
    } catch (_: Exception) {}
    return null
}
