package com.carmanager.app.navigation

import com.carmanager.app.core.ui.navigation.InterstitialNavigationPolicy
import com.carmanager.app.core.ui.navigation.Screen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialNavigationPolicyTest {
    @Test fun initialDashboardIsSafeWithoutAnyPreviousDestination() { assertTrue(InterstitialNavigationPolicy.isSafeRoute(Screen.Dashboard.route)) }
    @Test fun sameDashboardSurfaceRemainsSafeAcrossRecomposition() { repeat(5) { assertTrue(InterstitialNavigationPolicy.isSafeRoute("dashboard")) } }
    @Test fun calculatorRootIsAnAlwaysPresentInputFormAndRemainsUnsafe() { assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Calculators.route)) }
    @Test fun vehiclesMenusAndDestructiveActionsRemainOutsideAllowlist() { assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Vehicles.route)) }
    @Test fun settingsAccountPremiumAndLogoutActionsAreNeverSafe() { assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Settings.route)) }
    @Test fun everyFormDestinationIsProtectedEvenWithoutVisibleIme() {
        for(route in listOf(Screen.VehicleEdit.route,Screen.FuelEdit.route,Screen.MaintenanceEdit.route,"vehicle/edit","fuel/add/1","maintenance/add/1")) {
            assertFalse(InterstitialNavigationPolicy.isSafeRoute(route),route)
        }
    }
    @Test fun loginPrivacyImportsHistoriesAndPermissionsAreProtected() {
        for(route in listOf(Screen.Login.route,Screen.PrivacyPolicy.route,Screen.Documents.route,
            Screen.MaintenanceList.route,Screen.MileageHistory.route,Screen.MaintenanceAdvice.route,Screen.Deadlines.route)) {
            assertFalse(InterstitialNavigationPolicy.isSafeRoute(route),route)
        }
    }
    @Test fun unknownNullAndSubstringLookalikesFailClosed() {
        for(route in listOf(null,"","unknown","dashboard/extra","dashboard?dialog=true")) assertFalse(InterstitialNavigationPolicy.isSafeRoute(route))
    }
    @Test fun fuelHistoryIsPresentableOnlyWithSeparateCompletedSavePermit() { assertTrue(InterstitialNavigationPolicy.isSafeRoute(Screen.FuelList.route)) }
    @Test fun reportGenerationAndResultScreensAreUnsafe() { assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Stats.route)) }
    @Test fun dashboardSafetyDoesNotEstablishANaturalBreak() {
        assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.VehicleEdit.route))
        assertTrue(InterstitialNavigationPolicy.isSafeRoute(Screen.Dashboard.route))
    }
}
