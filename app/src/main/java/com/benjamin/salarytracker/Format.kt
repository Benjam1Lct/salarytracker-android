package com.benjamin.salarytracker

import java.util.Locale

/**
 * Formatage des montants/heures : jusqu'à **2 décimales**, jamais d'arrondi à
 * l'entier — on garde les décimales si elles existent, sinon on les masque.
 * Format français (séparateur de milliers espace, décimale virgule).
 */

private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

/** Argent : "1 234 €" si entier, "1 234,56 €" sinon. */
fun fmtMoney(v: Double): String {
    val r = round2(v)
    return if (r == Math.floor(r)) String.format(Locale.FRANCE, "%,d €", r.toLong())
    else String.format(Locale.FRANCE, "%,.2f €", r)
}

/** Argent sans symbole. */
fun fmtMoneyNum(v: Double): String {
    val r = round2(v)
    return if (r == Math.floor(r)) String.format(Locale.FRANCE, "%,d", r.toLong())
    else String.format(Locale.FRANCE, "%,.2f", r)
}

/** Heures : "47 h" si entier, "47,25 h" sinon. */
fun fmtHours(v: Double): String {
    val r = round2(v)
    return if (r == Math.floor(r)) String.format(Locale.FRANCE, "%d h", r.toLong())
    else String.format(Locale.FRANCE, "%.2f h", r)
}

/** Heures avec signe + devant (pour le livret). */
fun fmtHoursSigned(v: Double): String = (if (v >= 0) "+" else "") + fmtHours(v)
