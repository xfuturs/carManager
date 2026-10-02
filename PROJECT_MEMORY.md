# 🧠 Mémoire du Projet : Car Manager (Juillet - Août 2026)

Ce document est la "boîte noire" technique de l'application. Il contient l'intégralité des choix d'architecture, des fonctionnalités implémentées et des secrets de fabrication pour permettre à tout développeur (ou IA) de reprendre le projet instantanément.

---

## 🏗️ 1. Architecture Technique
L'application suit une architecture **Clean Architecture** avec une organisation **Feature-based** (par fonctionnalités).

- **`core/`** : Les fondations partagées.
    - `data/` : Base de données Room (actuellement en **v7**), Mappers, Repositories globaux.
    - `domain/` : Modèles métier (`Vehicle`, `FuelRecord`, `MaintenanceRecord`).
    - `ui/` : Thème Turquoise (Teal), composants communs (Odomètre XXL, StatCards).
    - `util/` : IA (OCR), **Générateur PDF Pro**, Formatage de dates (FR), gestion des fichiers.
- **`features/`** : Dossiers indépendants pour chaque métier.
    - `dashboard/` : Pilotage central, Statistiques et Échéances prioritaires.
    - `vehicles/` : Gestion du garage (Ajout avec catalogue marques/modèles, Modif, Suppression).
    - `fuel/` : Suivi Carburant et Recharges Électriques.
    - `maintenance/` : Historique d'entretien et **Coach d'Entretien Intelligent**.
    - `documents/` : Porte-documents sécurisé avec 8 catégories métiers (Administratif, Assurance, CT, Entretien, Carburant, Photos, Sinistres, Divers) et **recherche globale**.
    - **`auth/`** : Gestion des comptes (Connexion Google, Inscription, RGPD) et **Support Client (xfuturs.app@gmail.com)**.

---

## 🌟 2. Fonctionnalités "Magiques" (IA & UX)

### 📸 Reconnaissance de Texte (IA/OCR)
- **Technologie :** Google ML Kit (Text Recognition).
- **Usage :** Bouton "Scanner ticket" dans les formulaires de carburant/entretien.
- **Intelligence :** Extraction automatique du Prix (€), des Litres (L) ou kWh, et de la Date directement sur l'appareil.

### 📊 Dashboard 2.0 & Statistiques
- **Odomètre :** Affichage XXL du kilométrage au centre de la fiche véhicule (style tableau de bord).
- **Historique Kilométrique :** Journal de bord détaillé de tous les relevés (Manuels, Carburant, Entretien).
- **Graphiques :** Bibliothèque **Vico** pour tracer les courbes de consommation sur l'écran Stats.
- **Alertes Pulsantes :** Badges rouges animés avec un symbole "!" pour signaler les infos ou documents manquants.

### 💡 Coach d'Entretien Intelligent
- **Personnalisation :** Conseils dynamiques adaptés au type de véhicule et au kilométrage.

### 🇪🇺 Adaptabilité Européenne & Énergie
- **Multi-Devises :** Support de toutes les devises d'Europe (Euro, Livre, Franc Suisse, Złoty, Koruna, Forint, Leu, Krona, Lev, Hryvnia, Lira, Ruble, Dinar, Mark, Lek, Denar).
- **Mode Électrique :** L'interface bascule intelligemment en "kWh" et "Recharge".
- **Unités :** Gestion flexible des kilomètres (km) et des miles (mi).

---

## 💎 3. Version Premium & Cloud
- **AdMob :** Bannière flottante (masquée automatiquement pour les membres Premium).
- **Premium :** Système de facturation Google Play (v6.2.1 stable).
- **Accès Admin :** Système de Whitelist e-mail pour déverrouiller le Premium gratuitement (comptes : `admin@xfuturs.com`, `tester@xfuturs.com`).
- **Cloud Sync :** Sauvegarde automatique sur compte Google via Firebase Firestore.
- **Rapports PDF :** Génération de **Carnets d'Entretien PDF Professionnels** pour valoriser le véhicule lors de la revente (Exclusivité Premium).

---

## 🛡️ 4. RGPD & Sécurité
- **UMP :** Gestion du consentement publicitaire Google.
- **Transparence :** Écran Politique de Confidentialité.
- **Droit à l'oubli :** Suppression définitive du compte et des données dans les paramètres.

---

## 🧪 5. Qualité & Tests
- **Tests Unitaires :** Situés dans `src/test`.
    - `OcrHelperTest` : Extraction IA.
    - `GetDashboardStatsUseCaseTest` : Calculs de budget.
    - `UpdateMileageUseCaseTest` : Logique odomètre.

---

## 💾 6. Comment sauvegarder ce projet ?
1. **Drive/Cloud :** Synchroniser `C:/Users/mihai/Documents/Android/XFuturs/carManager/`.
2. **Indexation IA :** Le fichier `AGENTS.md` est le point d'entrée pour l'IA.

---
*Fichier mis à jour le 04/08/2026.*
