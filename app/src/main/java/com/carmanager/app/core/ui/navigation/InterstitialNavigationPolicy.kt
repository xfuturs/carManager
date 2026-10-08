package com.carmanager.app.core.ui.navigation

/** Surfaces techniquement présentables ; une route ne constitue jamais une opportunité. */
object InterstitialNavigationPolicy {
    // Calculs est un formulaire permanent ; Véhicules contient des menus/destructions.
    // Les écrans non listés sont fermés par défaut, même sans IME visible.
    fun isSafeRoute(route: String?): Boolean = route == Screen.Dashboard.route || route == Screen.FuelList.route
}
