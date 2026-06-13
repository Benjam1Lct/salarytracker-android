package com.benjamin.salarytracker

import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.YearMonth
import java.util.UUID

enum class OvertimeMode {
    PAYEE, CAPITALISEE
}

/** Bulletin de salaire réel (importé/scanné) pour comparer avec l'estimation. */
data class Payslip(
    val id: String = UUID.randomUUID().toString(),
    val month: Int,            // 1–12
    val year: Int,
    val brut: Double = 0.0,
    val net: Double = 0.0,             // net à payer
    val netImposable: Double = 0.0,
    val heuresPayees: Double = 0.0,
    val cotisations: Double = 0.0,     // total cotisations salariales
    val uploadedAt: Long = System.currentTimeMillis()
) {
    val yearMonth: YearMonth get() = YearMonth.of(year, month)
}

/** État de la connexion à la Realtime Database. */
enum class ConnectionStatus {
    /** Connecté et synchronisé avec le serveur. */
    CONNECTED,
    /** Tentative de connexion en cours. */
    CONNECTING,
    /** Connexion établie mais lente / instable (état intermédiaire). */
    SLOW,
    /** Hors connexion : l'app fonctionne sur le cache local. */
    OFFLINE
}

data class Job(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hourlyRateBrut: Double,
    val weeklyContractHours: Double = 35.0,
    val annualOvertimeQuota: Int = 220,
    val overtimeMode: OvertimeMode = OvertimeMode.PAYEE,
    val livretThreshold: Double = 43.0,
    val soldeLivretHeures: Double = 0.0,
    val targetMonthlySalary: Double = 3000.0,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val dayTemplates: List<DayTemplate> = emptyList(),
    val isMainJob: Boolean = false,
    val isArchived: Boolean = false
)

data class DayTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val pauseBlocks: List<PauseBlock> = emptyList()
)

data class PauseBlock(
    val durationMinutes: Long
)

data class DayEntry(
    val id: String = UUID.randomUUID().toString(),
    val jobId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val pauseMinutes: Long,
    /** Jour de congé / absence : ne compte pas comme déficit dans le livret. */
    val isLeave: Boolean = false
) {
    val totalHours: Double
        get() {
            if (isLeave) return 0.0
            val duration = Duration.between(startTime, endTime)
            return (duration.toMinutes() - pauseMinutes) / 60.0
        }
}

/** Mode de la saisie automatique de journées. */
enum class AutoEntryMode {
    /** Répété chaque jour de travail jusqu'à désactivation manuelle. */
    UNTIL_DISABLED,
    /** Limité à une période [startDate, endDate]. */
    PERIOD
}

/**
 * Règle de saisie automatique : génère une journée depuis un template pour
 * chaque jour de travail sélectionné (rattrapage à l'ouverture de l'app).
 */
data class AutoEntryRule(
    val id: String = UUID.randomUUID().toString(),
    /** Si non-null : utilise ce template. Sinon : journée custom (heures ci-dessous). */
    val templateId: String? = null,
    val templateName: String = "",
    // Journée personnalisée (utilisée si templateId == null)
    val customStartTime: LocalTime? = null,
    val customEndTime: LocalTime? = null,
    val customPauseMinutes: Long = 0,
    val active: Boolean = true,
    val mode: AutoEntryMode = AutoEntryMode.UNTIL_DISABLED,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    /** Jours de la semaine concernés : 1 = lundi … 7 = dimanche. */
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5)
)

// Legacy compatibility — used by existing screens
data class SalaryStats(
    val totalBrut: Double,
    val totalNet: Double,
    val normalHours: Double,
    val overtime25: Double,
    val overtime50: Double,
    val quotaUsed: Double,
    val livretHours: Double = 0.0
)
