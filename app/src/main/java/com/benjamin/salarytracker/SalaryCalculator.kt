package com.benjamin.salarytracker

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Moteur de calcul — contrat de modulation agricole Jardins de Rabelais.
 *
 * Règles :
 *  - Salaire lissé sur 151.67h/mois (35h × 52 / 12).
 *  - 35h–43h/sem → heures créditées au livret avec majoration +25%.
 *    Formule : (heures_semaine − 35) × 1.25 créditées sur le livret.
 *  - > 43h/sem → heures de crête payées immédiatement avec +50%.
 *    Formule : (heures_semaine − 43) × 1.50 = équivalent payé.
 *    Les heures 35–43 sont tout de même créditées au plafond (8 × 1.25 = 10h).
 *  - Charges sociales salariales estimées à 22 % (coefficient net = 0.78).
 */
object SalaryCalculator {

    const val NET_COEFFICIENT = 0.78
    const val MONTHLY_SMOOTHED_HOURS = 151.67

    private const val WEEKLY_BASE = 35.0
    private const val LIVRET_CEILING = 43.0
    private const val LIVRET_RATE = 1.25
    private const val CRETE_RATE = 1.50

    // ─── Data classes ────────────────────────────────────────────────────────

    /** Heures attendues par jour de travail (35h / 5 jours). */
    private const val HOURS_PER_DAY = WEEKLY_BASE / 5.0   // 7h

    data class WeekStats(
        /** Ex: "2025-W22" */
        val weekKey: String,
        /** Heures réelles travaillées dans la semaine */
        val realHours: Double,
        /** Heures créditées au livret avec majoration ×1.25 */
        val livretCreditEquivalent: Double,
        /** Heures réelles au-delà du seuil (SANS la majoration, pour l'affichage) */
        val creteRealHours: Double,
        /** Nombre de jours de congé/absence dans la semaine */
        val leaveDays: Int = 0,
        /**
         * Vrai si cette semaine est la semaine en cours (non encore terminée).
         * Dans ce cas, on ne déduit rien du livret et on n'affiche pas de déficit.
         */
        val isCurrentWeek: Boolean = false,
        /** Seuil d'heures attendu pour cette semaine, après déduction des absences */
        val expectedHours: Double = 35.0
    ) {
        val isOvertime: Boolean get() = realHours > expectedHours
        val isCreteWeek: Boolean get() = creteRealHours > 0.0
        /**
         * Semaine sous le seuil attendu → manque d'heures à combler par le livret.
         * Faux si la semaine est en cours : on ne peut pas encore juger.
         */
        val isUnderWeek: Boolean get() = !isCurrentWeek && realHours < expectedHours
        /** Heures manquantes pour atteindre le seuil (débitées du livret) */
        val deficitHours: Double get() = if (isCurrentWeek) 0.0 else (expectedHours - realHours).coerceAtLeast(0.0)
        /** Équivalent ×1.5 utilisé pour le calcul du salaire brut crête */
        val cretePayedEquivalent: Double get() = creteRealHours * CRETE_RATE
    }

    /** Résumé global sur toute la durée du contrat (onglet Stats). */
    data class ContractSummary(
        val start: LocalDate?,
        val end: LocalDate?,
        val joursTravailles: Int,
        val semainesTravaillees: Int,
        val totalHeuresReelles: Double,
        val moyenneHeuresParSemaine: Double,
        val brutTotal: Double,
        val netTotal: Double,
        val soldeLivretHeures: Double,
        /** Valeur nette du livret, payée en fin de contrat s'il reste positif */
        val livretValeurNet: Double,
        /** % de la durée du contrat déjà écoulée (null si dates absentes) */
        val progressionPct: Float?,
        /** Projection du net total à la fin du contrat (null si dates absentes) */
        val projectionNetFinContrat: Double?
    )

