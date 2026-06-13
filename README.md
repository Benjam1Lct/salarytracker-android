# SalaryTracker ⏰

**SalaryTracker** est une application Android moderne conçue pour suivre vos heures de travail, estimer votre salaire mensuel (brut et net) en temps réel, et comparer ces estimations avec vos bulletins de paie réels pour détecter tout écart ou sous-paiement.

L'application intègre l'Intelligence Artificielle (Gemini API) pour faciliter la saisie automatique à partir de notes, le tout dans une interface fluide et animée.

---

## 📸 Aperçu de l'application

<div align="center">
  <img src="media/app_dashboard.png" width="30%" alt="Tableau de bord"/>
  &nbsp;&nbsp;
  <img src="media/app_history.png" width="30%" alt="Historique"/>
  &nbsp;&nbsp;
  <img src="media/app_auto.png" width="30%" alt="Automatisation"/>
</div>

<br/>

📊 **Tableau de bord** — Hub central avec progression du contrat en cours, jauge animée des objectifs de gains mensuels et actions rapides de saisie.

📅 **Historique Interactif** — Calendrier mensuel pour visualiser les journées travaillées et congés, avec résumé des saisies et raccourcis d'édition rapides.

🤖 **Saisie Automatique** — Règles intelligentes pour renseigner automatiquement les heures récurrentes sur les journées cibles, sans action manuelle.

---

<div align="center">
  <img src="media/app_stats_hours.png" width="30%" alt="Statistiques horaires"/>
  &nbsp;&nbsp;
  <img src="media/app_stats_salary.png" width="30%" alt="Statistiques financières"/>
  &nbsp;&nbsp;
  <img src="media/app_settings.png" width="30%" alt="Paramètres"/>
</div>

<br/>

📈 **Statistiques Horaires** — Total des heures réelles travaillées, heures supplémentaires (crête) et solde du livret avec graphe évolutif sur plusieurs mois.

💵 **Statistiques Financières** — Suivi des gains nets estimés et comparaison directe avec les bulletins de paie importés pour détecter tout sous-paiement.

⚙️ **Paramètres & IA** — Préférences utilisateur, rappels quotidiens configurables et clé Gemini pour activer l'import intelligent de fiches de paie.

---

<div align="center">
  <img src="media/app_selection.png" width="30%" alt="Sélection du contrat"/>
</div>

<br/>

💼 **Multi-Contrats** — Gestion de plusieurs contrats cloisonnés avec changement de contexte à la volée depuis n'importe quel écran. Les contrats archivés restent accessibles séparément.

---

## 🛠️ Stack Technique

*   **Langage** : Kotlin
*   **UI Framework** : Jetpack Compose (Material 3) avec animations fluides.
*   **Base de données & Auth** : Firebase (Authentication & Realtime Database) — synchronisation multi-appareils sécurisée.
*   **Intelligence Artificielle** : Google Gemini API pour l'OCR de fiches de paie et la structuration des saisies libres.
*   **Architecture** : MVVM avec gestion d'état réactive (StateFlow/Lifecycle).

---

## 📦 Compilation & Installation locale

### Prérequis
- Java JDK 17+ (par ex. via Android Studio JBR)
- Android SDK (API 26+)

### Compiler l'APK
```bash
./gradlew assembleRelease
```
L'APK généré sera disponible sous :
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 Licence

Ce projet est distribué sous licence libre d'utilisation à des fins de test et de formation.
