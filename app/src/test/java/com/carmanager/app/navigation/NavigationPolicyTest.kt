package com.carmanager.app.navigation

import com.carmanager.app.R
import com.carmanager.app.core.ads.adsEligible
import com.carmanager.app.core.ui.navigation.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NavigationPolicyTest {
    @Test fun `primary navigation contains exactly Accueil Calculs Parametres in order`() {
        assertEquals(listOf("dashboard", "calculators", "settings"), TopLevelDestination.entries.map { it.route })
        assertEquals(listOf(R.string.nav_dashboard, R.string.nav_calculators, R.string.nav_settings),
            TopLevelDestination.entries.map { it.titleRes })
    }

    @Test fun `garage route is preserved as a secondary destination without selected tab`() {
        assertEquals("vehicles", Screen.Vehicles.route)
        assertNull(topLevelDestinationForRoute(Screen.Vehicles.route))
    }

    @Test fun `each primary route resolves its own selected tab`() {
        TopLevelDestination.entries.forEach { destination ->
            assertEquals(destination, topLevelDestinationForRoute(destination.route))
        }
    }

    @Test fun `secondary unknown and absent routes never fall back to Accueil`() {
        listOf(null, "unknown", Screen.VehicleEdit.createRoute(), Screen.FuelList.createRoute(42),
            Screen.Stats.route, Screen.Login.route).forEach { assertNull(topLevelDestinationForRoute(it)) }
    }

    @Test fun `tab navigation preserves saved state and restores without duplicate start destination`() {
        val options = topLevelNavigationOptions(42)
        assertEquals(42, options.popUpToId)
        assertFalse(options.isPopUpToInclusive())
        assertTrue(options.shouldPopUpToSaveState())
        assertTrue(options.shouldLaunchSingleTop())
        assertTrue(options.shouldRestoreState())
    }

    @Test fun `banner placement includes garage and Calculs while Settings stays ad free`() {
        listOf(Screen.Dashboard.route, Screen.Vehicles.route, Screen.Calculators.route).forEach {
            assertTrue(canShowBannerOnRoute(it, true))
        }
        listOf(null, "unknown", Screen.Settings.route, Screen.Login.route, Screen.Stats.route,
            Screen.VehicleEdit.createRoute()).forEach { assertFalse(canShowBannerOnRoute(it, true)) }
    }

    @Test fun `parent gate suppresses every banner including Calculs before SDK readiness`() {
        listOf(Screen.Dashboard.route, Screen.Vehicles.route, Screen.Calculators.route, Screen.Settings.route)
            .forEach { assertFalse(canShowBannerOnRoute(it, false)) }
    }

    @Test fun `Calculs inherits current consent and Premium eligibility`() {
        for (consent in listOf(false, true)) {
            for (premium in listOf(false, true)) {
                assertEquals(consent && !premium,
                    canShowBannerOnRoute(Screen.Calculators.route, adsEligible(consent, premium)))
            }
        }
        assertFalse(canShowBannerOnRoute(Screen.Settings.route, adsEligible(true, false)))
    }
}
