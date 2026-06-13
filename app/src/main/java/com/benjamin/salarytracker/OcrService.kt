package com.benjamin.salarytracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Résultat d'une analyse de contrat. */
sealed interface ContractAnalysis {
    data class Success(val job: Job) : ContractAnalysis
    data class Failure(val reason: String) : ContractAnalysis
}

/** Résultat d'une analyse de bulletin de salaire. */
sealed interface PayslipAnalysis {
    data class Success(val payslip: Payslip) : PayslipAnalysis
    data class Failure(val reason: String) : PayslipAnalysis
}

/**
 * Analyse de contrat via l'API REST Gemini 2.5 Flash.
 *
 * Supporte MULTIPLES fichiers de formats mixtes (images JPEG/PNG ET PDF) en une
 * seule requête : chaque pièce est convertie en Base64 et injectée dans le
 * tableau `parts`, à côté du prompt. Gemini lie toutes les pages pour extraire
 * les paramètres du contrat (taux horaire, heures hebdo, règles du livret…).
 */
class OcrService(private val context: Context, private val apiKey: String) {

    companion object {
        private val DAYS_IMPORT_PROMPT = """
            Tu es un assistant spécialisé dans l'analyse de relevés d'heures et d'historiques de travail.
            Analyse le texte fourni (qui peut être un relevé d'heures informel, une prise de note ou un export CSV/Excel) 
            et extrais toutes les journées de travail ou de congé.
            
            Pour chaque journée, trouve :
            - La date (format YYYY-MM-DD) ;
            - Le type : "Travail" ou "Conge" (congés, absences, repos) ;
            - L'heure de début (format HH:mm, par défaut "00:00" si congé) ;
            - L'heure de fin (format HH:mm, par défaut "00:00" si congé) ;
            - La durée de la pause en minutes (nombre entier, défaut 0).
            
            Réponds UNIQUEMENT avec un tableau JSON strict, sans texte autour ni formatage markdown (pas de ```json ... ```), sous cette forme :
            [
              {
                "date": "YYYY-MM-DD",
                "isLeave": boolean,
                "startTime": "HH:mm",
                "endTime": "HH:mm",
                "pauseMinutes": number
              },
              ...
            ]
            
            Si l'année n'est pas spécifiée dans une date, utilise l'année en cours (2026).
        """.trimIndent()

        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private const val IMAGE_COMPRESSION_QUALITY = 80
        private const val TAG = "OcrService"

        private val EXTRACTION_PROMPT = """
            Tu es un assistant RH spécialisé dans les contrats de travail français,
            notamment les contrats agricoles avec modulation du temps de travail.
            Analyse l'INTÉGRALITÉ des documents fournis (pages multiples, images et PDF
            mélangés font partie d'UN SEUL contrat) et extrais les informations.

            Cherche ACTIVEMENT, même si l'info est dispersée dans le texte :
            - les DATES de début et de fin du contrat (CDD, période d'essai, échéance) ;
            - tout dispositif de LIVRET / modulation / capitalisation des heures
              supplémentaires (repos compensateur, paiement en fin de contrat, solde de
              tout compte, annualisation) → dans ce cas overtimeMode = "CAPITALISEE".

            Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour, au format :
            {
              "name": string,                 // Nom de l'entreprise / employeur
              "hourlyRateBrut": number,       // Taux horaire BRUT en euros (ex: 11.88)
              "weeklyContractHours": number,  // Heures hebdomadaires contractuelles (ex: 35)
              "annualOvertimeQuota": number,  // Contingent annuel d'heures sup (défaut 220)
              "overtimeMode": "PAYEE" | "CAPITALISEE", // CAPITALISEE si modulation / livret / repos compensateur / paiement fin de contrat
              "startDate": string | null,     // Date de début au format ISO "YYYY-MM-DD", ou null
              "endDate": string | null,       // Date de fin au format ISO "YYYY-MM-DD", ou null si CDI / non précisée
              "hasLivret": boolean            // true s'il existe un livret / capitalisation des heures
            }

            Convertis toute date française (ex: "1er mars 2024", "01/03/2024") au format ISO.
            Si une valeur est introuvable, utilise : name="Entreprise", hourlyRateBrut=0,
            weeklyContractHours=35, annualOvertimeQuota=220, overtimeMode="PAYEE",
            startDate=null, endDate=null, hasLivret=false.
        """.trimIndent()

        private val PAYSLIP_PROMPT = """
            Tu es un expert en bulletins de paie français. Analyse l'INTÉGRALITÉ du
            ou des documents (un seul bulletin, éventuellement sur plusieurs pages/images)
            et extrais les montants.

            Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour :
            {
              "month": number,         // mois de la période de paie (1-12)
              "year": number,          // année (ex: 2026)
              "brut": number,          // salaire brut total
              "net": number,           // NET À PAYER (ce qui est versé)
              "netImposable": number,  // net imposable / net fiscal
              "heuresPayees": number,  // total des heures payées
              "cotisations": number    // total des cotisations/contributions salariales
            }

            Les montants sont en euros, point décimal (ex: 1512.34). Si une valeur est
            introuvable, mets 0 (sauf month/year qu'il faut déduire de la période).
        """.trimIndent()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API publique — analyse multi-format
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convertit toutes les [uris] (images et/ou PDF) en Base64, construit une
     * requête Gemini unique et retourne un [Job] pré-rempli, ou null en cas d'échec.
     *
     * @param onStatus callback de progression (libellé à afficher dans l'UI).
     */
    suspend fun extractMultiContractData(
        uris: List<Uri>,
        onStatus: (String) -> Unit = {}
    ): ContractAnalysis = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ContractAnalysis.Failure("Aucun fichier sélectionné")
        if (apiKey.isBlank()) {
            return@withContext ContractAnalysis.Failure("Veuillez renseigner votre clé API Gemini dans les Paramètres.")
        }

        try {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", EXTRACTION_PROMPT))

            uris.forEachIndexed { index, uri ->
                onStatus("Préparation du document ${index + 1}/${uris.size}…")
                val attachment = buildAttachmentPart(uri) ?: return@forEachIndexed
                parts.put(attachment)
            }

            // Aucune pièce valide récupérée
            if (parts.length() <= 1) {
                return@withContext ContractAnalysis.Failure("Aucun document lisible (formats acceptés : images, PDF)")
            }

            onStatus("Analyse de ${uris.size} document(s) par l'IA en cours…")
            val responseJson = callGemini(parts) // lève une exception en cas d'erreur HTTP
            val job = parseJobFromResponse(responseJson)
                ?: return@withContext ContractAnalysis.Failure("Réponse de l'IA illisible")
            ContractAnalysis.Success(job)
        } catch (e: Exception) {
            Log.e(TAG, "extractMultiContractData failed", e)
            ContractAnalysis.Failure(e.message ?: "Erreur réseau inconnue")
        }
    }

    /** Analyse un bulletin de salaire (images/PDF) et retourne un [Payslip]. */
    suspend fun extractPayslipData(
        uris: List<Uri>,
        onStatus: (String) -> Unit = {}
    ): PayslipAnalysis = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext PayslipAnalysis.Failure("Aucun fichier sélectionné")
        if (apiKey.isBlank()) {
            return@withContext PayslipAnalysis.Failure("Veuillez renseigner votre clé API Gemini dans les Paramètres.")
        }
        try {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", PAYSLIP_PROMPT))
            uris.forEachIndexed { index, uri ->
                onStatus("Préparation du document ${index + 1}/${uris.size}…")
                buildAttachmentPart(uri)?.let { parts.put(it) }
            }
            if (parts.length() <= 1) {
                return@withContext PayslipAnalysis.Failure("Aucun document lisible (images ou PDF)")
            }
            onStatus("Analyse du bulletin par l'IA…")
            val responseJson = callGemini(parts)
            val payslip = parsePayslipFromResponse(responseJson)
                ?: return@withContext PayslipAnalysis.Failure("Réponse de l'IA illisible")
            PayslipAnalysis.Success(payslip)
        } catch (e: Exception) {
            Log.e(TAG, "extractPayslipData failed", e)
            PayslipAnalysis.Failure(e.message ?: "Erreur réseau inconnue")
        }
    }

