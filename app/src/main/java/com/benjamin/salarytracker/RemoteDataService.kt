package com.benjamin.salarytracker

import kotlinx.coroutines.flow.Flow

/**
 * Implémentation distante de [DataService] : délègue intégralement à [FirestoreService]
 * (Firebase Realtime Database). Utilisée pour les comptes authentifiés.
 */
class RemoteDataService(
    val fs: FirestoreService = FirestoreService()
) : DataService {
    override fun setUserId(newUserId: String) = fs.setUserId(newUserId)
    override suspend fun transferOldDataIfNeeded(newUserId: String) = fs.transferOldDataIfNeeded(newUserId)
    override suspend fun deleteAllUserData() = fs.deleteAllUserData()

    override fun connectionStatus(): Flow<ConnectionStatus> = fs.connectionStatus()
    override fun getCompanies(): Flow<List<Company>> = fs.getCompanies()
    override fun getJobs(): Flow<List<Job>> = fs.getJobs()
    override fun getEntries(jobId: String): Flow<List<DayEntry>> = fs.getEntries(jobId)
    override fun getTemplates(jobId: String): Flow<List<DayTemplate>> = fs.getTemplates(jobId)
    override fun getAutoRules(jobId: String): Flow<List<AutoEntryRule>> = fs.getAutoRules(jobId)
    override fun getPayslips(jobId: String): Flow<List<Payslip>> = fs.getPayslips(jobId)
    override fun getAppTheme(): Flow<String> = fs.getAppTheme()
    override fun getAppLanguage(): Flow<String> = fs.getAppLanguage()

    override suspend fun addCompany(company: Company) = fs.addCompany(company)
    override suspend fun deleteCompany(companyId: String) = fs.deleteCompany(companyId)

    override suspend fun addJob(job: Job) = fs.addJob(job)
    override suspend fun updateJob(job: Job) = fs.updateJob(job)
    override suspend fun deleteJob(jobId: String) = fs.deleteJob(jobId)
    override suspend fun updateJobFields(jobId: String, fields: Map<String, Any?>) = fs.updateJobFields(jobId, fields)
    override suspend fun forceRefresh(jobId: String?) = fs.forceRefresh(jobId)
    override suspend fun setMainJob(jobId: String) = fs.setMainJob(jobId)

    override suspend fun addDayEntry(jobId: String, entry: DayEntry) = fs.addDayEntry(jobId, entry)
    override suspend fun addDayEntries(jobId: String, entries: List<DayEntry>) = fs.addDayEntries(jobId, entries)
    override suspend fun deleteDayEntry(jobId: String, entryId: String) = fs.deleteDayEntry(jobId, entryId)
    override suspend fun deleteAllEntries(jobId: String) = fs.deleteAllEntries(jobId)

    override suspend fun addTemplate(jobId: String, template: DayTemplate) = fs.addTemplate(jobId, template)
    override suspend fun deleteTemplate(jobId: String, templateId: String) = fs.deleteTemplate(jobId, templateId)

    override suspend fun addAutoRule(jobId: String, rule: AutoEntryRule) = fs.addAutoRule(jobId, rule)
    override suspend fun deleteAutoRule(jobId: String, ruleId: String) = fs.deleteAutoRule(jobId, ruleId)

    override suspend fun addPayslip(jobId: String, payslip: Payslip) = fs.addPayslip(jobId, payslip)
    override suspend fun deletePayslip(jobId: String, payslipId: String) = fs.deletePayslip(jobId, payslipId)

    override suspend fun updateAppTheme(theme: String) = fs.updateAppTheme(theme)
    override suspend fun updateAppLanguage(lang: String) = fs.updateAppLanguage(lang)
    override suspend fun saveGeminiApiKey(key: String) = fs.saveGeminiApiKey(key)
    override suspend fun getGeminiApiKey(): String? = fs.getGeminiApiKey()
}
