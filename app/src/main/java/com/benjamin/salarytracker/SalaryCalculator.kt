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
        /** Heures créditées au livret avec majoration ×1.25 (35h–43h) */
        val livretCreditEquivalent: Double,
        /** Heures réelles au-delà de 43h (SANS la majoration, pour l'affichage) */
        val creteRealHours: Double,
        /** Nombre de jours de congé/absence dans la semaine */
        val leaveDays: Int = 0
    ) {
        /** Seuil attendu ajusté : 35h − 7h par jour de congé (plancher 0). */
        val expectedHours: Double get() = (WEEKLY_BASE - HOURS_PER_DAY * leaveDays).coerceAtLeast(0.0)
        val isOvertime: Boolean get() = realHours > WEEKLY_BASE
        val isCreteWeek: Boolean get() = realHours > LIVRET_CEILING
        /** Semaine sous le seuil attendu → manque d'heures à combler par le livret */
        val isUnderWeek: Boolean get() = realHours < expectedHours
        /** Heures manquantes pour atteindre le seuil (débitées du livret) */
        val deficitHours: Double get() = (expectedHours - realHours).coerceAtLeast(0.0)
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

    fun calculateWeekStats(weekKey: String, weekEntries: List<DayEntry>): WeekStats {
        val realHours = weekEntries.sumOf { it.totalHours }

        var livretCredit = 0.0
        var creteReal = 0.0

        if (realHours > WEEKLY_BASE) {
            val hoursForLivret = minOf(realHours - WEEKLY_BASE, LIVRET_CEILING - WEEKLY_BASE)
            livretCredit = hoursForLivret * LIVRET_RATE

            if (realHours > LIVRET_CEILING) {
                creteReal = realHours - LIVRET_CEILING
            }
        }

        return WeekStats(
            weekKey = weekKey,
            realHours = realHours,
            livretCreditEquivalent = livretCredit,
            creteRealHours = creteReal,
            leaveDays = weekEntries.count { it.isLeave }
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

    /** Regroupe les entrées par semaine ISO et calcule les stats de chaque semaine. */
    fun weeklyBreakdown(entries: List<DayEntry>): List<WeekStats> {
        return entries
            .groupBy { weekKeyOf(it.date) }
            .map { (key, weekEntries) -> calculateWeekStats(key, weekEntries) }
            .sortedBy { it.weekKey }
    }

    /**
     * Estimation de gains basée UNIQUEMENT sur les heures travaillées.
     * Toutes les heures comptées au taux de base ; les heures de crête (>43h/sem)
     * ajoutent +50 % en plus. Net = brut × 0.78.
     */
    fun estimateEarnings(job: Job, entries: List<DayEntry>): EarningsEstimate {
        val weeks = weeklyBreakdown(entries)
        val realHours = weeks.sumOf { it.realHours }
        val creteReal = weeks.sumOf { it.creteRealHours }
        val livret = weeks.sumOf { it.livretCreditEquivalent }

        val brut = realHours * job.hourlyRateBrut +
                   creteReal * (CRETE_RATE - 1.0) * job.hourlyRateBrut

        return EarningsEstimate(
            heuresReelles = realHours,
            brut = brut,
            net = brut * NET_COEFFICIENT,
            creteRealHours = creteReal,
            livretHeures = livret
        )
    }

    // ─── Calcul mensuel ──────────────────────────────────────────────────────

    fun calculateMonthStats(
        job: Job,
        entries: List<DayEntry>,
        currentMonth: YearMonth = YearMonth.now()
    ): MonthDashboardStats {
        val monthlyEntries = entries.filter { YearMonth.from(it.date) == currentMonth }
        val weeklyDetails = weeklyBreakdown(monthlyEntries)

        // Estimation du mois (heures travaillées ce mois)
        val mois = estimateEarnings(job, monthlyEntries)
        // Estimation cumulée sur tout le contrat (toutes les entrées)
        val contrat = estimateEarnings(job, entries)

        // Solde NET du livret (crédits des semaines >35h − débits des semaines <35h)
        val soldeLivretTotal = calculateTotalLivretFromEntries(entries)

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
     *  - semaine > 35h : crédit (heures sup ×1.25, plafonné à 43h) ;
     *  - semaine < 35h : DÉBIT du manque (35 − heures) pour égaliser à 35h ;
     *  - la semaine EN COURS (incomplète) n'est jamais débitée.
     * Le solde peut devenir négatif (heures dues / payées non travaillées).
     */
    fun calculateTotalLivretFromEntries(
        entries: List<DayEntry>,
        today: LocalDate = LocalDate.now()
    ): Double {
        val currentWeek = weekKeyOf(today)
        return weeklyBreakdown(entries).sumOf { w ->
            when {
                w.realHours >= WEEKLY_BASE -> w.livretCreditEquivalent      // crédit
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
        val weeks = weeklyBreakdown(entries)
        val est = estimateEarnings(job, entries)
        val soldeLivret = calculateTotalLivretFromEntries(entries, today)
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
