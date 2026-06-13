# SalaryTracker ⏰

**SalaryTracker** est une application Android moderne conçue pour suivre vos heures de travail, estimer votre salaire mensuel (brut et net) en temps réel, et comparer ces estimations avec vos bulletins de paie réels pour détecter tout écart ou sous-paiement.

L'application intègre l'Intelligence Artificielle (Gemini API) pour faciliter la saisie automatique à partir de notes, le tout dans une interface fluide et animée.

---

## 📸 Aperçu de l'application

| | |
|---|---|
| <img src="media/app_dashboard.png" width="240"/> | **📊 Tableau de bord**<br/><br/>Votre hub central affichant la progression de votre contrat (jours, semaines et heures travaillées), une jauge animée de vos objectifs de gains mensuels et des actions rapides de saisie. |
| **📅 Historique Interactif**<br/><br/>Calendrier mensuel interactif pour visualiser vos journées travaillées et vos congés. Naviguez de mois en mois et accédez à un résumé des saisies avec des raccourcis d'édition rapides. | <img src="media/app_history.png" width="240"/> |
| <img src="media/app_auto.png" width="240"/> | **🤖 Saisie Automatique**<br/><br/>Configurez des règles intelligentes pour renseigner automatiquement vos heures récurrentes sur les journées cibles — sans aucune action manuelle répétitive. |
| **📈 Statistiques Horaires**<br/><br/>Analysez votre répartition horaire sur plusieurs mois. Total des heures réelles, heures supplémentaires (crête) accumulées et solde du livret sous forme de graphe évolutif. | <img src="media/app_stats_hours.png" width="240"/> |
| <img src="media/app_stats_salary.png" width="240"/> | **💵 Statistiques Financières**<br/><br/>Suivez vos gains nets cumulés estimés mois par mois et comparez en un clic vos estimations avec vos bulletins de paie importés pour détecter tout sous-paiement. |
| **⚙️ Paramètres & Intelligence Artificielle**<br/><br/>Gérez vos préférences, configurez les rappels de saisie quotidiens et renseignez votre clé Gemini pour activer l'import intelligent de notes et de fiches de paie. | <img src="media/app_settings.png" width="240"/> |
| <img src="media/app_selection.png" width="240"/> | **💼 Sélection Multi-Contrats**<br/><br/>Gérez plusieurs contrats cloisonnés. Changez de contexte à la volée depuis n'importe quel écran du dock. Les contrats archivés restent accessibles séparément. |

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