    data class MonthDashboardStats(
        /** Fixe lissé : 151.67 × tauxHoraireBrut */
        val salaireBrutBase: Double,
        /** Heures crête × tauxHoraireBrut (payées immédiatement) */
        val salaireBrutHeuresSupPayees: Double,
        /** (salaireBrutBase + salaireBrutHeuresSupPayees) × 0.78 */
        val salaireNetEstime: Double,
        /** Somme des crédits livret +25% accumulés ce mois */
        val heuresAjouteesAuLivretCeMois: Double,
        /** soldeLivretHeures total × tauxHoraireBrut × 0.78 */
        val valeurFinanciereLivretTotal: Double,
        /** Heures réelles totales du mois */
        val totalHeuresReellesMois: Double,
        /** Heures réelles au-delà de 43h (pour affichage, sans majoration) */
        val creteRealHoursMois: Double,
        /** Solde livret cumulé (toutes les entrées, tous mois confondus) */
        val soldeLivretTotal: Double,
        /** Estimation cumulée sur tout le contrat (toutes les entrées) */
        val contrat: EarningsEstimate,
        /** Détail par semaine pour affichage */
        val weeklyDetails: List<WeekStats>
    )

    /** Estimation de gains basée uniquement sur les heures travaillées. */
    data class EarningsEstimate(
        val heuresReelles: Double,
        val brut: Double,
        val net: Double,
        val creteRealHours: Double,
        val livretHeures: Double
    )

    // ─── Calcul hebdomadaire ─────────────────────────────────────────────────

    fun calculateWeekStats(weekKey: String, weekEntries: List<DayEntry>, job: Job): WeekStats {
        val realHours = weekEntries.sumOf { it.totalHours }

        var livretCredit = 0.0
        var creteReal = 0.0

        val baseThreshold = job.weeklyContractHours + job.includedOvertimeHours
        if (realHours > baseThreshold) {
            val threshold = maxOf(job.livretThreshold, baseThreshold)
            val hoursForLivret = minOf(realHours - baseThreshold, threshold - baseThreshold)
            livretCredit = hoursForLivret * LIVRET_RATE

            if (realHours > threshold) {
                creteReal = realHours - threshold
            }
        }

        val hoursPerDay = baseThreshold / 5.0
        val leaveDays = weekEntries.count { it.isLeave }
        val expected = (baseThreshold - hoursPerDay * leaveDays).coerceAtLeast(0.0)

        return WeekStats(
            weekKey = weekKey,
            realHours = realHours,
            livretCreditEquivalent = livretCredit,
            creteRealHours = creteReal,
            leaveDays = leaveDays,
            expectedHours = expected
        )
    }

    // ─── Découpage hebdomadaire ──────────────────────────────────────────────

    /** Clé de semaine ISO (ex "2026-W24") pour une date. */
    fun weekKeyOf(date: LocalDate): String {
        val wf = WeekFields.of(Locale.FRANCE)
        val y = date.get(wf.weekBasedYear())
        val w = date.get(wf.weekOfWeekBasedYear())
        return "$y-W${w.toString().padStart(2, '0')}"
    }

    /**
     * Regroupe les entrées par semaine ISO et calcule les stats de chaque semaine.
     * La semaine contenant [today] est marquée [WeekStats.isCurrentWeek] = true :
     * son déficit n'est pas compté (la semaine n'est pas encore terminée).
     */
    fun weeklyBreakdown(
        job: Job,
        entries: List<DayEntry>,
        today: LocalDate = LocalDate.now()
    ): List<WeekStats> {
        val currentWeek = weekKeyOf(today)
        return entries
            .groupBy { weekKeyOf(it.date) }
            .map { (key, weekEntries) ->
                calculateWeekStats(key, weekEntries, job).copy(isCurrentWeek = key == currentWeek)
            }
            .sortedBy { it.weekKey }
    }

