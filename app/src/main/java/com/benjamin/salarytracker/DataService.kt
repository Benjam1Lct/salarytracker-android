package com.benjamin.salarytracker

import kotlinx.coroutines.flow.Flow

/**
 * Couche de persistance abstraite. Deux implémentations :
 *  - [RemoteDataService] : Firebase Realtime Database (comptes Google/e-mail/téléphone).
 *  - [LocalDataService]  : stockage sur l'appareil (comptes locaux, hors-ligne).
 *
 * Le [SalaryViewModel] route vers l'une ou l'autre selon le type de session.
 */
interface DataService {
    fun setUserId(newUserId: String)
    suspend fun transferOldDataIfNeeded(newUserId: String)
    suspend fun deleteAllUserData()

    fun connectionStatus(): Flow<ConnectionStatus>
    fun getCompanies(): Flow<List<Company>>
    fun getJobs(): Flow<List<Job>>
    fun getEntries(jobId: String): Flow<List<DayEntry>>
    fun getTemplates(jobId: String): Flow<List<DayTemplate>>
    fun getAutoRules(jobId: String): Flow<List<AutoEntryRule>>
    fun getPayslips(jobId: String): Flow<List<Payslip>>
    fun getAppTheme(): Flow<String>

    suspend fun addCompany(company: Company)
    suspend fun deleteCompany(companyId: String)

    suspend fun addJob(job: Job)
    suspend fun updateJob(job: Job)
    suspend fun deleteJob(jobId: String)
    suspend fun updateJobFields(jobId: String, fields: Map<String, Any?>)
    suspend fun forceRefresh(jobId: String?)
    suspend fun setMainJob(jobId: String)

    suspend fun addDayEntry(jobId: String, entry: DayEntry)
    suspend fun addDayEntries(jobId: String, entries: List<DayEntry>)
    suspend fun deleteDayEntry(jobId: String, entryId: String)
    suspend fun deleteAllEntries(jobId: String)

    suspend fun addTemplate(jobId: String, template: DayTemplate)
    suspend fun deleteTemplate(jobId: String, templateId: String)

    suspend fun addAutoRule(jobId: String, rule: AutoEntryRule)
    suspend fun deleteAutoRule(jobId: String, ruleId: String)

    suspend fun addPayslip(jobId: String, payslip: Payslip)
    suspend fun deletePayslip(jobId: String, payslipId: String)

    suspend fun updateAppTheme(theme: String)
    suspend fun saveGeminiApiKey(key: String)
    suspend fun getGeminiApiKey(): String?
}