    private fun parsePayslipFromResponse(raw: String): Payslip? {
        return try {
            val root = JSONObject(raw)
            val text = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            val data = JSONObject(text.trim())
            val month = data.optInt("month", 0)
            val year = data.optInt("year", 0)
            if (month !in 1..12 || year < 2000) return null
            Payslip(
                month = month,
                year = year,
                brut = data.optDouble("brut", 0.0),
                net = data.optDouble("net", 0.0),
                netImposable = data.optDouble("netImposable", 0.0),
                heuresPayees = data.optDouble("heuresPayees", 0.0),
                cotisations = data.optDouble("cotisations", 0.0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "parsePayslipFromResponse failed : $raw", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion multi-format → part Base64
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAttachmentPart(uri: Uri): JSONObject? {
        val mime = context.contentResolver.getType(uri) ?: return null

        return when {
            mime.startsWith("image/") -> {
                val base64 = imageToBase64(uri) ?: return null
                inlineData("image/jpeg", base64)
            }
            mime == "application/pdf" -> {
                val base64 = bytesToBase64(uri) ?: return null
                inlineData("application/pdf", base64)
            }
            else -> {
                Log.w(TAG, "Type non supporté ignoré : $mime")
                null
            }
        }
    }

    /** Décode l'image, la compresse légèrement (JPEG) puis encode en Base64. */
    private fun imageToBase64(uri: Uri): String? {
        return try {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, _, _ -> decoder.isMutableRequired = false }
            }
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_COMPRESSION_QUALITY, baos)
            bitmap.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "imageToBase64 failed for $uri", e)
            null
        }
    }

