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
  <img src="media/app_stats_hours.png" width="30%" alt="Statistiques"/>
</div>

<br/>

**📊 Tableau de bord** — Votre hub central : progression du contrat en cours, jauge animée de vos objectifs de gains mensuels, et actions rapides de saisie. **📅 Historique** — Calendrier mensuel interactif pour visualiser vos journées et congés avec raccourcis d'édition. **📈 Statistiques** — Heures réelles, heures supplémentaires (crête), solde du livret et évolution graphique sur plusieurs mois.

---

<div align="center">
  <img src="media/app_auto.png" width="30%" alt="Automatisation"/>
  &nbsp;&nbsp;
  <img src="media/app_settings.png" width="30%" alt="Paramètres"/>
  &nbsp;&nbsp;
  <img src="media/app_selection.png" width="30%" alt="Sélection du contrat"/>
</div>

<br/>

**🤖 Saisie automatique** — Configurez des règles intelligentes pour renseigner vos heures récurrentes automatiquement sans action manuelle. **⚙️ Paramètres & IA** — Gérez vos préférences, configurez les rappels quotidiens et activez l'OCR Gemini pour importer vos fiches de paie. **💼 Multi-contrats** — Gérez plusieurs contrats cloisonnés et changez de contexte d'un seul tap depuis n'importe quel écran.

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
