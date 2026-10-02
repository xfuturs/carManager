# 🚗 Car Manager (v1.0 - 2026)

Application Android professionnelle de gestion de flotte personnelle. Suivez vos consommations, vos entretiens et vos documents administratifs en toute simplicité, que vous rouliez en thermique, électrique ou hybride.

## ✨ Fonctionnalités Clés

- **Pilotage Central :** Tableau de bord interactif avec odomètre XXL et indicateurs d'urgence.
- **IA Scan (OCR) :** Reconnaissance automatique de texte via Google ML Kit pour saisir vos reçus de carburant et factures d'entretien.
- **Gestion Multi-Énergie :** Support complet des véhicules Électriques (kWh), Hybrides et Thermiques (L).
- **Statistiques Avancées :** Graphiques de consommation dynamique (Vico) et répartition budgétaire mensuelle.
- **Centre des Échéances :** Suivi intelligent du Contrôle Technique et de l'Assurance avec badges d'alerte animés.
- **Coffre-fort Numérique :** Rangement de documents par catégories (7 dossiers) avec conversion Photo vers PDF intégrée.
- **Sécurités Intégrées :** Validation de cohérence sur l'année, la puissance moteur et la capacité des réservoirs.

## 🛠️ Stack Technique

- **Langage :** Kotlin 2.4+ (Coroutines, Flow)
- **UI :** Jetpack Compose (Material 3)
- **Architecture :** Clean Architecture + Feature-based (modulaire et évolutif)
- **Données :** Room (v8) avec pré-remplissage du catalogue des marques
- **IA :** Google ML Kit OCR (traitement 100% local)
- **Graphiques :** Vico Charts
- **Injection de dépendances :** Dagger Hilt

## 📂 Structure du Projet

```
app/src/main/java/com/carmanager/app/
├── core/           → Fondations partagées
│   ├── data/       → Room, Mappers, Repositories
│   ├── domain/     → Modèles métier et interfaces
│   ├── ui/         → Thème Teal, composants communs
│   └── util/       → IA OCR, Formatage, Date util
└── features/       → Modules par métier
    ├── dashboard/  → Écran principal et Stats
    ├── vehicles/   → Gestion du garage
    ├── fuel/       → Carburant et Recharges
    ├── maintenance/→ Entretien et Conseils
    └── documents/  → Porte-documents
```

## 🚀 Installation & Utilisation

1. **Prérequis :** Android Studio Ladybug (2026.1+) et SDK 37.
2. **Lancement :** Cloner le projet et laisser Gradle synchroniser.
3. **Tests :** Exécuter `./gradlew test` pour vérifier la logique métier et l'IA.

## 🔒 Confidentialité & Sauvegarde

- **Garage local :** Room et les fichiers de cette installation sont la source des véhicules, historiques et documents, en mode invité comme avec un compte Google. Les espaces invité et comptes restent séparés.
- **Connexion :** Google est le seul parcours de connexion proposé ; Firebase conserve l'identité. Une ancienne session déjà ouverte reste accessible jusqu'à sa déconnexion explicite, sans transfert de son garage.
- **Sauvegarde :** La sauvegarde cloud du garage est inactive. Aucune sauvegarde Drive, restauration cloud ou fusion invité/compte n'est disponible. La perte du téléphone ou la désinstallation peut entraîner la perte des données locales. Git sauvegarde le code source, pas le garage de l'utilisateur.
- **Services réseau :** Google/Firebase Auth, Google Play Billing et Ads/UMP restent utilisés. Firestore sert uniquement au nettoyage des anciennes collections lors d'une suppression de compte explicitement demandée ; aucun envoi automatique du garage n'est actif.

---
*Développé avec passion pour simplifier la vie des conducteurs.*
