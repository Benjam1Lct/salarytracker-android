# SalaryTracker — Android

> **Suivez vos heures, simulez votre salaire, pilotez votre contrat.**
> Application Android pensée pour les contrats de modulation agricole, CDD, CDI, alternance et bien plus.

<p align="center">
  <img src="media/screen1.png" width="160"/>
  &nbsp;&nbsp;
  <img src="media/screen2.png" width="160"/>
  &nbsp;&nbsp;
  <img src="media/screen3.png" width="160"/>
  &nbsp;&nbsp;
  <img src="media/screen4.png" width="160"/>
</p>

<p align="center">
  <a href="https://github.com/Benjam1Lct/salarytracker-android/releases/latest">
    <img src="https://img.shields.io/badge/Version-1.2-6C63FF?style=for-the-badge&logo=android" alt="Version 1.2"/>
  </a>
  &nbsp;
  <img src="https://img.shields.io/badge/Android-8%2B-3DDC84?style=for-the-badge&logo=android" alt="Android 8+"/>
  &nbsp;
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin Compose"/>
  &nbsp;
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase"/>
</p>

---

## ✨ Fonctionnalités

| Fonctionnalité | Description |
|:---|:---|
| 🏢 **Multi-emplois** | Gérez plusieurs entreprises — CDI, CDD, Intérim, Alternance, Stage |
| ⏱️ **Saisie des heures** | Saisie manuelle, par template ou en remplissage automatique |
| 🤖 **IA Gemini** | Importez contrats et fiches de paie par photo ou PDF |
| 📱 **IA locale (ML Kit)** | Analyse on-device — 100 % hors-ligne, sans clé API |
| 📊 **Statistiques & progression** | Salaire net estimé, solde livret, **progression du contrat en temps réel**, projection de fin de contrat |
| 📅 **Saisie automatique** | Règles de remplissage auto avec rattrapage des jours manqués |
| 🔔 **Rappels quotidiens** | Notifications pour ne pas oublier de pointer |
| 🎨 **6 thèmes de couleur** | Purple, Bleu, Vert, Orange, Rouge, Rose — avec icône dynamique |
| 📦 **Widgets** | Widget écran d'accueil (petit & grand) pour consulter le solde d'un coup d'œil |

### Connexion

- ✅ E-mail & Mot de passe (Firebase Auth)
- ✅ Compte Google
- ✅ Compte Facebook
- ✅ Mode Invité / Démo (sans compte)

### Modes de gestion des heures supplémentaires

| Mode | Fonctionnement |
|:---|:---|
| **Payées** | Majoration +25 % / +50 % en paiement direct |
| **Livret / Modulation** | 35–43 h créditées au livret, au-delà payées (+50 %) — *contrats agricoles* |
| **Récupération** | Heure pour heure, génère des repos compensateurs |
| **CET** | Compte Épargne-Temps avec accumulation |
| **Mixte** | Quota livret + paiement au-delà |
| **Forfait Jours** | Pour les cadres (~218 jours/an) |

---

## 🚀 Changelog

### v1.2 — Juin 2026

- 🕐 **Progression du contrat en temps réel** — la barre de complétion monte désormais en continu tout au long de la journée (précision à la seconde), et non plus une fois par jour à minuit
- 🔄 **Synchronisation hors-ligne** — les données saisies sans connexion sont synchronisées automatiquement au retour du réseau
- 📈 **Suivi de session amélioré** — indicateurs de statut plus précis et réactifs
- 🔒 **Suppression de journée sécurisée** — correction d'un bug de suppression d'entrée
- ⚙️ **Boutons désactivés contextuellement** — évite les doubles-soumissions

### v1.1 — Connexion E-mail & Archivage

- Connexion e-mail / mot de passe
- Archivage automatique des contrats terminés
- Indicateur de statut de connexion avec reconnexion automatique
- Améliorations UI

### v1.0 — Lancement

- Première version publique
- Connexion Google & Firebase
- Livret de modulation complet
- Widgets & notifications

---

## 📲 Installation

### Option A — Télécharger l'APK

