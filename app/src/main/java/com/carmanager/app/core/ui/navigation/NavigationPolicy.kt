package com.carmanager.app.core.ui.navigation

import androidx.navigation.NavOptions

/** Le graphe est plat : seules ces destinations affichent la navigation principale. */
fun topLevelDestinationForRoute(route: String?): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { it.route == route }

fun topLevelNavigationOptions(startDestinationId: Int): NavOptions = NavOptions.Builder()
    .setPopUpTo(startDestinationId, inclusive = false, saveState = true)
    .setLaunchSingleTop(true)
    .setRestoreState(true)
    .build()

/** Placement uniquement ; l'éligibilité UMP/Premium/SDK vient du parent. */
fun canShowBannerOnRoute(route: String?, canShowAds: Boolean): Boolean =
    canShowAds && when (route) {
        Screen.Dashboard.route, Screen.Vehicles.route, Screen.Calculators.route -> true
        else -> false
    }
