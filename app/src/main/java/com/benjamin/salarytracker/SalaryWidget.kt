package com.benjamin.salarytracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widgets écran d'accueil. Comme un widget ne peut pas lire la Realtime DB,
 * l'app enregistre un snapshot dans les SharedPreferences (via [save]) et les
 * widgets lisent cette valeur. [save] est appelé à l'ouverture / changement de données.
 */
object SalaryWidget {

    private const val PREFS = "salary_widget"

    data class Snapshot(
        val job: String,
        val net: Double,
        val target: Double,
        val daysLeft: Int   // -1 si pas de date de fin
    )

    /** Enregistre les données et rafraîchit les widgets posés. */
    fun save(context: Context, job: String, net: Double, target: Double, daysLeft: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("job", job)
            .putFloat("net", net.toFloat())
            .putFloat("target", target.toFloat())
            .putInt("daysLeft", daysLeft)
            .apply()
        updateAll(context)
    }

    private fun read(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            job = p.getString("job", "—") ?: "—",
            net = p.getFloat("net", 0f).toDouble(),
            target = p.getFloat("target", 3000f).toDouble(),
            daysLeft = p.getInt("daysLeft", -1)
        )
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.getAppWidgetIds(ComponentName(context, SalaryWidgetSmall::class.java)).forEach {
            mgr.updateAppWidget(it, buildSmall(context))
        }
        mgr.getAppWidgetIds(ComponentName(context, SalaryWidgetLarge::class.java)).forEach {
            mgr.updateAppWidget(it, buildLarge(context))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildSmall(context: Context): RemoteViews {
        val d = read(context)
        return RemoteViews(context.packageName, R.layout.widget_small).apply {
            setTextViewText(R.id.w_net, fmtMoney(d.net))
            setTextViewText(R.id.w_target, "/ ${fmtMoney(d.target)}")
            setTextViewText(R.id.w_job, d.job)
            setOnClickPendingIntent(R.id.w_root, openAppIntent(context))
        }
    }

    fun buildLarge(context: Context): RemoteViews {
        val d = read(context)
        val progress = if (d.target > 0) ((d.net / d.target) * 100).toInt().coerceIn(0, 100) else 0
        return RemoteViews(context.packageName, R.layout.widget_large).apply {
            setTextViewText(R.id.w_net, fmtMoney(d.net))
            setTextViewText(R.id.w_target, "Objectif ${fmtMoney(d.target)}")
            setTextViewText(R.id.w_job, d.job)
            setProgressBar(R.id.w_progress, 100, progress, false)
            if (d.daysLeft >= 0) {
                setTextViewText(R.id.w_days, d.daysLeft.toString())
                setTextViewText(R.id.w_days_label, "jours restants")
            } else {
                setTextViewText(R.id.w_days, "∞")
                setTextViewText(R.id.w_days_label, "CDI")
            }
            setOnClickPendingIntent(R.id.w_root, openAppIntent(context))
        }
    }
}

class SalaryWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, SalaryWidget.buildSmall(context)) }
    }
}

class SalaryWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, SalaryWidget.buildLarge(context)) }
    }
}