    /** Lit directement les octets du PDF (toutes les pages) et encode en Base64. */
    private fun bytesToBase64(uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "bytesToBase64 failed for $uri", e)
            null
        }
    }

    private fun inlineData(mimeType: String, base64: String): JSONObject =
        JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", mimeType).put("data", base64)
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Requête HTTP Gemini
    // ─────────────────────────────────────────────────────────────────────────

    /** Effectue l'appel HTTP. Lève une exception explicite en cas d'erreur. */
    private fun callGemini(parts: JSONArray): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().put("response_mime_type", "application/json"))
        }

        val url = URL(ENDPOINT)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Nouveau format de clé API Gemini → authentification via en-tête
            setRequestProperty("x-goog-api-key", apiKey)
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }

            if (code !in 200..299) {
                Log.e(TAG, "Gemini HTTP $code : $response")
                // Extrait le message d'erreur lisible de l'API si disponible
                val apiMsg = runCatching {
                    JSONObject(response ?: "").getJSONObject("error").getString("message")
                }.getOrNull()
                throw java.io.IOException("HTTP $code — ${apiMsg ?: "vérifie la clé API et le modèle"}")
            }
            return response ?: throw java.io.IOException("Réponse vide du serveur")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Extrait le JSON renvoyé par Gemini (candidates[0].content.parts[0].text)
     * puis le mappe vers un [Job].
     */
    private fun parseJobFromResponse(raw: String): Job? {
        return try {
            val root = JSONObject(raw)
            val text = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val data = JSONObject(text.trim())

            // Livret = soit overtimeMode CAPITALISEE, soit le flag hasLivret
            val modeStr = data.optString("overtimeMode", "PAYEE").uppercase()
            val hasLivret = data.optBoolean("hasLivret", false)
            val mode = if (modeStr == "CAPITALISEE" || hasLivret)
                OvertimeMode.CAPITALISEE else OvertimeMode.PAYEE

            Job(
                name = data.optString("name", "Entreprise").ifBlank { "Entreprise" },
                hourlyRateBrut = data.optDouble("hourlyRateBrut", 0.0),
                weeklyContractHours = data.optDouble("weeklyContractHours", 35.0),
                annualOvertimeQuota = data.optInt("annualOvertimeQuota", 220),
                overtimeMode = mode,
                startDate = parseIsoDateOrNull(data.optString("startDate", "")),
                endDate = parseIsoDateOrNull(data.optString("endDate", ""))
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseJobFromResponse failed : $raw", e)
            null
        }
    }

    /** Parse une date ISO "YYYY-MM-DD" en LocalDate, ou null si invalide/vide. */
    private fun parseIsoDateOrNull(raw: String?): java.time.LocalDate? {
        if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return null
        return try {
            java.time.LocalDate.parse(raw.trim())
        } catch (e: Exception) {
            Log.w(TAG, "Date non parsable : $raw")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper : nom lisible d'un fichier depuis son Uri (pour l'affichage)
    // ─────────────────────────────────────────────────────────────────────────

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

    suspend fun extractDaysFromText(
        text: String,
        onStatus: (String) -> Unit = {}
    ): List<DayEntry> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("Veuillez renseigner votre clé API Gemini dans les Paramètres.")
        }
        onStatus("Analyse de l'historique par l'IA...")

        val parts = JSONArray()
        parts.put(JSONObject().put("text", DAYS_IMPORT_PROMPT))
        parts.put(JSONObject().put("text", text))

        val responseJson = callGemini(parts)
        parseDaysFromResponse(responseJson)
    }

    private fun parseDaysFromResponse(raw: String): List<DayEntry> {
        val entries = mutableListOf<DayEntry>()
        try {
            val root = JSONObject(raw)
            val text = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = JSONArray(cleanText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                try {
                    val date = java.time.LocalDate.parse(obj.getString("date"))
                    val isLeave = obj.optBoolean("isLeave", false)
                    val startTime = java.time.LocalTime.parse(obj.optString("startTime", "00:00"))
                    val endTime = java.time.LocalTime.parse(obj.optString("endTime", "00:00"))
                    val pauseMinutes = obj.optLong("pauseMinutes", 0L)

                    entries.add(
                        DayEntry(
                            id = java.util.UUID.randomUUID().toString(),
                            jobId = "", // sera renseigné par le ViewModel
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            pauseMinutes = pauseMinutes,
                            isLeave = isLeave
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse individual day entry: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseDaysFromResponse failed : $raw", e)
        }
        return entries
    }
}
