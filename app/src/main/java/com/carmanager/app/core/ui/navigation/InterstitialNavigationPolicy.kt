package com.carmanager.app.core.ui.navigation

/** Allowlist de surfaces passives auditées ; aucun historique de navigation. */
object InterstitialNavigationPolicy {
    // Calculs est un formulaire permanent ; Véhicules contient des menus/destructions.
    // Les écrans non listés sont fermés par défaut, même sans IME visible.
    fun isSafeRoute(route: String?): Boolean = route == Screen.Dashboard.route
}
