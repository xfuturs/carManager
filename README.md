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
- **Données :** Room (v7) avec pré-remplissage du catalogue des marques
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

- **100% Local :** Vos données et photos ne quittent jamais votre téléphone.
- **Sauvegarde :** Pour sauvegarder, synchronisez le dossier du projet avec votre service Cloud préféré (Google Drive, OneDrive) ou utilisez Git.

---
*Développé avec passion pour simplifier la vie des conducteurs.*