> **[⬇️ Télécharger SalaryTracker v1.2](https://github.com/Benjam1Lct/salarytracker-android/releases/download/v1.2/SalaryTracker-v1.2.apk)**

1. Autorisez l'installation depuis des sources inconnues *(Paramètres → Sécurité)*
2. Ouvrez le fichier `.apk` téléchargé
3. Installez et lancez l'app

### Option B — Compiler depuis les sources

#### Prérequis

- Android Studio Hedgehog (ou plus récent)
- JDK 11+
- Un projet Firebase configuré

#### Étapes

```bash
# 1. Cloner le dépôt
git clone https://github.com/Benjam1Lct/salarytracker-android.git
cd salarytracker-android

# 2. Placer google-services.json dans /app/
# (téléchargé depuis console.firebase.google.com)

# 3. Compiler en debug
./gradlew assembleDebug

# 4. Compiler en release (nécessite keystore.properties)
./gradlew assembleRelease
```

---

## ⚙️ Configuration Firebase

### 1. Créer le projet Firebase

1. Rendez-vous sur [console.firebase.google.com](https://console.firebase.google.com)
2. Créez une application Android avec le package `com.benjamin.salarytracker`
3. Téléchargez `google-services.json` et placez-le dans `/app/`
4. Activez dans **Authentication → Méthodes de connexion** :
   - ✅ Google
   - ✅ E-mail / Mot de passe
   - ✅ Facebook *(voir ci-dessous)*

### 2. Configurer Realtime Database

Activez **Realtime Database** et appliquez ces règles de sécurité :

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    }
  }
}
```

### 3. Configurer Facebook Login

1. Créez une app sur [developers.facebook.com](https://developers.facebook.com/apps)
2. Dans **Facebook Login → Paramètres**, ajoutez l'URI de redirection :
   ```
   https://<YOUR_PROJECT_ID>.firebaseapp.com/__/auth/handler
   ```
3. Dans Firebase Console → **Authentication → Facebook** → collez l'App ID + Secret

### 4. Clés SHA (Auth Google)

Ajoutez votre SHA-1 **et** SHA-256 dans les paramètres Firebase pour activer la connexion Google.

### 5. (Optionnel) Clé API Gemini

L'app fonctionne sans clé Gemini grâce à l'IA locale. Pour activer l'analyse cloud :

1. Obtenez une clé gratuite sur [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
2. Dans l'app → **Paramètres → Intelligence Artificielle** → collez votre clé

---

## 📱 Guide d'utilisation rapide

### Premier lancement

Un onboarding de 7 slides vous guide à travers toutes les fonctionnalités. Vous pouvez le passer à tout moment.

### Ajouter un emploi

1. Appuyez sur **+ Ajouter un emploi**
2. Choisissez le **type de contrat** (CDI, CDD, Intérim…)
3. Renseignez l'entreprise, le taux horaire et les heures hebdomadaires
4. Sélectionnez le **mode de gestion des heures sup**
5. *(Optionnel)* Importez votre contrat par photo / PDF pour un remplissage automatique

### Saisir une journée

| Méthode | Comment |
|:---|:---|
| Saisie manuelle | **+ (tableau de bord)** → Choisissez l'heure ou un template |
| Via le calendrier | Touchez un jour dans l'onglet Calendrier |
| Automatiquement | Configurez une règle dans l'onglet **Auto** |

### Importer une fiche de paie

1. Onglet **Stats → Bulletins de paie**
2. Appuyez sur **+** → Sélectionnez une photo ou un PDF
3. L'IA extrait automatiquement : brut, net, cotisations, heures payées
4. Comparez avec votre estimation mensuelle

---

## 🏗️ Architecture

```
app/
├── MainActivity.kt          # Navigation + Auth (Google, Facebook, Phone OTP)
├── SalaryViewModel.kt       # ViewModel central (MVVM)
├── FirestoreService.kt      # Couche de données Firebase Realtime DB
├── Models.kt                # Job, DayEntry, Payslip, ContractType, OvertimeMode
├── SalaryCalculator.kt      # Moteur de calcul (livret, modulation, heures sup)
├── OcrService.kt            # IA cloud via Gemini 2.5 Flash REST API
├── LocalOcrService.kt       # IA locale via ML Kit + regex
├── GeminiKeyModal.kt        # Configuration de la clé Gemini
├── LoginScreen.kt           # Authentification + Onboarding 7 slides
├── CreateJobScreen.kt       # Création / édition d'un emploi
├── DashboardScreen.kt       # Tableau de bord mensuel
├── StatsScreen.kt           # Statistiques & progression du contrat
├── SettingsScreen.kt        # Paramètres (thème, IA, rappels)
└── ...
```

### Stack technique

| Couche | Technologie |
|:---|:---|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| Backend | Firebase Realtime Database |
| Auth | Firebase Auth (Google, Facebook, Email) |
| IA Cloud | Gemini 2.5 Flash REST API |
| IA Locale | ML Kit Text Recognition (on-device) |
| Navigation | Navigation Compose |
| Widgets | AppWidgetProvider |
| Notifications | AlarmManager + BroadcastReceiver |

---

## 🔐 Sécurité

- Règles Realtime DB par `uid` (isolation totale des données utilisateurs)
- SHA-1 + SHA-256 enregistrés dans Firebase
- Clés API restreintes dans Google Cloud Console
- Minification ProGuard/R8 en release

---

## 📄 Licence

Projet privé — Benjamin. Tous droits réservés.
