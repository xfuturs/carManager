# Plan : Ajout de l'Historique Kilométrique

Permettre à l'utilisateur de consulter l'historique chronologique de tous les relevés de compteur (manuels, carburant, entretien) pour suivre l'utilisation de son véhicule.

## Revue Utilisateur Requise

> [!TIP]
> **Expérience Utilisateur :**
> - Un nouveau bouton "Historique" sera ajouté à côté du gros compteur (Odomètre) sur l'accueil.
> - L'écran affichera la source de chaque relevé : 📱 (Saisie manuelle), ⛽ (Plein de carburant), 🔧 (Entretien).

## Changements Proposés

### 1. Navigation

#### [MODIFIER] [Screen.kt](file:///C:/Users/mihai/Documents/Android/XFuturs/carManager/app/src/main/java/com/carmanager/app/core/ui/navigation/Screen.kt)
- Ajouter `data object MileageHistory : Screen("mileage/history/{vehicleId}")`.

#### [MODIFIER] [NavGraph.kt](file:///C:/Users/mihai/Documents/Android/XFuturs/carManager/app/src/main/java/com/carmanager/app/core/ui/navigation/NavGraph.kt)
- Déclarer la route pour le nouvel écran d'historique.

### 2. Interface Utilisateur (Dashboard)

#### [MODIFIER] [DashboardVehicleCard.kt](file:///C:/Users/mihai/Documents/Android/XFuturs/carManager/app/src/main/java/com/carmanager/app/core/ui/components/DashboardVehicleCard.kt)
- Ajouter une icône d'historique (`Icons.Default.History`) dans le bloc `OdometerBlock`.
- Passer l'action `onMileageHistoryClick` vers l'accueil.

### 3. Nouvel Écran d'Historique

#### [NOUVEAU] [MileageHistoryScreen.kt](file:///C:/Users/mihai/Documents/Android/XFuturs/carManager/app/src/main/java/com/carmanager/app/features/mileage/MileageHistoryScreen.kt)
- Écran affichant une `LazyColumn` des relevés.
- Design épuré : Date, Kilométrage, et icône de la source.

#### [NOUVEAU] [MileageHistoryViewModel.kt](file:///C:/Users/mihai/Documents/Android/XFuturs/carManager/app/src/main/java/com/carmanager/app/features/mileage/MileageHistoryViewModel.kt)
- Charger les données via `mileageRepository.observeByVehicle(vehicleId)`.

## Plan de Vérification

### Tests Automatisés
- Compilation réussie.

### Vérification Manuelle
1. Cliquer sur l'icône historique de l'odomètre.
2. Vérifier que la liste affiche bien les derniers pleins et saisies manuelles.
3. Vérifier que le bouton de retour fonctionne.
