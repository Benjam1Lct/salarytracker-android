/**
 * Backend sécurisé SalaryTracker (Firebase Cloud Functions, gen 2).
 *
 *  - aiGenerate     : proxy Gemini. Vérifie l'abonnement + le rate-limit (jour/mois)
 *                     puis appelle Gemini avec la clé serveur (jamais exposée à l'app).
 *  - verifyPurchase : vérifie un achat Google Play et écrit l'entitlement en RTDB.
 *  - playRtdn       : reçoit les notifications temps réel de Google Play (renouvellement,
 *                     annulation, expiration…) et met l'entitlement à jour.
 *
 * Région : europe-west1 (comme la Realtime Database).
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const { google } = require("googleapis");

admin.initializeApp();
const db = admin.database();

const REGION = "europe-west1";
const PACKAGE_NAME = "com.benjamin.salarytracker";

// Quotas pour les abonnés (anti-spam).
const DAILY_LIMIT = 10;
const MONTHLY_LIMIT = 100;

// Clé Gemini stockée comme secret (jamais dans le code/app) :
//   firebase functions:secrets:set GEMINI_KEY
const GEMINI_KEY = defineSecret("GEMINI_KEY");

// ─────────────────────────────────────────────────────────────────────────────
// Utilitaires
// ─────────────────────────────────────────────────────────────────────────────

function todayKey() {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD (UTC)
}
function monthKey() {
  return new Date().toISOString().slice(0, 7); // YYYY-MM
}

/** Vrai si l'utilisateur a un abonnement actif et non expiré. */
async function isSubscribed(uid) {
  const snap = await db.ref(`subscriptions/${uid}`).get();
  if (!snap.exists()) return false;
  const sub = snap.val();
  const active = sub.active === true || sub.active === "true";
  const notExpired = !sub.expiryTimeMillis || sub.expiryTimeMillis > Date.now();
  return active && notExpired;
}

/**
 * Incrémente atomiquement les compteurs jour + mois et renvoie une erreur
 * si l'un des quotas est dépassé. Réservation faite AVANT l'appel Gemini.
 */
async function consumeQuota(uid) {
  const dRef = db.ref(`aiUsage/${uid}/daily/${todayKey()}`);
  const mRef = db.ref(`aiUsage/${uid}/monthly/${monthKey()}`);

  const dRes = await dRef.transaction((c) => (c || 0) + 1);
  const mRes = await mRef.transaction((c) => (c || 0) + 1);

  const daily = dRes.snapshot.val() || 0;
  const monthly = mRes.snapshot.val() || 0;

  if (daily > DAILY_LIMIT || monthly > MONTHLY_LIMIT) {
    // Annule la réservation : on rembourse le compteur dépassé.
    await dRef.transaction((c) => Math.max(0, (c || 1) - 1));
    await mRef.transaction((c) => Math.max(0, (c || 1) - 1));
    const which = daily > DAILY_LIMIT ? "quotidien" : "mensuel";
    throw new HttpsError(
      "resource-exhausted",
      `Quota ${which} atteint. Réessayez plus tard.`
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Proxy Gemini (réservé aux abonnés)
// ─────────────────────────────────────────────────────────────────────────────

exports.aiGenerate = onCall(
  { region: REGION, secrets: [GEMINI_KEY] },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Connexion requise.");

    if (!(await isSubscribed(uid))) {
      throw new HttpsError("permission-denied", "Abonnement requis.");
    }

    // L'app envoie le tableau "contents" Gemini (texte + images base64) en JSON string.
    let contents;
    try {
      contents = JSON.parse((request.data && request.data.contentsJson) || "");
    } catch (_) {
      throw new HttpsError("invalid-argument", "contentsJson invalide.");
    }
    if (!Array.isArray(contents)) {
      throw new HttpsError("invalid-argument", "contentsJson doit être un tableau.");
    }

    // Réserve le quota AVANT l'appel (évite le spam même si Gemini est lent).
    await consumeQuota(uid);

    const model = "gemini-2.5-flash";
    const url =
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_KEY.value()}`;

    const resp = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents,
        generationConfig: { response_mime_type: "application/json" },
      }),
    });

    if (!resp.ok) {
      const errText = await resp.text();
      console.error("Gemini error", resp.status, errText);
      throw new HttpsError("internal", "Erreur du service d'analyse.");
    }

    const json = await resp.json();
    const text =
      json.candidates?.[0]?.content?.parts?.[0]?.text ?? "";

    return { text };
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// 2. Vérification d'un achat Google Play → écrit l'entitlement
// ─────────────────────────────────────────────────────────────────────────────

const androidPublisher = google.androidpublisher("v3");

async function authForPlay() {
  // Utilise le compte de service des Cloud Functions (doit être autorisé dans
  // Play Console → Utilisateurs et autorisations + API liée).
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return auth.getClient();
}

/** Met à jour users/{uid}/subscription à partir d'un purchaseToken vérifié. */
async function applySubscriptionState(uid, productId, purchaseToken) {
  const authClient = await authForPlay();
  const res = await androidPublisher.purchases.subscriptionsv2.get({
    packageName: PACKAGE_NAME,
    token: purchaseToken,
    auth: authClient,
  });

  const data = res.data;
  const state = data.subscriptionState; // SUBSCRIPTION_STATE_ACTIVE, _CANCELED, _EXPIRED…
  const active =
    state === "SUBSCRIPTION_STATE_ACTIVE" ||
    state === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" ||
    state === "SUBSCRIPTION_STATE_CANCELED"; // canceled mais encore valide jusqu'à l'expiry

  const lineItem = (data.lineItems && data.lineItems[0]) || {};
  const expiryTimeMillis = lineItem.expiryTime
    ? new Date(lineItem.expiryTime).getTime()
    : 0;
  const stillValid = expiryTimeMillis > Date.now();

  await db.ref(`subscriptions/${uid}`).set({
    active: active && stillValid,
    productId: productId || null,
    purchaseToken,
    expiryTimeMillis,
    state,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  // Mapping inverse pour les notifications temps réel (token → uid).
  await db.ref(`playTokens/${purchaseToken}`).set({ uid, productId: productId || null });

  return active && stillValid;
}

exports.verifyPurchase = onCall({ region: REGION }, async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Connexion requise.");

  const { purchaseToken, productId } = request.data || {};
  if (!purchaseToken) {
    throw new HttpsError("invalid-argument", "purchaseToken manquant.");
  }

  try {
    const active = await applySubscriptionState(uid, productId, purchaseToken);
    return { active };
  } catch (e) {
    console.error("verifyPurchase failed", e);
    throw new HttpsError("internal", "Vérification de l'achat impossible.");
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. Notifications temps réel Google Play (RTDN) via Pub/Sub
// ─────────────────────────────────────────────────────────────────────────────

exports.playRtdn = onMessagePublished(
  { region: REGION, topic: "play-subscriptions" },
  async (event) => {
    try {
      const raw = Buffer.from(event.data.message.data, "base64").toString();
      const notif = JSON.parse(raw);
      const sub = notif.subscriptionNotification;
      if (!sub) return; // ignore les test/voided notifications ici

      const purchaseToken = sub.purchaseToken;
      const productId = sub.subscriptionId;
      const map = await db.ref(`playTokens/${purchaseToken}`).get();
      if (!map.exists()) {
        console.warn("Token inconnu (pas de uid mappé)", purchaseToken);
        return;
      }
      const uid = map.val().uid;
      await applySubscriptionState(uid, productId, purchaseToken);
    } catch (e) {
      console.error("playRtdn error", e);
    }
  }
);
