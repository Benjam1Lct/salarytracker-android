package com.benjamin.salarytracker

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File

/**
 * Service de données hybride / offline-first.
 * Utilise [LocalDataService] comme source de vérité locale pour les flux de l'interface utilisateur (Ui Flow),
 * et synchronise les données en tâche de fond avec Firebase Realtime Database ([RemoteDataService])
 * en utilisant une stratégie de merge basée sur des timestamps et un suivi des suppressions (tombstones).
 */
class SyncDataService(
    private val local: LocalDataService,
    private val remote: RemoteDataService
) : DataService {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    
    private var activeListener: ValueEventListener? = null
    private var activeRef: com.google.firebase.database.DatabaseReference? = null
    private var metadataManager: SyncMetadataManager? = null

    private class SyncMetadataManager(private val context: Context, private val userId: String) {
        private val file = File(context.filesDir, "sync_metadata_$userId.json")
        val updatedAt = mutableMapOf<String, Long>()
        val deletedAt = mutableMapOf<String, Long>()

        init {
            load()
        }

        private fun load() {
            if (!file.exists()) return
            try {
                val json = JSONObject(file.readText())
                val updatedJson = json.optJSONObject("updatedAt")
                if (updatedJson != null) {
                    val keys = updatedJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        updatedAt[key] = updatedJson.optLong(key, 0L)
                    }
                }
                val deletedJson = json.optJSONObject("deletedAt")
                if (deletedJson != null) {
                    val keys = deletedJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        deletedAt[key] = deletedJson.optLong(key, 0L)
                    }
                }
            } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }

        fun save() {
            try {
                val json = JSONObject()
                val updatedJson = JSONObject()
                updatedAt.forEach { (k, v) -> updatedJson.put(k, v) }
                val deletedJson = JSONObject()
                deletedAt.forEach { (k, v) -> deletedJson.put(k, v) }
                json.put("updatedAt", updatedJson)
                json.put("deletedAt", deletedJson)
                file.writeText(json.toString())
            } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }

        fun getUpdatedAt(id: String): Long = updatedAt[id] ?: 0L

        fun setUpdatedAt(id: String, timestamp: Long) {
            updatedAt[id] = timestamp
            deletedAt.remove(id)
            save()
        }

        fun getDeletedAt(id: String): Long = deletedAt[id] ?: 0L

        fun setDeletedAt(id: String, timestamp: Long) {
            deletedAt[id] = timestamp
            updatedAt.remove(id)
            save()
        }

        fun clear() {
            try { file.delete() } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
            updatedAt.clear()
            deletedAt.clear()
        }
    }

    override fun setUserId(newUserId: String) {
        local.setUserId(newUserId)
        remote.setUserId(newUserId)

        // Désenregistre l'ancien listener s'il existe
        activeListener?.let { activeRef?.removeEventListener(it) }

        val metadata = SyncMetadataManager(SalaryApp.instance, newUserId)
        metadataManager = metadata

        val ref = remote.fs.userRef
        activeRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                syncScope.launch {
                    try {
                        performSync(snapshot)
                    } catch (e: Exception) {
                        android.util.Log.e("SyncDataService", "Erreur lors de la synchronisation", e)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("SyncDataService", "Listener Firebase annulé", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        activeListener = listener
    }

    private suspend fun performSync(snapshot: DataSnapshot) {
        val metadata = metadataManager ?: return
        syncMutex.withLock {
            // --- 1. Lecture et parsing des données distantes ---
            val remoteDeleted = snapshot.child("deleted").children.associate {
                it.key.toString() to it.lng()
            }.toMutableMap()

            val remoteCompanies = snapshot.child("companies").children.associate { child ->
                val id = child.key.toString()
                val name = child.child("name").getValue(String::class.java) ?: ""
                val updatedAt = child.lng("updatedAt")
                id to (Company(id, name) to updatedAt)
            }

            val remoteJobs = mutableMapOf<String, Pair<Job, Long>>()
            val remoteEntries = mutableMapOf<String, Pair<DayEntry, Long>>()
            val remoteTemplates = mutableMapOf<String, Triple<DayTemplate, String, Long>>()
            val remoteRules = mutableMapOf<String, Triple<AutoEntryRule, String, Long>>()
            val remotePayslips = mutableMapOf<String, Triple<Payslip, String, Long>>()

            snapshot.child("jobs").children.forEach { jobChild ->
                val jobId = jobChild.key.toString()
                val job = jobChild.toJob()
                val jobUpdatedAt = jobChild.lng("updatedAt")
                if (job != null) {
                    remoteJobs[jobId] = job to jobUpdatedAt
                }

                jobChild.child("days").children.forEach { entryChild ->
                    val entryId = entryChild.key.toString()
                    val entry = entryChild.toDayEntry(jobId)
                    val entryUpdatedAt = entryChild.lng("updatedAt")
                    if (entry != null) {
                        remoteEntries[entryId] = entry to entryUpdatedAt
                    }
                }

                jobChild.child("templates").children.forEach { templateChild ->
                    val templateId = templateChild.key.toString()
                    val template = templateChild.toDayTemplate()
                    val templateUpdatedAt = templateChild.lng("updatedAt")
                    if (template != null) {
                        remoteTemplates[templateId] = Triple(template, jobId, templateUpdatedAt)
                    }
                }

                jobChild.child("autoRules").children.forEach { ruleChild ->
                    val ruleId = ruleChild.key.toString()
                    val rule = ruleChild.toAutoEntryRule()
                    val ruleUpdatedAt = ruleChild.lng("updatedAt")
                    if (rule != null) {
                        remoteRules[ruleId] = Triple(rule, jobId, ruleUpdatedAt)
                    }
                }

                jobChild.child("payslips").children.forEach { payslipChild ->
                    val payslipId = payslipChild.key.toString()
                    val payslip = payslipChild.toPayslip()
                    val payslipUpdatedAt = payslipChild.lng("updatedAt")
                    if (payslip != null) {
                        remotePayslips[payslipId] = Triple(payslip, jobId, payslipUpdatedAt)
                    }
                }
            }

            // --- 2. Lecture des données locales ---
            val localCompanies = local.getCompanies().first()
            val localJobs = local.getJobs().first().toMutableList()

            val localEntries = mutableMapOf<String, DayEntry>()
            val localTemplates = mutableMapOf<String, Pair<DayTemplate, String>>() // id -> (template, jobId)
            val localRules = mutableMapOf<String, Pair<AutoEntryRule, String>>() // id -> (rule, jobId)
            val localPayslips = mutableMapOf<String, Pair<Payslip, String>>() // id -> (payslip, jobId)

            localJobs.forEach { job ->
                local.getEntries(job.id).first().forEach { localEntries[it.id] = it }
                local.getTemplates(job.id).first().forEach { localTemplates[it.id] = it to job.id }
                local.getAutoRules(job.id).first().forEach { localRules[it.id] = it to job.id }
                local.getPayslips(job.id).first().forEach { localPayslips[it.id] = it to job.id }
            }

            // --- 3. Synchronisation des suppressions (Tombstones) ---
            val mergedDeleted = metadata.deletedAt.toMutableMap()
            remoteDeleted.forEach { (id, tRemote) ->
                val tLocal = mergedDeleted[id] ?: 0L
                if (tRemote > tLocal) {
                    mergedDeleted[id] = tRemote
                    metadata.setDeletedAt(id, tRemote)
                } else if (tLocal > tRemote) {
                    remote.fs.userRef.child("deleted").child(id).setValue(tLocal)
                }
            }
            metadata.deletedAt.forEach { (id, tLocal) ->
                if (!remoteDeleted.containsKey(id)) {
                    remote.fs.userRef.child("deleted").child(id).setValue(tLocal)
                }
            }

            // --- 3.5 Fusion et suppression automatique des doublons d'entreprises ---
            val allCompaniesCombinedList = (localCompanies + remoteCompanies.values.map { it.first })
                .distinctBy { it.id }
            val groupedByName = allCompaniesCombinedList.groupBy { it.name.trim().lowercase() }
            
            val companiesToMerge = mutableMapOf<String, String>() // duplicateId -> mainId
            groupedByName.forEach { (_, group) ->
                if (group.size > 1) {
                    val mainCompany = group.first()
                    group.drop(1).forEach { dup ->
                        companiesToMerge[dup.id] = mainCompany.id
                    }
                }
            }

            if (companiesToMerge.isNotEmpty()) {
                val now = System.currentTimeMillis()
                companiesToMerge.forEach { (dupId, _) ->
                    mergedDeleted[dupId] = now
                    metadata.setDeletedAt(dupId, now)
                }

                // Mettre à jour les contrats (Jobs) locaux qui font référence à un doublon
                for (idx in localJobs.indices) {
                    val job = localJobs[idx]
                    if (job.companyId != null && companiesToMerge.containsKey(job.companyId)) {
                        val newCompanyId = companiesToMerge[job.companyId]
                        val updatedJob = job.copy(companyId = newCompanyId)
                        local.addJob(updatedJob)
                        metadata.setUpdatedAt(job.id, now)
                        localJobs[idx] = updatedJob
                    }
                }

                // Mettre à jour également les contrats (Jobs) distants dans notre map de travail
                remoteJobs.forEach { (jobId, pair) ->
                    val job = pair.first
                    val tJob = pair.second
                    if (job.companyId != null && companiesToMerge.containsKey(job.companyId)) {
                        val newCompanyId = companiesToMerge[job.companyId]
                        remoteJobs[jobId] = job.copy(companyId = newCompanyId) to tJob
                    }
                }
            }

            // --- 4. Synchronisation des Entreprises (Companies) ---
            val allCompanyIds = (localCompanies.map { it.id } + remoteCompanies.keys).distinct()
            for (id in allCompanyIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localItem = localCompanies.find { it.id == id }
                val remotePair = remoteCompanies[id]
                val remoteItem = remotePair?.first
                val tRemote = remotePair?.second ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && tDelete >= tLocal) {
                        local.deleteCompany(id)
                    }
                    if (remoteItem != null && tDelete >= tRemote) {
                        remote.fs.deleteCompany(id, tDelete)
                    }
                } else {
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addCompany(localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addCompany(remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addCompany(localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addCompany(remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 5. Synchronisation des Contrats (Jobs) ---
            val allJobIds = (localJobs.map { it.id } + remoteJobs.keys).distinct()
            for (id in allJobIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localItem = localJobs.find { it.id == id }
                val remotePair = remoteJobs[id]
                val remoteItem = remotePair?.first
                val tRemote = remotePair?.second ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && tDelete >= tLocal) {
                        local.deleteJob(id)
                    }
                    if (remoteItem != null && tDelete >= tRemote) {
                        remote.fs.deleteJob(id, tDelete)
                    }
                } else {
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addJob(localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addJob(remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addJob(localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addJob(remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 6. Synchronisation des Saisies (DayEntries) ---
            val allEntryIds = (localEntries.keys + remoteEntries.keys).distinct()
            for (id in allEntryIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localItem = localEntries[id]
                val remotePair = remoteEntries[id]
                val remoteItem = remotePair?.first
                val tRemote = remotePair?.second ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && tDelete >= tLocal) {
                        local.deleteDayEntry(localItem.jobId, id)
                    }
                    if (remoteItem != null && tDelete >= tRemote) {
                        remote.fs.deleteDayEntry(remoteItem.jobId, id, tDelete)
                    }
                } else {
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addDayEntry(localItem.jobId, localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addDayEntry(remoteItem.jobId, remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addDayEntry(localItem.jobId, localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addDayEntry(remoteItem.jobId, remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 7. Synchronisation des Templates (DayTemplates) ---
            val allTemplateIds = (localTemplates.keys + remoteTemplates.keys).distinct()
            for (id in allTemplateIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localPair = localTemplates[id]
                val localItem = localPair?.first
                val localJobId = localPair?.second
                val remoteTriple = remoteTemplates[id]
                val remoteItem = remoteTriple?.first
                val remoteJobId = remoteTriple?.second
                val tRemote = remoteTriple?.third ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && localJobId != null && tDelete >= tLocal) {
                        local.deleteTemplate(localJobId, id)
                    }
                    if (remoteItem != null && remoteJobId != null && tDelete >= tRemote) {
                        remote.fs.deleteTemplate(remoteJobId, id, tDelete)
                    }
                } else {
                    val jobId = localJobId ?: remoteJobId!!
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addTemplate(jobId, localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addTemplate(jobId, remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addTemplate(jobId, localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addTemplate(jobId, remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 8. Synchronisation des Règles Automatiques (AutoEntryRules) ---
            val allRuleIds = (localRules.keys + remoteRules.keys).distinct()
            for (id in allRuleIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localPair = localRules[id]
                val localItem = localPair?.first
                val localJobId = localPair?.second
                val remoteTriple = remoteRules[id]
                val remoteItem = remoteTriple?.first
                val remoteJobId = remoteTriple?.second
                val tRemote = remoteTriple?.third ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && localJobId != null && tDelete >= tLocal) {
                        local.deleteAutoRule(localJobId, id)
                    }
                    if (remoteItem != null && remoteJobId != null && tDelete >= tRemote) {
                        remote.fs.deleteAutoRule(remoteJobId, id, tDelete)
                    }
                } else {
                    val jobId = localJobId ?: remoteJobId!!
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addAutoRule(jobId, localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addAutoRule(jobId, remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addAutoRule(jobId, localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addAutoRule(jobId, remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 9. Synchronisation des Bulletins (Payslips) ---
            val allPayslipIds = (localPayslips.keys + remotePayslips.keys).distinct()
            for (id in allPayslipIds) {
                val tDelete = mergedDeleted[id] ?: 0L
                val localPair = localPayslips[id]
                val localItem = localPair?.first
                val localJobId = localPair?.second
                val remoteTriple = remotePayslips[id]
                val remoteItem = remoteTriple?.first
                val remoteJobId = remoteTriple?.second
                val tRemote = remoteTriple?.third ?: 0L
                val tLocal = metadata.getUpdatedAt(id)

                // Le tombstone n'est effectif que s'il est plus récent que les deux côtés.
                // Si l'élément a été recréé après (tRemote/tLocal > tDelete), on ignore le
                // tombstone et on retombe sur le merge normal (sinon l'élément resterait en
                // BD sans jamais être récupéré en local → invisible dans l'app).
                if (tDelete > 0L && tDelete >= tRemote && tDelete >= tLocal) {
                    if (localItem != null && localJobId != null && tDelete >= tLocal) {
                        local.deletePayslip(localJobId, id)
                    }
                    if (remoteItem != null && remoteJobId != null && tDelete >= tRemote) {
                        remote.fs.deletePayslip(remoteJobId, id, tDelete)
                    }
                } else {
                    val jobId = localJobId ?: remoteJobId!!
                    if (localItem != null && remoteItem != null) {
                        if (tLocal > tRemote) {
                            remote.fs.addPayslip(jobId, localItem, tLocal)
                        } else if (tRemote > tLocal) {
                            local.addPayslip(jobId, remoteItem)
                            metadata.setUpdatedAt(id, tRemote)
                        }
                    } else if (localItem != null) {
                        remote.fs.addPayslip(jobId, localItem, tLocal)
                    } else if (remoteItem != null) {
                        local.addPayslip(jobId, remoteItem)
                        metadata.setUpdatedAt(id, tRemote)
                    }
                }
            }

            // --- 10. Synchronisation des Paramètres ---
            val remoteTheme = snapshot.child("settings").child("appTheme").getValue(String::class.java)
            val remoteThemeUpdatedAt = snapshot.child("settings").lng("appThemeUpdatedAt")

            val localTheme = local.getAppTheme().first()
            val localThemeUpdatedAt = metadata.getUpdatedAt("appTheme")

            if (remoteTheme != null) {
                if (localThemeUpdatedAt > remoteThemeUpdatedAt) {
                    remote.fs.updateAppTheme(localTheme, localThemeUpdatedAt)
                } else if (remoteThemeUpdatedAt > localThemeUpdatedAt) {
                    local.updateAppTheme(remoteTheme)
                    metadata.setUpdatedAt("appTheme", remoteThemeUpdatedAt)
                }
            } else {
                remote.fs.updateAppTheme(localTheme, localThemeUpdatedAt)
            }

            // Langue de l'app (même stratégie que le thème)
            val remoteLang = snapshot.child("settings").child("appLanguage").getValue(String::class.java)
            val remoteLangUpdatedAt = snapshot.child("settings").lng("appLanguageUpdatedAt")
            val localLang = local.getAppLanguage().first()
            val localLangUpdatedAt = metadata.getUpdatedAt("appLanguage")
            if (remoteLang != null) {
                if (localLangUpdatedAt > remoteLangUpdatedAt) {
                    remote.fs.updateAppLanguage(localLang, localLangUpdatedAt)
                } else if (remoteLangUpdatedAt > localLangUpdatedAt) {
                    local.updateAppLanguage(remoteLang)
                    metadata.setUpdatedAt("appLanguage", remoteLangUpdatedAt)
                }
            } else if (localLang != "system") {
                remote.fs.updateAppLanguage(localLang, localLangUpdatedAt)
            }

            val remoteGeminiKey = snapshot.child("settings").child("geminiApiKey").getValue(String::class.java)
            val remoteGeminiKeyUpdatedAt = snapshot.child("settings").lng("geminiApiKeyUpdatedAt")

            val localGeminiKey = local.getGeminiApiKey()
            val localGeminiKeyUpdatedAt = metadata.getUpdatedAt("geminiApiKey")

            if (remoteGeminiKey != null) {
                if (localGeminiKeyUpdatedAt > remoteGeminiKeyUpdatedAt) {
                    remote.fs.saveGeminiApiKey(localGeminiKey ?: "", localGeminiKeyUpdatedAt)
                } else if (remoteGeminiKeyUpdatedAt > localGeminiKeyUpdatedAt) {
                    local.saveGeminiApiKey(remoteGeminiKey)
                    metadata.setUpdatedAt("geminiApiKey", remoteGeminiKeyUpdatedAt)
                }
            } else if (localGeminiKey != null) {
                remote.fs.saveGeminiApiKey(localGeminiKey, localGeminiKeyUpdatedAt)
            }
        }
    }

    suspend fun forceSaveLocal() {
        // 1. Sauvegarde locale immédiate sur le disque — ne dépend PAS du réseau.
        local.forcePersist()

        // 2. Tentative de réconciliation avec le serveur en best-effort :
        //    si le réseau est absent ou lent, on n'échoue pas pour autant.
        try {
            val ref = activeRef ?: remote.fs.userRef
            val snapshot = kotlinx.coroutines.withTimeout(5000) {
                ref.get().await()
            }
            performSync(snapshot)
        } catch (_: Exception) {
            // Hors-ligne / timeout : la sauvegarde locale a déjà réussi.
        }
    }

    override suspend fun transferOldDataIfNeeded(newUserId: String) {
        remote.transferOldDataIfNeeded(newUserId)
    }

    override suspend fun deleteAllUserData() {
        local.deleteAllUserData()
        remote.deleteAllUserData()
        metadataManager?.clear()
    }

    override fun connectionStatus(): Flow<ConnectionStatus> = remote.connectionStatus()
    override fun getCompanies(): Flow<List<Company>> = local.getCompanies()
    override fun getJobs(): Flow<List<Job>> = local.getJobs()
    override fun getEntries(jobId: String): Flow<List<DayEntry>> = local.getEntries(jobId)
    override fun getTemplates(jobId: String): Flow<List<DayTemplate>> = local.getTemplates(jobId)
    override fun getAutoRules(jobId: String): Flow<List<AutoEntryRule>> = local.getAutoRules(jobId)
    override fun getPayslips(jobId: String): Flow<List<Payslip>> = local.getPayslips(jobId)
    override fun getAppTheme(): Flow<String> = local.getAppTheme()
    override fun getAppLanguage(): Flow<String> = local.getAppLanguage()
    override suspend fun getGeminiApiKey(): String? = local.getGeminiApiKey()

    override suspend fun addCompany(company: Company) {
        local.addCompany(company)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(company.id, now)
        syncScope.launch {
            try { remote.fs.addCompany(company, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteCompany(companyId: String) {
        local.deleteCompany(companyId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(companyId, now)
        syncScope.launch {
            try { remote.fs.deleteCompany(companyId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addJob(job: Job) {
        local.addJob(job)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(job.id, now)
        syncScope.launch {
            try { remote.fs.addJob(job, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun updateJob(job: Job) {
        local.updateJob(job)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(job.id, now)
        syncScope.launch {
            try { remote.fs.updateJob(job, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteJob(jobId: String) {
        local.deleteJob(jobId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(jobId, now)
        syncScope.launch {
            try { remote.fs.deleteJob(jobId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun updateJobFields(jobId: String, fields: Map<String, Any?>) {
        local.updateJobFields(jobId, fields)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(jobId, now)
        syncScope.launch {
            try { remote.fs.updateJobFields(jobId, fields, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun forceRefresh(jobId: String?) {
        remote.forceRefresh(jobId)
    }

    override suspend fun setMainJob(jobId: String) {
        local.setMainJob(jobId)
        val now = System.currentTimeMillis()
        local.getJobs().first().forEach { job ->
            metadataManager?.setUpdatedAt(job.id, now)
        }
        syncScope.launch {
            try { remote.fs.setMainJob(jobId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addDayEntry(jobId: String, entry: DayEntry) {
        local.addDayEntry(jobId, entry)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(entry.id, now)
        syncScope.launch {
            try { remote.fs.addDayEntry(jobId, entry, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addDayEntries(jobId: String, entries: List<DayEntry>) {
        local.addDayEntries(jobId, entries)
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            metadataManager?.setUpdatedAt(entry.id, now)
        }
        syncScope.launch {
            try { remote.fs.addDayEntries(jobId, entries, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteDayEntry(jobId: String, entryId: String) {
        local.deleteDayEntry(jobId, entryId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(entryId, now)
        syncScope.launch {
            try { remote.fs.deleteDayEntry(jobId, entryId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteAllEntries(jobId: String) {
        val entries = local.getEntries(jobId).first()
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            local.deleteDayEntry(jobId, entry.id)
            metadataManager?.setDeletedAt(entry.id, now)
        }
        syncScope.launch {
            try {
                val updates = mutableMapOf<String, Any?>()
                entries.forEach { updates["deleted/${it.id}"] = now }
                if (updates.isNotEmpty()) {
                    remote.fs.userRef.updateChildren(updates).await()
                }
                remote.fs.userRef.child("jobs").child(jobId).child("days").removeValue().await()
            } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addTemplate(jobId: String, template: DayTemplate) {
        local.addTemplate(jobId, template)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(template.id, now)
        syncScope.launch {
            try { remote.fs.addTemplate(jobId, template, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteTemplate(jobId: String, templateId: String) {
        local.deleteTemplate(jobId, templateId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(templateId, now)
        syncScope.launch {
            try { remote.fs.deleteTemplate(jobId, templateId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addAutoRule(jobId: String, rule: AutoEntryRule) {
        local.addAutoRule(jobId, rule)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(rule.id, now)
        syncScope.launch {
            try { remote.fs.addAutoRule(jobId, rule, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deleteAutoRule(jobId: String, ruleId: String) {
        local.deleteAutoRule(jobId, ruleId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(ruleId, now)
        syncScope.launch {
            try { remote.fs.deleteAutoRule(jobId, ruleId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun addPayslip(jobId: String, payslip: Payslip) {
        local.addPayslip(jobId, payslip)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt(payslip.id, now)
        syncScope.launch {
            try { remote.fs.addPayslip(jobId, payslip, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun deletePayslip(jobId: String, payslipId: String) {
        local.deletePayslip(jobId, payslipId)
        val now = System.currentTimeMillis()
        metadataManager?.setDeletedAt(payslipId, now)
        syncScope.launch {
            try { remote.fs.deletePayslip(jobId, payslipId, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun updateAppTheme(theme: String) {
        local.updateAppTheme(theme)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt("appTheme", now)
        syncScope.launch {
            try { remote.fs.updateAppTheme(theme, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun updateAppLanguage(lang: String) {
        local.updateAppLanguage(lang)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt("appLanguage", now)
        syncScope.launch {
            try { remote.fs.updateAppLanguage(lang, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }

    override suspend fun saveGeminiApiKey(key: String) {
        local.saveGeminiApiKey(key)
        val now = System.currentTimeMillis()
        metadataManager?.setUpdatedAt("geminiApiKey", now)
        syncScope.launch {
            try { remote.fs.saveGeminiApiKey(key, now) } catch (e: Exception) { android.util.Log.e("SyncDataService", "Error in remote sync", e) }
        }
    }
}

private fun DataSnapshot.lng(key: String): Long {
    val childSnap = child(key)
    val value = childSnap.value ?: return 0L
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

private fun DataSnapshot.lng(): Long {
    val value = value ?: return 0L
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

