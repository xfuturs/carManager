# 🤖 Car Manager Project Agent

Ceci est le fichier d'instructions maître pour Gemini. Il définit l'identité, les règles et les connaissances de l'application **Car Manager**.

---

## 📋 Informations Projet
- **Nom :** Car Manager (v1.0 - 2026)
- **Objectif :** Gestion complète de flotte personnelle (thermique, électrique, hybride).
- **Style Visuel :** Turquoise (Teal), Moderne, Épuré, Odomètre XXL.
- **Localisation :** Français (FR) uniquement pour les dates et messages.

---

## 🛠️ Stack Technique & Règles de Code
- **Architecture :** Clean Architecture + Feature-based (Dossier `features/` par métier).
- **UI :** Jetpack Compose, Material 3.
- **Données :** Room (v7), Dagger Hilt (DI), Kotlin Coroutines & Flow.
- **Graphiques :** Vico (Evolution conso).
- **IA :** Google ML Kit OCR (Scan de tickets local).

---

## 🧠 Mémoire & Historique
Pour tout détail sur les implémentations passées, les choix techniques ou le guide de maintenance, se référer impérativement à :
👉 **[@PROJECT_MEMORY.md](./PROJECT_MEMORY.md)**

---

## 📜 Instructions pour l'Assistant (Gemini)
1. **Contexte :** Toujours proposer des solutions compatibles avec l'architecture `features/`.
2. **Langue :** Répondre et coder les libellés UI en **Français**.
3. **Design :** Utiliser les couleurs définies dans `core/ui/theme` (Teal).
4. **Sécurité :** Ne jamais bypasser les validations techniques (Année, Puissance, Capacité).
5. **Documents :** Toujours proposer la conversion PDF pour les nouveaux types de documents.

---
*Fichier généré le 03/08/2026 pour indexation IDE.*