    /**
     * Estimation de gains basée UNIQUEMENT sur les heures travaillées.
     * Les heures supplémentaires incluses ou extra sont appliquées selon le contrat.
     */
    fun estimateEarnings(job: Job, entries: List<DayEntry>): EarningsEstimate {
        val weeks = entries.groupBy { weekKeyOf(it.date) }.values
        
        var totalRealHours = 0.0
        var totalBrut = 0.0
        var totalCreteOrExtraPaidHours = 0.0
        var totalLivretHours = 0.0

        for (weekEntries in weeks) {
            val realHours = weekEntries.sumOf { it.totalHours }
            totalRealHours += realHours

            // 1. Heures de base
            val baseHours = minOf(realHours, job.weeklyContractHours)
            
            // 2. Heures sup incluses
            val incOtHours = if (job.includedOvertimeHours > 0.0) {
                minOf(maxOf(realHours - job.weeklyContractHours, 0.0), job.includedOvertimeHours)
            } else 0.0

            // 3. Heures sup extra (au-delà du contrat)
            val extraOtHours = maxOf(realHours - job.weeklyContractHours - job.includedOvertimeHours, 0.0)

            // Calcul du brut pour la semaine
            val baseBrut = baseHours * job.hourlyRateBrut
            val incOtBrut = incOtHours * job.hourlyRateBrut * (1.0 + (job.includedOvertimeRatePercent / 100.0))
            
            val extraOtBrut = when (job.overtimeMode) {
                OvertimeMode.CAPITALISEE, OvertimeMode.MIXTE -> {
                    // Modulation
                    val baseThreshold = job.weeklyContractHours + job.includedOvertimeHours
                    val threshold = maxOf(job.livretThreshold, baseThreshold)
                    val hoursForLivret = minOf(extraOtHours, maxOf(0.0, threshold - baseThreshold))
                    val creteHours = maxOf(0.0, realHours - threshold)
                    
                    totalLivretHours += hoursForLivret * LIVRET_RATE
                    totalCreteOrExtraPaidHours += creteHours
                    
                    creteHours * CRETE_RATE * job.hourlyRateBrut
                }
                else -> {
                    // Payées directement
                    val ot25 = minOf(extraOtHours, 8.0)
                    val ot50 = maxOf(0.0, extraOtHours - 8.0)
                    totalCreteOrExtraPaidHours += extraOtHours
                    
                    (ot25 * 1.25 + ot50 * 1.50) * job.hourlyRateBrut
                }
            }

            totalBrut += (baseBrut + incOtBrut + extraOtBrut)
        }

        return EarningsEstimate(
            heuresReelles = totalRealHours,
            brut = totalBrut,
            net = totalBrut * NET_COEFFICIENT,
            creteRealHours = totalCreteOrExtraPaidHours,
            livretHeures = totalLivretHours
        )
    }

    // ─── Calcul mensuel ──────────────────────────────────────────────────────

    fun calculateMonthStats(
        job: Job,
        entries: List<DayEntry>,
        currentMonth: YearMonth = YearMonth.now()
    ): MonthDashboardStats {
        val monthlyEntries = entries.filter { YearMonth.from(it.date) == currentMonth }
        val weeklyDetails = weeklyBreakdown(job, monthlyEntries)

        // Estimation du mois (heures travaillées ce mois)
        val mois = estimateEarnings(job, monthlyEntries)
        // Estimation cumulée sur tout le contrat (toutes les entrées)
        val contrat = estimateEarnings(job, entries)

        // Solde NET du livret (crédits des semaines > seuil − débits des semaines < seuil)
        val soldeLivretTotal = calculateTotalLivretFromEntries(job, entries)

        return MonthDashboardStats(
            salaireBrutBase = mois.heuresReelles * job.hourlyRateBrut,
            salaireBrutHeuresSupPayees = mois.creteRealHours * (CRETE_RATE - 1.0) * job.hourlyRateBrut,
            salaireNetEstime = mois.net,
            heuresAjouteesAuLivretCeMois = weeklyDetails.sumOf { it.livretCreditEquivalent },
            valeurFinanciereLivretTotal = soldeLivretTotal * job.hourlyRateBrut * NET_COEFFICIENT,
            totalHeuresReellesMois = mois.heuresReelles,
            creteRealHoursMois = mois.creteRealHours,
            soldeLivretTotal = soldeLivretTotal,
            contrat = contrat,
            weeklyDetails = weeklyDetails
        )
    }

