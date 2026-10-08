package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialPresentabilityTest {
    private val safe=InterstitialPresentability("dashboard",true,true,true,false)
    @Test fun defaultsAreClosed() { assertNotNull(InterstitialPresentability().blocker()); assertFalse(InterstitialPresentability().interactiveForeground) }
    @Test fun stableDashboardIsPresentableWithoutNavigation() { assertNull(safe.blocker()); assertTrue(safe.interactiveForeground) }
    @Test fun formIsUnsafeButStillInteractiveForeground() {
        val form=safe.copy(route="vehicle/edit?vehicleId={vehicleId}")
        assertEquals(InterstitialDecision.DueButUnsafeRoute,form.blocker()); assertTrue(form.interactiveForeground)
    }
    @Test fun calculatorInputsRemainProtectedWithImeHidden() { assertEquals(InterstitialDecision.DueButUnsafeRoute,safe.copy(route="calculators").blocker()) }
    @Test fun vehiclesWithMenusAreOutsideAllowlist() { assertEquals(InterstitialDecision.DueButUnsafeRoute,safe.copy(route="vehicles").blocker()) }
    @Test fun settingsAndLogoutAreOutsideAllowlist() { assertEquals(InterstitialDecision.DueButUnsafeRoute,safe.copy(route="settings").blocker()) }
    @Test fun unfinishedNavigationTransitionBlocksAndPausesTime() {
        val state=safe.copy(destinationResumed=false)
        assertEquals(InterstitialDecision.DueButTransitionActive,state.blocker()); assertFalse(state.interactiveForeground)
    }
    @Test fun pausedActivityBlocksAndPausesTime() {
        val state=safe.copy(hostResumed=false)
        assertEquals(InterstitialDecision.DueButActivityUnavailable,state.blocker()); assertFalse(state.interactiveForeground)
    }
    @Test fun missingWindowFocusBlocksAndPausesTime() {
        val state=safe.copy(windowFocused=false)
        assertEquals(InterstitialDecision.DueButActivityUnavailable,state.blocker()); assertFalse(state.interactiveForeground)
    }
    @Test fun visibleImeBlocksWhileUserEditingTimeCanAccumulate() {
        val state=safe.copy(imeVisible=true)
        assertEquals(InterstitialDecision.DueButImeVisible,state.blocker()); assertTrue(state.interactiveForeground)
    }
    @Test fun modalBlocksEvenWhenItOwnsWindowFocus() {
        val state=safe.copy(modalActive=true,windowFocused=false)
        assertEquals(InterstitialDecision.DueButModalActive,state.blocker()); assertTrue(state.interactiveForeground)
    }
    @Test fun destructiveOrExternalFlowBlocksPresentation() { assertEquals(InterstitialDecision.DueButBlockingFlow,safe.copy(blockingFlow=true).blocker()) }
    @Test fun destroyedHostCannotBePresentableEvenWithOtherSafeFields() { assertNotNull(safe.copy(hostResumed=false,destinationResumed=false).blocker()) }
    @Test fun nestedModalOwnersCannotUnblockEachOther() {
        val state=InterstitialModalState(); val first=Any(); val second=Any()
        state.acquire(first); state.acquire(second); state.release(first); assertTrue(state.blocked.value)
        state.release(second); assertFalse(state.blocked.value)
    }
    @Test fun duplicateAcquireIsIdempotent() {
        val state=InterstitialModalState(); val token=Any()
        repeat(3) { state.acquire(token) }; state.release(token); assertFalse(state.blocked.value)
    }
    @Test fun releasingUnknownOwnerDoesNotClearAnActiveModal() {
        val state=InterstitialModalState(); state.acquire(Any()); state.release(Any()); assertTrue(state.blocked.value)
    }
    @Test fun dueOpportunitySurvivesUnsafeRouteUntilDashboard() {
        var now=0L; val policy=InterstitialFrequencyPolicy(MonotonicClock { now }); policy.setForeground(true); now=60_000L
        assertTrue(policy.isDue); assertNotNull(safe.copy(route="fuel/add/1").blocker())
        now+=20_000L; assertTrue(policy.isDue); assertNull(safe.blocker()); assertEquals(0L,policy.shownCount)
    }
    @Test fun dueOpportunitySurvivesDialogUntilClose() {
        var now=0L; val policy=InterstitialFrequencyPolicy(MonotonicClock { now }); policy.setForeground(true); now=60_000L
        val modals=InterstitialModalState(); val token=Any(); modals.acquire(token)
        assertEquals(InterstitialDecision.DueButModalActive,safe.copy(modalActive=modals.blocked.value).blocker())
        modals.release(token); assertNull(safe.copy(modalActive=modals.blocked.value).blocker()); assertTrue(policy.isDue)
    }
    @Test fun dueOpportunitySurvivesImeUntilKeyboardCloses() {
        var now=0L; val policy=InterstitialFrequencyPolicy(MonotonicClock { now }); policy.setForeground(true); now=60_000L
        assertNotNull(safe.copy(imeVisible=true).blocker()); assertTrue(policy.isDue)
        assertNull(safe.copy(imeVisible=false).blocker()); assertTrue(policy.isDue)
    }
    @Test fun activeModalCannotMakeBackgroundInteractive() {
        val state=safe.copy(hostResumed=false,modalActive=true,windowFocused=false)
        assertFalse(state.interactiveForeground); assertNotNull(state.blocker())
    }
}
