package com.carmanager.app.core.ui.navigation

/** Routes de destination (patterns NavGraph), jamais de substring de route concrete. */
class InterstitialNavigationPolicy {
    private var previousRoute: String? = null

    fun onDestination(route: String?): Boolean {
        val previous = previousRoute
        previousRoute = route
        return isEligible(previous, route)
    }

    companion object {
        private val destinations = setOf(Screen.Dashboard.route, Screen.Calculators.route)
        // Allowlist conservative : les ecrans document/PDF/permission/history restent exclus.
        private val origins = destinations + setOf(Screen.Settings.route, Screen.Vehicles.route)
        fun isEligible(previous: String?, destination: String?): Boolean =
            previous != null && previous != destination && previous in origins && destination in destinations
    }
}
