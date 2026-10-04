package com.carmanager.app.navigation

import com.carmanager.app.core.ui.navigation.InterstitialNavigationPolicy
import com.carmanager.app.core.ui.navigation.Screen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialNavigationPolicyTest {
    private val dashboard = Screen.Dashboard.route
    private val calculators = Screen.Calculators.route
    @Test fun initialDestinationAndSameRouteNeverCount() {
        val policy = InterstitialNavigationPolicy()
        assertFalse(policy.onDestination(dashboard)); assertFalse(policy.onDestination(dashboard))
        assertTrue(policy.onDestination(calculators)); assertFalse(policy.onDestination(calculators))
    }
    @Test fun restoredDestinationSeedsNewObserverWithoutOpportunity() {
        assertFalse(InterstitialNavigationPolicy().onDestination(calculators))
        assertFalse(InterstitialNavigationPolicy().onDestination(dashboard))
    }
    @Test fun dashboardAndCalculatorsAreNaturalTransitionsBothWays() {
        assertTrue(InterstitialNavigationPolicy.isEligible(dashboard, calculators))
        assertTrue(InterstitialNavigationPolicy.isEligible(calculators, dashboard))
    }
    @Test fun vehiclesAndSettingsMayLeaveToSafeDestination() {
        for (origin in listOf(Screen.Vehicles.route, Screen.Settings.route)) {
            assertTrue(InterstitialNavigationPolicy.isEligible(origin, dashboard))
            assertTrue(InterstitialNavigationPolicy.isEligible(origin, calculators))
        }
    }
    @Test fun allOtherDestinationsAreProtected() {
        for (destination in listOf(Screen.Settings.route, Screen.Vehicles.route, Screen.Login.route,
            Screen.PrivacyPolicy.route, Screen.Stats.route, Screen.Deadlines.route, Screen.Documents.route,
            Screen.VehicleEdit.route, Screen.FuelEdit.route, Screen.MaintenanceEdit.route)) {
            assertFalse(InterstitialNavigationPolicy.isEligible(dashboard, destination), destination)
        }
    }
    @Test fun formSaveAndCancelReturnNeverCount() {
        for (form in listOf(Screen.VehicleEdit.route, Screen.FuelEdit.route, Screen.MaintenanceEdit.route)) {
            val policy = InterstitialNavigationPolicy(); policy.onDestination(dashboard)
            assertFalse(policy.onDestination(form)); assertFalse(policy.onDestination(dashboard))
        }
    }
    @Test fun loginPrivacyDocumentsHistoriesAndPermissionScreensNeverActAsOrigin() {
        for (origin in listOf(Screen.Login.route, Screen.PrivacyPolicy.route, Screen.Documents.route,
            Screen.FuelList.route, Screen.MaintenanceList.route, Screen.MileageHistory.route,
            Screen.MaintenanceAdvice.route, Screen.Stats.route, Screen.Deadlines.route)) {
            assertFalse(InterstitialNavigationPolicy.isEligible(origin, dashboard), origin)
            assertFalse(InterstitialNavigationPolicy.isEligible(origin, calculators), origin)
        }
    }
    @Test fun unknownNullAndSubstringLookalikesAreFailClosed() {
        for (route in listOf(null, "", "dashboard/extra", "vehicle/edit?vehicleId=1", "unknown")) {
            assertFalse(InterstitialNavigationPolicy.isEligible(route, dashboard))
            assertFalse(InterstitialNavigationPolicy.isEligible(dashboard, route))
        }
    }
    @Test fun excludedRouteBreaksPreviousSafeOriginChain() {
        val policy = InterstitialNavigationPolicy(); policy.onDestination(dashboard)
        assertFalse(policy.onDestination(Screen.PrivacyPolicy.route))
        assertFalse(policy.onDestination(calculators)); assertTrue(policy.onDestination(dashboard))
    }
}
