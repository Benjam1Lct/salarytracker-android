package com.benjamin.salarytracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Service d'analyse locale (sans API cloud) basé sur :
 *  - ML Kit Text Recognition pour l'OCR on-device
 *  - Expressions régulières pour extraire les données structurées
 *
 * Utilisé comme fallback quand l'utilisateur n'a pas configuré de clé Gemini.
 */
class LocalOcrService(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "LocalOcrService"

    // ─── OCR helper ──────────────────────────────────────────────────────────

    /** Extrait le texte d'une URI image via ML Kit OCR. */
    private suspend fun extractTextFromImageUri(uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, _, _ -> decoder.isMutableRequired = true }
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    bitmap.recycle()
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit OCR failed", e)
                    cont.resume("")
                }
        } catch (e: Exception) {
            Log.e(TAG, "extractTextFromImageUri failed", e)
            cont.resume("")
        }
    }

    /** OCR public d'une image (ML Kit, on-device) → texte brut. */
    suspend fun ocrImage(uri: Uri): String = withContext(Dispatchers.IO) {
        extractTextFromImageUri(uri)
    }

    /** Lit le texte brut d'un fichier texte/CSV via ContentResolver. */
    private fun extractTextFromTextUri(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "extractTextFromTextUri failed", e)
            ""
        }
    }

    fun resolveFileName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex)
                else uri.lastPathSegment ?: "fichier"
            } ?: uri.lastPathSegment ?: "fichier"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "fichier"
        }
    }

    /** Extrait le texte de tous les URIs (images ou texte brut). PDF non supporté en local. */
    private suspend fun extractAllText(uris: List<Uri>, onStatus: (String) -> Unit): String {
        val builder = StringBuilder()
        uris.forEachIndexed { index, uri ->
            onStatus("Lecture du document ${index + 1}/${uris.size}…")
            val mime = context.contentResolver.getType(uri) ?: ""
            val text = when {
                mime.startsWith("image/") -> extractTextFromImageUri(uri)
                mime == "application/pdf" -> {
                    // ML Kit ne supporte pas les PDF directement — on essaie le texte brut
                    extractTextFromTextUri(uri).ifBlank {
                        "[PDF non supporté en mode local — veuillez utiliser une image]"
                    }
                }
                else -> extractTextFromTextUri(uri)
            }
            if (text.isNotBlank()) builder.append(text).append("\n\n")
        }
        return builder.toString()
    }

    // ─── Analyse de contrat ──────────────────────────────────────────────────

    /**
     * Analyse des documents (images) de contrat par OCR local + regex.
     * Retourne un [ContractAnalysis] avec les données extraites.
     */
    suspend fun extractMultiContractData(
        uris: List<Uri>,
        onStatus: (String) -> Unit = {}
    ): ContractAnalysis = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ContractAnalysis.Failure("Aucun fichier sélectionné")

        try {
            val text = extractAllText(uris, onStatus)
            if (text.isBlank()) {
                return@withContext ContractAnalysis.Failure("Aucun texte lisible dans les documents")
            }

            onStatus("Analyse locale du contrat…")
            val job = parseContractFromText(text)
            ContractAnalysis.Success(job)
        } catch (e: Exception) {
            Log.e(TAG, "extractMultiContractData local failed", e)
            ContractAnalysis.Failure(e.message ?: "Erreur d'analyse locale")
        }
    }

    private fun parseContractFromText(text: String): Job {
        val lower = text.lowercase()

        // Nom entreprise
        val name = extractCompanyName(text) ?: "Entreprise"

        // Taux horaire brut
        val hourlyRate = extractHourlyRate(text) ?: 0.0

        // Heures hebdomadaires
        val weeklyHours = extractWeeklyHours(text) ?: 35.0

        // Contingent heures sup
        val quota = extractOvertimeQuota(text) ?: 220

        // Mode de gestion des heures sup
        val overtimeMode = when {
            lower.contains("livret") || lower.contains("modulat") || lower.contains("capitalisa") ->
                OvertimeMode.CAPITALISEE
            lower.contains("récupération") || lower.contains("recuperation") || lower.contains("repos compensateur") ->
                OvertimeMode.RECUPERATION
            lower.contains("compte épargne") || lower.contains("c.e.t") || lower.contains("cet ") ->
                OvertimeMode.CET
            lower.contains("forfait jour") || lower.contains("forfait en jours") ->
                OvertimeMode.FORFAIT_JOURS
            else -> OvertimeMode.PAYEE
        }

        // Type de contrat
        val contractType = when {
            lower.contains("intérim") || lower.contains("interim") || lower.contains("mission d'intérim") ->
                ContractType.INTERIM
            lower.contains("cdd") || lower.contains("durée déterminée") || lower.contains("duree determinee") ->
                ContractType.CDD
            lower.contains("alternance") || lower.contains("apprentissage") || lower.contains("professionnalisation") ->
                ContractType.ALTERNANCE
            lower.contains("stage") || lower.contains("stagiaire") ->
                ContractType.STAGE
            lower.contains("mission") && !lower.contains("mission d'intérim") ->
                ContractType.MISSION
            else -> ContractType.CDI
        }

        // Dates
        val startDate = extractDate(text, isStart = true)
        val endDate = extractDate(text, isStart = false)

        return Job(
            name = name,
            companyName = name,
            contractType = contractType,
            hourlyRateBrut = hourlyRate,
            weeklyContractHours = weeklyHours,
            annualOvertimeQuota = quota,
            overtimeMode = overtimeMode,
            startDate = startDate,
            endDate = endDate
        )
    }

    private fun extractCompanyName(text: String): String? {
        // Patterns communs pour le nom de l'employeur dans les contrats français
        val patterns = listOf(
            Regex("(?:employeur|société|entreprise|établissement|sarl|sas|eurl|sa|association)[\\s:]+([A-ZÀ-Ÿ][\\wÀ-ÿ\\s&-]{2,50})", RegexOption.IGNORE_CASE),
            Regex("(?:entre|entre les soussignés)[\\s:]+([A-ZÀ-Ÿ][\\wÀ-ÿ\\s&-]{2,50})\\s*(?:,|\\n|représent)", RegexOption.IGNORE_CASE),
            Regex("^([A-ZÀ-Ÿ][A-ZÀ-Ÿ\\s&.-]{5,50})\\s*$", RegexOption.MULTILINE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 3..60) return candidate
            }
        }
        return null
    }

    private fun extractHourlyRate(text: String): Double? {
        val patterns = listOf(
            Regex("(\\d{1,3}[,.]\\d{1,4})\\s*(?:€|euros?)\\s*(?:brut)?\\s*(?:/|par)\\s*heure", RegexOption.IGNORE_CASE),
            Regex("taux\\s+horaire\\s+brut\\s*:?\\s*(\\d{1,3}[,.]\\d{1,4})", RegexOption.IGNORE_CASE),
            Regex("salaire\\s+horaire\\s+brut\\s*:?\\s*(\\d{1,3}[,.]\\d{1,4})", RegexOption.IGNORE_CASE),
            Regex("(?:taux|salaire)\\s+horaire\\s*:?\\s*(\\d{1,3}[,.]\\d{1,4})", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val raw = match.groupValues[1].replace(',', '.')
                val value = raw.toDoubleOrNull()
                if (value != null && value in 8.0..200.0) return value
            }
        }
        return null
    }

    private fun extractWeeklyHours(text: String): Double? {
        val patterns = listOf(
            Regex("(\\d{2}(?:[,.]\\d{1,2})?)\\s*(?:heures?|h)\\s*(?:/|par)\\s*semaine", RegexOption.IGNORE_CASE),
            Regex("durée\\s+(?:hebdomadaire|du\\s+travail)\\s*:?\\s*(\\d{2}(?:[,.]\\d{1,2})?)\\s*(?:heures?|h)", RegexOption.IGNORE_CASE),
            Regex("(35|39|40|32|28|24)\\s*(?:heures?|h)\\s*(?:hebdomadaires?|/semaine)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val raw = match.groupValues[1].replace(',', '.')
                val value = raw.toDoubleOrNull()
                if (value != null && value in 10.0..60.0) return value
            }
        }
        return null
    }

    private fun extractOvertimeQuota(text: String): Int? {
        val patterns = listOf(
            Regex("contingent\\s+(?:annuel|d'heures\\s+supplémentaires?)\\s*:?\\s*(\\d{2,4})", RegexOption.IGNORE_CASE),
            Regex("(\\d{2,4})\\s*heures?\\s*(?:de|d')\\s*(?:contingent|quota)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 100..500) return value
            }
        }
        return null
    }

    private fun extractDate(text: String, isStart: Boolean): LocalDate? {
        val keyword = if (isStart) {
            listOf("à compter du", "prend effet le", "début", "embauche", "du", "à partir du", "le")
        } else {
            listOf("au", "jusqu'au", "jusqu'au", "se termine le", "fin du contrat", "échéance")
        }

        // Formats de dates françaises
        val datePatterns = listOf(
            Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})"),
            Regex("(\\d{1,2})(?:er|ème)?\\s+(janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)\\s+(\\d{4})", RegexOption.IGNORE_CASE)
        )

        val monthNames = mapOf(
            "janvier" to 1, "février" to 2, "mars" to 3, "avril" to 4,
            "mai" to 5, "juin" to 6, "juillet" to 7, "août" to 8,
            "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12
        )

        // Cherche les dates dans le contexte des mots-clés
        for (kw in keyword) {
            val kwIndex = text.lowercase().indexOf(kw)
            if (kwIndex < 0) continue
            val context = text.substring(kwIndex, minOf(kwIndex + 120, text.length))

            // Essai pattern numérique
            val numMatch = datePatterns[0].find(context)
            if (numMatch != null) {
                try {
                    val day = numMatch.groupValues[1].toInt()
                    val month = numMatch.groupValues[2].toInt()
                    var year = numMatch.groupValues[3].toInt()
                    if (year < 100) year += 2000
                    if (day in 1..31 && month in 1..12 && year in 2015..2040) {
                        return LocalDate.of(year, month, day)
                    }
                } catch (_: Exception) {}
            }

            // Essai pattern lettré
            val letMatch = datePatterns[1].find(context)
            if (letMatch != null) {
                try {
                    val day = letMatch.groupValues[1].toInt()
                    val month = monthNames[letMatch.groupValues[2].lowercase()] ?: continue
                    val year = letMatch.groupValues[3].toInt()
                    if (day in 1..31 && year in 2015..2040) {
                        return LocalDate.of(year, month, day)
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    // ─── Analyse de fiche de paie ─────────────────────────────────────────────

    /**
     * Analyse des documents de fiche de paie par OCR local + regex.
     */
    suspend fun extractPayslipData(
        uris: List<Uri>,
        onStatus: (String) -> Unit = {}
    ): PayslipAnalysis = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext PayslipAnalysis.Failure("Aucun fichier sélectionné")

        try {
            val text = extractAllText(uris, onStatus)
            if (text.isBlank()) {
                return@withContext PayslipAnalysis.Failure("Aucun texte lisible dans les documents")
            }

            onStatus("Analyse locale de la fiche de paie…")
            val payslip = parsePayslipFromText(text)
                ?: return@withContext PayslipAnalysis.Failure("Impossible d'extraire les données de la fiche de paie")
            PayslipAnalysis.Success(payslip)
        } catch (e: Exception) {
            Log.e(TAG, "extractPayslipData local failed", e)
            PayslipAnalysis.Failure(e.message ?: "Erreur d'analyse locale")
        }
    }

    private fun parsePayslipFromText(text: String): Payslip? {
        // Mois et année
        val periodMonths = mapOf(
            "janvier" to 1, "février" to 2, "mars" to 3, "avril" to 4,
            "mai" to 5, "juin" to 6, "juillet" to 7, "août" to 8,
            "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12
        )
        var month = 0
        var year = 0

        val periodPattern = Regex(
            "(janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)\\s+(\\d{4})",
            RegexOption.IGNORE_CASE
        )
        periodPattern.find(text.lowercase())?.let {
            month = periodMonths[it.groupValues[1].lowercase()] ?: 0
            year = it.groupValues[2].toIntOrNull() ?: 0
        }

        // Fallback: pattern MM/YYYY
        if (month == 0) {
            Regex("(\\d{2})/(\\d{4})").find(text)?.let {
                month = it.groupValues[1].toIntOrNull() ?: 0
                year = it.groupValues[2].toIntOrNull() ?: 0
            }
        }

        if (month !in 1..12 || year < 2015) return null

        // Montants
        fun findAmount(vararg keywords: String): Double {
            for (kw in keywords) {
                val idx = text.lowercase().indexOf(kw.lowercase())
                if (idx < 0) continue
                val context = text.substring(idx, minOf(idx + 100, text.length))
                val match = Regex("(\\d{1,6}[,.]\\d{2})").find(context)
                if (match != null) {
                    val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null && v > 0) return v
                }
            }
            return 0.0
        }

        val brut = findAmount("salaire brut", "total brut", "brut total")
        val net = findAmount("net à payer", "net versé", "net payé", "montant net")
        val netImposable = findAmount("net imposable", "net fiscal")
        val cotisations = findAmount("total cotisations", "cotisations salariales", "total retenues")

        // Heures payées
        val heuresPayees = run {
            val match = Regex("(\\d{1,4}[,.]\\d{2})\\s*(?:heures?|h)\\s*(?:payées?|rémunérées?)", RegexOption.IGNORE_CASE).find(text)
                ?: Regex("(?:heures?|h)\\s+(?:payées?|travaillées?)\\s*:?\\s*(\\d{1,4}[,.]?\\d{0,2})", RegexOption.IGNORE_CASE).find(text)
            match?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        }

        return Payslip(
            month = month,
            year = year,
            brut = brut,
            net = net,
            netImposable = netImposable,
            heuresPayees = heuresPayees,
            cotisations = cotisations
        )
    }

    // ─── Analyse de texte libre (import journées) ─────────────────────────────

    /**
     * Parse un texte libre pour en extraire des journées de travail.
     * Fallback local de OcrService.extractDaysFromText().
     */
    suspend fun extractDaysFromText(
        text: String,
        onStatus: (String) -> Unit = {}
    ): List<DayEntry> = withContext(Dispatchers.IO) {
        onStatus("Analyse locale de l'historique…")
        parseDaysFromText(text)
    }

    private fun parseDaysFromText(text: String): List<DayEntry> {
        val entries = mutableListOf<DayEntry>()
        val lines = text.lines()

        // Pattern: date + optionnellement horaires
        // Ex: "2026-06-10 08:00 17:00" ou "10/06/2026 8h-17h" ou "lun 10/06 8h00 17h30"
        val datePattern = Regex("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}[/.-]\\d{2}[/.-]\\d{4})|(\\d{2}[/.-]\\d{2}[/.-]\\d{2})")
        val timePattern = Regex("(\\d{1,2})h?(\\d{2})?")
        val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val frFormatter1 = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val frFormatter2 = DateTimeFormatter.ofPattern("dd/MM/yy")
        val frFormatter3 = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val frFormatter4 = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val dateMatch = datePattern.find(trimmed) ?: continue
            val dateStr = dateMatch.value

            val date: LocalDate = try {
                when {
                    dateStr.contains("-") && dateStr.length == 10 -> LocalDate.parse(dateStr, isoFormatter)
                    dateStr.contains("/") && dateStr.length == 10 -> LocalDate.parse(dateStr, frFormatter1)
                    dateStr.contains("/") && dateStr.length == 8 -> LocalDate.parse(dateStr, frFormatter2)
                    dateStr.contains("-") -> LocalDate.parse(dateStr, frFormatter3)
                    dateStr.contains(".") -> LocalDate.parse(dateStr, frFormatter4)
                    else -> continue
                }
            } catch (_: DateTimeParseException) { continue }

            // Congé / absence?
            val isLeave = trimmed.lowercase().let {
                it.contains("congé") || it.contains("conge") || it.contains("absence") ||
                it.contains("repos") || it.contains("cp ") || it.contains(" cp") ||
                it.contains("maladie") || it.contains("rtt")
            }

            // Horaires
            val after = trimmed.substring(dateMatch.range.last + 1)
            val times = timePattern.findAll(after).take(2).toList()
            val startTime: LocalTime
            val endTime: LocalTime

            if (times.size >= 2 && !isLeave) {
                val sh = times[0].groupValues[1].toIntOrNull() ?: 8
                val sm = times[0].groupValues[2].toIntOrNull() ?: 0
                val eh = times[1].groupValues[1].toIntOrNull() ?: 17
                val em = times[1].groupValues[2].toIntOrNull() ?: 0
                startTime = LocalTime.of(sh.coerceIn(0, 23), sm.coerceIn(0, 59))
                endTime = LocalTime.of(eh.coerceIn(0, 23), em.coerceIn(0, 59))
            } else {
                startTime = LocalTime.of(0, 0)
                endTime = LocalTime.of(0, 0)
            }

            // Détection de la pause : extraction par regex ou valeur par défaut
            val pausePattern = Regex("(?i)(?:pause\\s*[: ]*\\s*(\\d{1,3})\\b)|\\b(\\d{1,3})\\s*(?:min|m\\b|minutes?)\\b")
            val pauseMatch = pausePattern.find(trimmed)
            val pauseMinutes = if (pauseMatch != null) {
                val g1 = pauseMatch.groupValues[1]
                val g2 = pauseMatch.groupValues[2]
                val matchStr = if (g1.isNotEmpty()) g1 else g2
                matchStr.toLongOrNull() ?: 0L
            } else {
                if (times.size >= 2 && !isLeave) {
                    val sh = times[0].groupValues[1].toIntOrNull() ?: 8
                    val sm = times[0].groupValues[2].toIntOrNull() ?: 0
                    val eh = times[1].groupValues[1].toIntOrNull() ?: 17
                    val em = times[1].groupValues[2].toIntOrNull() ?: 0
                    val totalMin = (eh * 60 + em) - (sh * 60 + sm)
                    if (totalMin > 360) 30L else 0L
                } else {
                    0L
                }
            }

            entries.add(
                DayEntry(
                    id = UUID.randomUUID().toString(),
                    jobId = "",
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    pauseMinutes = pauseMinutes,
                    isLeave = isLeave
                )
            )
        }
        return entries
    }
}