    // ─── Recalcul du solde livret depuis toutes les entrées ──────────────────

    /**
     * Solde NET du livret depuis toutes les entrées :
     *  - semaine > seuil : crédit (heures sup ×1.25, plafonné au livret) ;
     *  - semaine < seuil : DÉBIT du manque pour égaliser ;
     *  - la semaine EN COURS (incomplète) n'est jamais débitée.
     */
    fun calculateTotalLivretFromEntries(
        job: Job,
        entries: List<DayEntry>,
        today: LocalDate = LocalDate.now()
    ): Double {
        val currentWeek = weekKeyOf(today)
        val baseThreshold = job.weeklyContractHours + job.includedOvertimeHours
        return weeklyBreakdown(job, entries, today).sumOf { w ->
            when {
                w.realHours >= baseThreshold -> w.livretCreditEquivalent      // crédit
                w.weekKey == currentWeek -> 0.0                              // semaine en cours : pas de débit
                else -> -w.deficitHours                                      // débit pour égaliser
            }
        }
    }

    // ─── Résumé contrat (onglet Stats) ───────────────────────────────────────

    fun calculateContractSummary(
        job: Job,
        entries: List<DayEntry>,
        now: LocalDateTime = LocalDateTime.now()
    ): ContractSummary {
        val today = now.toLocalDate()
        val weeks = weeklyBreakdown(job, entries)
        val est = estimateEarnings(job, entries)
        val soldeLivret = calculateTotalLivretFromEntries(job, entries, today)
        val livretValeurNet = soldeLivret * job.hourlyRateBrut * NET_COEFFICIENT

        val moyenne = if (weeks.isNotEmpty()) est.heuresReelles / weeks.size else 0.0

        // Progression + projection si les dates du contrat sont définies
        // On utilise les secondes pour que le pourcentage monte tout au long de la journée
        val start = job.startDate
        val end = job.endDate
        var progression: Float? = null
        var projectionNet: Double? = null
        if (start != null && end != null && end.isAfter(start)) {
            val startDt = start.atStartOfDay()
            val endDt = end.atStartOfDay()
            val totalSeconds = ChronoUnit.SECONDS.between(startDt, endDt).coerceAtLeast(1)
            val elapsedSeconds = ChronoUnit.SECONDS.between(startDt, now).coerceIn(0, totalSeconds)
            progression = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            // Pour la projection on garde la granularité en secondes
            if (elapsedSeconds > 0 && est.net > 0) {
                projectionNet = est.net / elapsedSeconds * totalSeconds
            }
        }

        return ContractSummary(
            start = start,
            end = end,
            joursTravailles = entries.size,
            semainesTravaillees = weeks.size,
            totalHeuresReelles = est.heuresReelles,
            moyenneHeuresParSemaine = moyenne,
            brutTotal = est.brut,
            netTotal = est.net,
            soldeLivretHeures = soldeLivret,
            livretValeurNet = livretValeurNet,
            progressionPct = progression,
            projectionNetFinContrat = projectionNet
        )
    }

    // ─── Alias de compatibilité pour les écrans existants ───────────────────

    fun calculateStats(job: Job, entries: List<DayEntry>): SalaryStats {
        val m = calculateMonthStats(job, entries)
        return SalaryStats(
            totalBrut = m.salaireBrutBase + m.salaireBrutHeuresSupPayees,
            totalNet = m.salaireNetEstime,
            normalHours = m.totalHeuresReellesMois,
            overtime25 = m.heuresAjouteesAuLivretCeMois,
            overtime50 = m.creteRealHoursMois,
            quotaUsed = m.creteRealHoursMois,
            livretHours = job.soldeLivretHeures
        )
    }
}
