package com.benjamin.salarytracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

/** Export des journées au format CSV (séparateur ';' pour Excel FR) + partage. */
object ExportService {

    fun exportCsv(context: Context, job: Job, entries: List<DayEntry>) {
        val sb = StringBuilder()
        sb.append("﻿") // BOM pour Excel/UTF-8
        sb.appendLine("Date;Type;Debut;Fin;Pause (min);Heures")

        entries.sortedBy { it.date }.forEach { e ->
            val type = if (e.isLeave) "Conge" else "Travail"
            val heures = if (e.isLeave) "0" else String.format(Locale.FRANCE, "%.2f", e.totalHours)
            sb.appendLine("${e.date};$type;${e.startTime};${e.endTime};${e.pauseMinutes};$heures")
        }

        // Récap mensuel
        sb.appendLine()
        sb.appendLine("Mois;Jours;Heures;Net estime")
        entries.groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .forEach { (month, monthEntries) ->
                val s = SalaryCalculator.calculateMonthStats(job, monthEntries, month)
                val totH = String.format(Locale.FRANCE, "%.2f", s.totalHeuresReellesMois)
                val net = String.format(Locale.FRANCE, "%.2f", s.salaireNetEstime)
                sb.appendLine("$month;${monthEntries.count { !it.isLeave }};$totH;$net")
            }

        try {
            val file = File(context.cacheDir, "journees_${job.name.replace(" ", "_")}.csv")
            file.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject, job.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_chooser)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.export_failed, e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun parseLocalCsv(context: Context, uri: Uri, jobId: String): List<DayEntry> {
        val entries = mutableListOf<DayEntry>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = inputStream.bufferedReader(Charsets.UTF_8)
                var line = reader.readLine()
                if (line != null && line.startsWith("\uFEFF")) {
                    line = line.substring(1)
                }
                while (line != null) {
                    if (line.isNotBlank()) {
                        val entry = parseLine(line, jobId)
                        if (entry != null) {
                            entries.add(entry)
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExportService", "parseLocalCsv failed", e)
        }
        return entries
    }

    private fun parseLine(line: String, jobId: String): DayEntry? {
        val separator = if (line.contains(";")) ";" else ","
        val parts = line.split(separator)
        if (parts.size < 5) return null

        val dateStr = parts[0].trim()
        val typeStr = parts[1].trim()
        val startStr = parts[2].trim()
        val endStr = parts[3].trim()
        val pauseStr = parts[4].trim()

        val date = try {
            LocalDate.parse(dateStr)
        } catch (e: Exception) {
            return null
        }

        val isLeave = typeStr.contains("conge", ignoreCase = true) ||
                      typeStr.contains("congé", ignoreCase = true) ||
                      typeStr.contains("leave", ignoreCase = true) ||
                      typeStr.contains("absence", ignoreCase = true)

        val startTime = try {
            if (startStr.isEmpty()) LocalTime.MIN else parseTime(startStr)
        } catch (e: Exception) {
            LocalTime.MIN
        }

        val endTime = try {
            if (endStr.isEmpty()) LocalTime.MIN else parseTime(endStr)
        } catch (e: Exception) {
            LocalTime.MIN
        }

        val pauseMinutes = try {
            if (pauseStr.isEmpty()) 0L else pauseStr.toLong()
        } catch (e: Exception) {
            0L
        }

        return DayEntry(
            id = java.util.UUID.randomUUID().toString(),
            jobId = jobId,
            date = date,
            startTime = startTime,
            endTime = endTime,
            pauseMinutes = pauseMinutes,
            isLeave = isLeave
        )
    }

    private fun parseTime(timeStr: String): LocalTime {
        val cleanTime = timeStr.replace("h", ":").trim()
        val parts = cleanTime.split(":")
        if (parts.isEmpty()) return LocalTime.MIN
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
}
