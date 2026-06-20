# À faire une fois la vérification Google validée 🚀

Checklist de publication de **SalaryTracker** sur le Play Store, à suivre dès que
tu reçois l'e-mail confirmant la validation de ton identité.

---

## ✅ Déjà fait (rien à refaire)
- [x] Compte développeur Play créé + payé (25 $)
- [x] Keystore release généré (`salarytracker-release.jks`) + `keystore.properties`
- [x] AAB signé release vérifié (`CN=Lecomte Benjamin`)
- [x] Règles Firebase RTDB sécurisées **déployées**
- [x] Politique de confidentialité en ligne :
      https://salarytracker-879e4.web.app/privacy.html
- [x] Visuels icône + bannière prêts (`assets/`)
- [x] Textes de fiche prêts (`PLAY_STORE_LISTING.md`)

---

## 1. Préparer les fichiers à uploader
- [ ] Exporter en **PNG** :
  - `assets/play_icon_512.svg` → **icône 512×512**
  - `assets/play_feature_1024x500.svg` → **bannière 1024×500**
  - (conversion : https://cloudconvert.com/svg-to-png)
- [ ] Faire **2 à 8 captures d'écran** depuis l'app (dashboard, stats, ajout de
      journée, sélection de contrat…). Format téléphone, min. 2.
- [ ] Récupérer le dernier **AAB** :
      `app\build\outputs\bundle\release\app-release.aab`
      (si tu as modifié le code depuis : incrémenter `versionCode` dans
      `app/build.gradle.kts` puis `./gradlew bundleRelease`)

## 2. Créer l'application dans la Play Console
- [ ] Play Console → **Créer une application**
  - Nom : `SalaryTracker`
  - Langue par défaut : Français
  - Type : Application — Gratuite
  - Accepter les déclarations

## 3. Configurer la fiche (Présence sur le Store → Fiche principale)
- [ ] Description courte + complète → copier depuis `PLAY_STORE_LISTING.md`
- [ ] Icône 512×512 (PNG)
- [ ] Bannière 1024×500 (PNG)
- [ ] Captures d'écran
- [ ] E-mail d'assistance : benjaminlecomte37@gmail.com

## 4. Remplir le panneau "Configurer votre application"
- [ ] **Politique de confidentialité** :
      `https://salarytracker-879e4.web.app/privacy.html`
- [ ] **Accès à l'application** : fournir un compte de test (Google ou e-mail/mdp)
      pour que les évaluateurs puissent se connecter
- [ ] **Publicités** : Non
- [ ] **Classification du contenu** : remplir le questionnaire (app utilitaire)
- [ ] **Public cible** : adultes (PAS "enfants")
- [ ] **Sécurité des données (Data Safety)** — voir tableau ci-dessous

### Réponses Data Safety
| Question | Réponse |
|---|---|
| L'app collecte/transmet des données ? | Oui |
| Chiffrées en transit ? | Oui (HTTPS) |
| Suppression possible par l'utilisateur ? | Oui (Paramètres → Supprimer le compte) |
| Infos personnelles | E-mail, nom, n° téléphone, ID utilisateur |
| Infos financières | « Autres infos financières » (heures/salaire estimé) |
| Finalité | Fonctionnalité de l'app + Gestion du compte |
| Partage avec des tiers ? | Non |

## 5. Uploader l'AAB en TEST INTERNE d'abord
- [ ] Tests → **Test interne** → Créer une version → uploader `app-release.aab`
- [ ] Notes de version (voir `PLAY_STORE_LISTING.md`)
- [ ] Enregistrer → Examiner → Déployer

## 6. ⚠️ SHA-1 Google → Firebase (CRITIQUE pour Google Sign-In)
- [ ] Play Console → Test et publication → **Intégrité de l'application** →
      copier le **SHA-1 du certificat de signature de l'app**
- [ ] Firebase Console → Paramètres du projet → app Android
      `com.benjamin.salarytracker` → **Ajouter une empreinte** → coller le SHA-1
- [ ] (si proposé) re-télécharger `google-services.json` et remplacer dans le projet
- [ ] Rappel : le SHA-1 de la clé d'upload est déjà connu :
      `5C:57:5A:CB:95:8A:DE:57:3A:10:CA:C9:84:0F:B0:99:31:73:AD:60`
> Sans le SHA-1 Google, la connexion Google échoue (erreur code 10) pour les
> installs Play Store.

## 7. Test fermé obligatoire (compte personnel)
- [ ] Tests → **Test fermé** → créer une piste
- [ ] Ajouter **au moins 12 testeurs** (adresses Gmail) + partager le lien d'opt-in
- [ ] Les testeurs installent et utilisent l'app pendant **≥ 14 jours**

## 8. Passage en production
- [ ] Une fois les 14 jours écoulés → **Production** → Créer une version
      (ou promouvoir la version testée)
- [ ] Soumettre pour examen
- [ ] Attendre l'approbation Google (quelques heures à quelques jours)

---

## 🔁 Pour chaque mise à jour future
1. Incrémenter `versionCode` (et `versionName`) dans `app/build.gradle.kts`
2. `./gradlew bundleRelease`
3. Uploader le nouvel AAB dans la piste voulue
4. (optionnel) Mettre à jour `config/minVersionCode` dans la RTDB pour forcer la MAJ

## 📌 Liens utiles
- Play Console : https://play.google.com/console
- Firebase Console : https://console.firebase.google.com/project/salarytracker-879e4
- Politique de confidentialité : https://salarytracker-879e4.web.app/privacy.html
