# Abonnement SalaryTracker Pro — configuration

Le code (app + backend) est prêt. Voici les étapes **manuelles** côté Firebase et
Play Console pour activer l'abonnement sécurisé.

## Architecture (rappel)
- **App** → Google Play Billing pour l'achat → Cloud Function `verifyPurchase` qui
  vérifie l'achat et écrit l'entitlement dans la RTDB (`subscriptions/{uid}`).
- **App abonnée** → appelle la Cloud Function `aiGenerate` (au lieu de Gemini direct).
  Le serveur détient la clé Gemini, vérifie l'abonnement + le quota (10/jour, 100/mois).
- Les utilisateurs **non abonnés** gardent : clé Gemini perso **ou** IA locale (ML Kit).

---

## 1. Backend Firebase (Cloud Functions)

```bash
cd functions
npm install
cd ..

# Clé Gemini stockée comme secret (jamais dans l'app) :
firebase functions:secrets:set GEMINI_KEY
# (colle ta clé Gemini quand demandé)

# Déploie functions + règles RTDB :
firebase deploy --only functions,database
```

> Nécessite le plan **Blaze** (déjà activé). Le déploiement crée 3 fonctions :
> `aiGenerate`, `verifyPurchase`, `playRtdn` (région europe-west1).

## 2. Autoriser la vérification des achats (Play Developer API)

Pour que `verifyPurchase` puisse interroger Google Play :

1. **Google Cloud Console** → API & Services → active **"Google Play Android Developer API"** pour le projet `salarytracker-879e4`.
2. **Play Console** → Configuration → **Accès aux API** → associe le projet Google Cloud `salarytracker-879e4`.
3. Toujours dans Play Console → **Utilisateurs et autorisations** → invite le **compte de service** des Cloud Functions
   (`salarytracker-879e4@appspot.gserviceaccount.com`) avec l'autorisation
   **« Afficher les données financières / Gérer les commandes et les abonnements »**.

## 3. Créer le produit d'abonnement

**Play Console** → ton app → **Monétiser → Abonnements** → Créer un abonnement :
- **ID produit** : `salarytracker_pro` (doit correspondre à `BillingManager.SUBSCRIPTION_ID`)
- **Forfait de base** : période **mensuelle**, renouvellement automatique
- Définis le **prix** (ex. 2,99 €/mois). Le prix s'affiche automatiquement dans l'app.
- Active le produit.

## 4. Notifications temps réel (renouvellement / annulation)

Pour garder l'abonnement à jour automatiquement :

1. **Google Cloud Console** → Pub/Sub → crée un **topic** nommé `play-subscriptions`.
2. Donne au compte `google-play-developer-notifications@system.gserviceaccount.com`
   le rôle **Pub/Sub Publisher** sur ce topic.
3. **Play Console** → Monétiser → **Configuration de la monétisation** →
   **Notifications développeur en temps réel** → colle le nom complet du topic :
   `projects/salarytracker-879e4/topics/play-subscriptions`.

> La fonction `playRtdn` est déjà abonnée à ce topic et met à jour l'entitlement
> à chaque événement (renouvellement, annulation, expiration, remboursement).

## 5. Tester

1. Play Console → Configuration → **Test des licences** → ajoute ton adresse Gmail
   comme testeur (achats sans débit réel).
2. Installe l'app via une piste de **test interne** (l'achat ne marche pas en debug local).
3. Settings → **SalaryTracker Pro** → S'abonner → le badge passe à « Vous êtes Pro ✓ ».
4. Lance une analyse IA (import contrat/bulletin) sans clé perso : elle doit passer
   par le backend.

---

## Quotas (modifiables dans `functions/index.js`)
```js
const DAILY_LIMIT = 10;
const MONTHLY_LIMIT = 100;
```
Après modif : `firebase deploy --only functions`.

## Fichiers concernés
- Backend : `functions/index.js`, `functions/package.json`
- Règles : `database.rules.json` (nœuds `subscriptions`, `aiUsage`, `playTokens` en lecture seule client)
- App : `BillingManager.kt`, `SubscriptionScreen.kt`, `SalaryViewModel.kt` (état + billing),
  `OcrService.kt` (route backend), `SettingsScreen.kt` (entrée Pro)
