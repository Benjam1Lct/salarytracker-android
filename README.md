# 📊 SalaryTracker — Android

> Application Android de suivi des heures de travail, de simulation de salaire et de gestion du livret d'heures, pour contrats de modulation agricole et autres types de contrats.

[![Android](https://img.shields.io/badge/Platform-Android%208%2B-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blue.svg)]()
[![Firebase](https://img.shields.io/badge/Backend-Firebase%20Realtime%20DB-orange.svg)]()

---

## ✨ Fonctionnalités principales

| Fonctionnalité | Description |
|---|---|
| 🏢 **Multi-emplois** | Gérez plusieurs entreprises avec leurs contrats (CDI, CDD, Intérim, Mission, Alternance, Stage) |
| ⏱️ **Suivi des heures** | Saisissez vos journées manuellement, via templates ou en saisie automatique |
| 🤖 **IA Gemini** | Importez vos contrats et fiches de paie par photo/PDF — extraction automatique des données |
| 📱 **IA Locale (ML Kit)** | Analyse on-device sans clé API — 100% hors-ligne et gratuit |
| 📅 **Saisie automatique** | Règles de remplissage auto des journées (avec rattrapage des jours manqués) |
| 📊 **Statistiques** | Salaire net estimé, solde livret, progression du contrat, comparaison avec fiches de paie |
| 🔔 **Rappels quotidiens** | Notifications pour ne pas oublier de saisir vos heures |
| 🎨 **6 thèmes** | Purple, Bleu, Vert, Orange, Rouge, Rose — avec icône de lanceur dynamique |
| 📦 **Widgets** | Widgets écran d'accueil (petit et grand) pour voir le solde rapidement |

### Authentification
- ✅ Connexion E-mail & Mot de passe (Firebase Auth)
- ✅ Connexion Google (Firebase)
- ✅ Connexion Facebook (Firebase OAuth)
- ✅ Mode Invité / Démo (pas de compte requis)

### Gestion des heures supplémentaires
L'app supporte 6 modes de gestion :
- **Payées** — Majoration +25%/+50% directe
- **Livret / Modulation** — 35–43h créditées, au-delà payées (contrats agricoles)
- **Récupération** — Heure pour heure, génère des repos compensateurs
- **CET** — Compte Épargne-Temps avec accumulation
- **Mixte** — Quota livret + paiement au-delà
- **Forfait Jours** — Pour les cadres (~218 jours/an)

---

## 🚀 Installation & Configuration

### Prérequis
- Android Studio Hedgehog (ou plus récent)
- JDK 11+
- Compte Firebase

### 1. Cloner le projet

```bash
git clone <url-du-repo>
cd salarytracker-android
```

### 2. Configurer Firebase

1. Crée un projet sur [console.firebase.google.com](https://console.firebase.google.com)
2. Ajoute une app Android avec le package `com.benjamin.salarytracker`
3. Télécharge `google-services.json` et place-le dans `/app/`
4. Active dans **Authentication → Sign-in method** :
   - ✅ Google
   - ✅ Facebook (voir config ci-dessous)
   - ✅ Téléphone
5. Active **Realtime Database** et configure les règles de sécurité :

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

1. Crée une app sur [developers.facebook.com](https://developers.facebook.com/apps)
2. Dans **Facebook Login → Paramètres** → ajoute l'URI de redirection :
   ```
   https://<YOUR_PROJECT_ID>.firebaseapp.com/__/auth/handler
   ```
3. Dans Firebase Console → **Authentication → Facebook** → colle l'App ID + Secret Facebook

### 4. Clé de signature (SHA-1/SHA-256)

Ajoute ton SHA-1 **et** SHA-256 dans les paramètres du projet Firebase pour permettre la connexion Google et Firebase Auth.

### 5. (Optionnel) Clé API Gemini

L'app fonctionne sans clé Gemini grâce à l'IA locale. Pour activer l'analyse Gemini :

1. Obtiens une clé gratuite sur [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
2. Dans l'app → **Paramètres → Intelligence Artificielle** → Colle ta clé

### 6. Compiler et lancer

```bash
./gradlew assembleDebug
# ou via Android Studio : Run > Run 'app'
```

---

## 📱 Guide d'utilisation

### 1. Premier lancement
Un onboarding de 7 slides vous guide à travers toutes les fonctionnalités. Vous pouvez le passer à tout moment.

### 2. Ajouter votre premier emploi
1. Appuyez sur **+ Ajouter un emploi** dans l'écran de sélection
2. Choisissez le **type de contrat** (CDI, CDD, Intérim…)
3. Renseignez l'entreprise, le taux horaire, les heures hebdomadaires
4. Choisissez le **mode de gestion des heures sup** (Payées, Livret, CET…)
5. **Optionnel** : Importez votre contrat (photo/PDF) pour remplir automatiquement via IA

### 3. Saisir une journée
- **Bouton + (tableau de bord)** → Saisie manuelle ou via template
- **Calendrier** → Touchez un jour pour l'ajouter
- Ou configurez la **saisie automatique** pour les journées récurrentes

### 4. Créer un template d'horaire
1. **Paramètres → Gérer les templates**
2. Définissez heure de début, de fin, et les pauses
3. Le template peut être utilisé dans la saisie manuelle ou les règles auto

### 5. Importer une fiche de paie
1. Onglet **Stats → Bulletins de paie**
2. Appuyez sur **+** → Sélectionnez une photo ou PDF
3. L'IA extrait automatiquement : brut, net, cotisations, heures payées
4. Comparez avec votre estimation mensuelle

### 6. Saisie automatique
1. Onglet **Auto** → Ajouter une règle
2. Choisissez un template et les jours de la semaine
3. Mode : **Continue** (jusqu'à désactivation) ou **Période** (dates définies)
4. L'app rattrape automatiquement les journées manquées à chaque ouverture

---

## 🔐 Sécurité Firebase (Production)

Voir le [Guide de Sécurité Firebase](FIREBASE_SECURITY.md) pour la checklist complète de mise en production.

**Résumé rapide :**
- Configurez les règles Realtime DB (voir section installation)
- Enregistrez SHA-1 + SHA-256 dans Firebase
- Activez App Check (Play Integrity)
- Restreignez les clés API dans Google Cloud Console
- Activez la minification ProGuard/R8 en release

---

## 🏗️ Architecture technique

```
app/
├── MainActivity.kt          # Navigation + Auth handlers (Google, Facebook, Phone OTP)
├── SalaryViewModel.kt       # ViewModel central (MVVM)
├── FirestoreService.kt      # Couche de données Firebase Realtime DB
├── Models.kt                # Job, DayEntry, Payslip, ContractType, OvertimeMode
├── SalaryCalculator.kt      # Moteur de calcul (livret, modulation, hors sup)
├── OcrService.kt            # Analyse IA via Gemini 2.5 Flash REST API
├── LocalOcrService.kt       # Analyse IA locale via ML Kit + regex
├── GeminiKeyModal.kt        # Modal de configuration de la clé Gemini
├── LoginScreen.kt           # Écran d'authentification + Onboarding 7 slides
├── CreateJobScreen.kt       # Création / édition d'un emploi + types de contrats
├── JobSelectionScreen.kt    # Sélection avec groupement par entreprise
├── DashboardScreen.kt       # Tableau de bord mensuel
├── SettingsScreen.kt        # Paramètres (thème, clé API, rappels)
└── ...
```

### Stack technique

| Couche | Technologie |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| Backend | Firebase Realtime Database |
| Auth | Firebase Authentication (Google, Facebook, Phone) |
| IA Cloud | Gemini 2.5 Flash REST API |
| IA Locale | ML Kit Text Recognition (on-device) |
| Navigation | Navigation Compose |
| Widgets | AppWidgetProvider |
| Notifications | AlarmManager + BroadcastReceiver |

---

## 🧪 Tests

```bash
# Tests unitaires
./gradlew test

# Tests instrumentation
./gradlew connectedAndroidTest
```



---

## 📦 Build de production

1. Configure `keystore.properties` à la racine :
```properties
keyAlias=your_key_alias
keyPassword=your_key_password
storeFile=/path/to/your.keystore
storePassword=your_store_password
```

2. Active la minification dans `build.gradle.kts` (release)

3. Build :
```bash
./gradlew bundleRelease   # AAB pour Play Store
./gradlew assembleRelease  # APK
```

---

## 📄 Licence

Projet privé — Benjamin. Tous droits réservés.
