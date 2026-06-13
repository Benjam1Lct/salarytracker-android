# SalaryTracker ⏰

**SalaryTracker** est une application Android moderne et performante conçue pour suivre vos heures de travail, estimer votre salaire mensuel (brut et net) en temps réel, et comparer ces estimations avec vos bulletins de paie réels pour détecter tout écart ou sous-paiement.

L'application intègre des technologies d'Intelligence Artificielle (Gemini API) pour faciliter la saisie automatique à partir de notes ou d'importation de documents, le tout enveloppé dans une interface utilisateur fluide, animée et haut de gamme.

---

## 📸 Aperçu & Fonctionnalités

---

### 📊 Tableau de Bord

<img src="media/app_dashboard.png" align="right" width="260" alt="Tableau de bord"/>

Votre hub principal affichant la progression de votre contrat en cours — jours, semaines et heures réelles travaillées.

Une jauge animée haut de gamme visualise vos objectifs de gains mensuels en temps réel. Les actions rapides d'ajout permettent de saisir une journée en quelques secondes, directement depuis l'accueil.

<br clear="right"/>

---

### 📅 Historique Interactif

<img src="media/app_history.png" align="left" width="260" alt="Historique calendrier"/>

Visualisez vos journées travaillées et vos congés sous forme de grille mensuelle interactive. Naviguez d'un mois à l'autre et consultez un résumé des saisies avec raccourcis d'édition rapides directement depuis le calendrier.

<br clear="left"/>

---

### 🤖 Saisie Automatique & Règles

<img src="media/app_auto.png" align="right" width="260" alt="Automatisation"/>

Configurez des règles intelligentes pour renseigner automatiquement vos heures récurrentes sur les journées cibles — sans aucune action manuelle répétitive.

Idéal pour les contrats réguliers avec des horaires fixes.

<br clear="right"/>

---

### 📈 Statistiques Horaires

<img src="media/app_stats_hours.png" align="left" width="260" alt="Analyses horaires"/>

Analysez votre répartition horaire au cours des derniers mois. Consultez le total de vos heures réelles travaillées, vos heures supplémentaires (crête) accumulées et le solde courant de votre livret d'heures sous forme de graphe.

<br clear="left"/>

---

### 💵 Statistiques Financières

<img src="media/app_stats_salary.png" align="right" width="260" alt="Analyses salaire"/>

Suivez vos gains nets cumulés estimés. L'application évalue vos revenus à venir et vous permet de comparer en un clic vos estimations avec vos bulletins de paie importés.

<br clear="right"/>

---

### ⚙️ Paramètres & Intelligence Artificielle

<img src="media/app_settings.png" align="left" width="260" alt="Paramètres"/>

Gérez vos préférences utilisateur, configurez les rappels de notifications quotidiens et renseignez votre clé API Gemini pour activer l'import intelligent de notes manuscrites et de fiches de paie.

<br clear="left"/>

---

### 💼 Sélection Multi-Contrats

<img src="media/app_selection.png" align="right" width="260" alt="Choix du contrat"/>

Gérez l'ensemble de vos contrats actifs de façon cloisonnée. Changez de contrat à la volée depuis n'importe quel écran du dock, et créez de nouveaux jobs facilement en quelques secondes.

<br clear="right"/>

---

## 🛠️ Stack Technique

*   **Langage** : Kotlin
*   **UI Framework** : Jetpack Compose (Material 3) avec animations fluides de transition et d'onboarding.
*   **Base de données & Auth** : Firebase (Authentication & Realtime Database) pour une synchronisation multi-appareils fluide et sécurisée.
*   **Intelligence Artificielle** : Google Gemini API (via SDK Client officiel) pour l'OCR de fiches de paie et la structuration des saisies libres.
*   **Architecture** : MVVM (Model-View-ViewModel) robuste avec gestion d'état réactive (StateFlow/Lifecycle).

---

## 📦 Compilation & Installation locale

### Prérequis
- Java JDK 17+ (par ex. configuré via Android Studio JBR)
- Android SDK (API 26+)

### Compiler l'APK de Test (Optimisé)
Vous pouvez générer une version Release optimisée (minifiée par R8/Proguard pour éliminer tout lag d'animation) et signée avec la clé debug :

```bash
./gradlew assembleRelease
```
L'APK généré sera disponible sous :
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 Licence

Ce projet est distribué sous licence libre d'utilisation à des fins de test et de formation.
