package com.carmanager.app.core.ui.navigation

import com.carmanager.app.core.ui.components.CarManagerBottomNavigation
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.carmanager.app.core.ads.InterstitialAdManager
import com.carmanager.app.core.ads.InterstitialModalState
import com.carmanager.app.core.ads.BannerHostRetention
import com.carmanager.app.core.ui.components.BannerAdSlot
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carmanager.app.features.auth.LoginScreen
import com.carmanager.app.features.calculators.CalculatorsScreen
import com.carmanager.app.features.dashboard.DashboardScreen
import com.carmanager.app.features.dashboard.DeadlinesScreen
import com.carmanager.app.features.dashboard.StatsScreen
import com.carmanager.app.features.documents.DocumentsScreen
import com.carmanager.app.features.fuel.AddFuelScreen
import com.carmanager.app.features.fuel.FuelListScreen
import com.carmanager.app.features.maintenance.AddMaintenanceScreen
import com.carmanager.app.features.maintenance.MaintenanceAdviceScreen
import com.carmanager.app.features.maintenance.MaintenanceListScreen
import com.carmanager.app.features.mileage.MileageHistoryScreen
import com.carmanager.app.features.settings.PrivacyPolicyScreen
import com.carmanager.app.features.settings.SettingsScreen
import com.carmanager.app.features.vehicles.AddEditVehicleScreen
import com.carmanager.app.features.vehicles.VehiclesScreen
import com.carmanager.app.R

@Composable
fun CarManagerNavHost(
    activity: ComponentActivity,
    interstitials: InterstitialAdManager,
    canShowAds: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onPrivacyOptionsClick: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selectedDestination = topLevelDestinationForRoute(currentDestination?.route)
    val bannerVisible = canShowBannerOnRoute(currentDestination?.route, canShowAds)
    val bannerOwner = remember { BannerHostRetention() }
    val bannerRetained by bannerOwner.retained.collectAsState()
    SideEffect { bannerOwner.update(canShowAds, bannerVisible) }
    val interstitialModals = remember { InterstitialModalState() }
    ObserveTimedInterstitials(navController, activity, interstitials, interstitialModals)

    CompositionLocalProvider(LocalInterstitialModals provides interstitialModals) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // Position stable, hors NavHost : aucune clé de route et un seul parent AndroidView.
                if (canShowAds && (bannerVisible || bannerRetained)) {
                    BannerAdSlot(visible = bannerVisible)
                }
                if (selectedDestination != null) {
                    CarManagerBottomNavigation(
                        selectedDestination = selectedDestination,
                        onDestinationClick = { destination ->
                            navController.navigate(
                                destination.route,
                                topLevelNavigationOptions(navController.graph.findStartDestination().id)
                            )
                        }
                    )
                }
                // La barre principale gère déjà ses insets ; Véhicules reste une destination secondaire.
                if (selectedDestination == null && bannerVisible) {
                    androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding())
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onAddVehicle = { navController.navigate(Screen.VehicleEdit.createRoute()) },
                    onEditVehicle = { id -> navController.navigate(Screen.VehicleEdit.createRoute(id)) },
                    onNavigateToFuel = { id -> navController.navigate(Screen.FuelList.createRoute(id)) },
                    onNavigateToMaintenance = { id -> navController.navigate(Screen.MaintenanceList.createRoute(id)) },
                    onAdminClick = { vehicleId, type -> 
                        navController.navigate(Screen.MaintenanceEdit.createRoute(vehicleId, type.name))
                    },
                    onDocumentsClick = { id -> navController.navigate(Screen.Documents.createRoute(id)) },
                    onNavigateToAdvice = { id -> navController.navigate(Screen.MaintenanceAdvice.createRoute(id)) },
                    onNavigateToMileageHistory = { id -> navController.navigate(Screen.MileageHistory.createRoute(id)) },
                    onNavigateToVehicles = {
                        navController.navigate(Screen.Vehicles.route) { launchSingleTop = true }
                    },
                    onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                    onNavigateToDeadlines = { navController.navigate(Screen.Deadlines.route) }
                )
            }
            composable(Screen.Vehicles.route) {
                VehiclesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAddVehicle = { navController.navigate(Screen.VehicleEdit.createRoute()) },
                    onEditVehicle = { id -> navController.navigate(Screen.VehicleEdit.createRoute(id)) },
                    onNavigateToFuel = { id -> navController.navigate(Screen.FuelList.createRoute(id)) },
                    onNavigateToMaintenance = { id -> navController.navigate(Screen.MaintenanceList.createRoute(id)) }
                )
            }
            composable(Screen.Calculators.route) {
                CalculatorsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                    onPrivacyOptionsClick = onPrivacyOptionsClick,
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPrivacy = { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }
            composable(Screen.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Deadlines.route) {
                DeadlinesScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.MaintenanceAdvice.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                MaintenanceAdviceScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.MileageHistory.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                MileageHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.VehicleEdit.route,
                arguments = listOf(
                    navArgument("vehicleId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                AddEditVehicleScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.FuelList.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                FuelListScreen(
                    onAddFuel = { id -> navController.navigate(Screen.FuelEdit.createRoute(id)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.FuelEdit.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                AddFuelScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.MaintenanceList.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                MaintenanceListScreen(
                    onAddMaintenance = { id: Long -> navController.navigate(Screen.MaintenanceEdit.createRoute(id)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.MaintenanceEdit.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType },
                    navArgument("initialType") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AddMaintenanceScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Documents.route,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType }
                )
            ) {
                DocumentsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
    }
}
