# Politique de confidentialité — SalaryTracker

_Dernière mise à jour : 20 juin 2026_

SalaryTracker (« l'application ») est édité par Benjamin Lecomte. Cette politique
explique quelles données sont collectées, pourquoi, et comment elles sont protégées.

## 1. Données collectées

Selon le mode de connexion choisi :

- **Compte Google / e-mail / téléphone** (via Firebase Authentication) : adresse
  e-mail, nom d'affichage, numéro de téléphone et identifiant de compte. Utilisés
  pour vous identifier et synchroniser vos données entre appareils.
- **Données de suivi du travail** que vous saisissez : heures travaillées, dates,
  contrats, taux horaire, estimations de salaire, bulletins importés.
- **Clé API Gemini** (facultative) : si vous en fournissez une pour la
  reconnaissance de documents, elle est stockée sur votre appareil et dans votre
  espace privé.
- **Compte local** : si vous choisissez ce mode, **aucune donnée n'est envoyée à
  nos serveurs** ; tout reste sur l'appareil.

## 2. Utilisation des données

Les données servent uniquement à fournir les fonctionnalités de l'application
(suivi des heures, estimation du salaire, synchronisation). **Nous ne vendons ni
ne partageons vos données à des fins publicitaires.**

## 3. Stockage et sécurité

- Les données des comptes en ligne sont stockées dans **Firebase Realtime
  Database** (Google Cloud, région europe-west1), accessibles uniquement par le
  propriétaire du compte authentifié.
- L'analyse de documents (OCR) est effectuée **sur l'appareil** (ML Kit). Si vous
  fournissez une clé Gemini, le texte est envoyé à l'API Google Gemini pour
  analyse.

## 4. Partage avec des tiers

L'application utilise les services Google suivants : Firebase Authentication,
Firebase Realtime Database, Google Sign-In, et (en option) l'API Gemini. Consultez
la politique de confidentialité de Google : https://policies.google.com/privacy

## 5. Conservation et suppression

Vous pouvez à tout moment supprimer vos journées, votre compte et toutes vos
données depuis **Paramètres → Supprimer le compte**. Cette action efface
définitivement vos données de nos serveurs.

## 6. Vos droits

Conformément au RGPD, vous disposez d'un droit d'accès, de rectification et de
suppression de vos données. Contact : benjaminlecomte37@gmail.com

## 7. Contact

Pour toute question : benjaminlecomte37@gmail.com
