package com.benjamin.salarytracker

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SalaryApp : Application() {

    companion object {
        lateinit var instance: SalaryApp
            private set

        const val DB_URL =
            "https://salarytracker-879e4-default-rtdb.europe-west1.firebasedatabase.app/"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // La persistance disque doit être activée AVANT toute autre utilisation
        // de la base. Elle permet : lecture hors-ligne depuis le cache, mise en
        // file d'attente des écritures, et resynchronisation auto à la reconnexion.
        try {
            FirebaseDatabase.getInstance(DB_URL).setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Déjà initialisée (ex : redémarrage de process) — sans effet.
        }
    }
}
