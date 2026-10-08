package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialDecisionTest {
    private class Harness {
        var time=0L
        val clock=MonotonicClock { time }
        val frequency=InterstitialFrequencyPolicy(clock)
        lateinit var loaded:(String)->Unit
        lateinit var failed:()->Unit
        var callbacks:InterstitialCoordinator.DisplayCallbacks?=null
        val coordinator=InterstitialCoordinator(frequency,clock,load={ success:(String)->Unit,failure -> loaded=success; failed=failure })
        init { coordinator.updateEligibility(true); frequency.setForeground(true) }
        fun opportunity(valid:Boolean=true):InterstitialDecision {
            frequency.setForeground(valid && !coordinator.isShowing)
            return coordinator.onOpportunity(NaturalBreakOpportunity(NaturalBreakWorkflow.FuelRecordSaved, time, "history", 0), { if(valid) null else InterstitialDecision.DueButActivityUnavailable }) { _, cb -> callbacks=cb }
        }
        fun ready() { loaded("ad"); time=60_000L; opportunity() }
    }
    @Test fun youngForegroundReturnsNotDueYetEvenWhenLoaded() {
        val h=Harness(); h.loaded("ad"); h.time=59_999L
        assertEquals(InterstitialDecision.NotDueYet,h.opportunity()); assertNull(h.callbacks)
    }
    @Test fun sixtySecondsWithZeroTransitionsIsEligible() {
        val h=Harness(); h.loaded("ad"); h.time=60_000L
        assertEquals(InterstitialDecision.EligibleToShow,h.opportunity()); assertNotNull(h.callbacks)
    }
    @Test fun premiumGateReturnsPremium() {
        assertEquals(InterstitialDecision.Premium,interstitialGateDecision(true,true,true,true))
    }
    @Test fun consentUnavailableReturnsConsentUnavailable() {
        assertEquals(InterstitialDecision.ConsentUnavailable,interstitialGateDecision(true,false,false,true))
    }
    @Test fun freeRequiresSettledOwnershipConsentAndSdk() {
        assertEquals(InterstitialDecision.EligibleToShow,interstitialGateDecision(true,true,false,true))
        assertEquals(InterstitialDecision.PremiumUnsettled,interstitialGateDecision(true,true,false,false))
        assertEquals(InterstitialDecision.SdkUnavailable,interstitialGateDecision(false,true,false,true))
    }
    @Test fun missingCacheReturnsNotLoadedWithoutConsumption() {
        val h=Harness(); h.time=60_000L
        assertEquals(InterstitialDecision.AdNotReadyAtBreak,h.opportunity()); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun failureReturnsBackoffUntilExactRetryDeadline() {
        val h=Harness(); h.ready(); h.callbacks!!.failed()
        assertEquals(InterstitialDecision.LoadBackoff,h.opportunity()); assertEquals(0L,h.frequency.shownCount)
        h.time=120_000L; assertEquals(InterstitialDecision.AdNotReadyAtBreak,h.opportunity())
    }
    @Test fun actualDisplayAdvancesTo80SecondsAndRemainsNotDueBeforeIt() {
        val h=Harness(); h.ready(); h.callbacks!!.shown(); h.callbacks!!.shown(); h.callbacks!!.dismissed(); h.loaded("next")
        assertEquals(1L,h.frequency.shownCount); assertEquals(InterstitialDecision.NotDueYet,h.opportunity())
        h.time=139_999L; assertEquals(InterstitialDecision.NotDueYet,h.opportunity())
        h.time=140_000L; assertEquals(InterstitialDecision.EligibleToShow,h.opportunity())
    }
    @Test fun thirdActualDisplayIsEligibleInsteadOfSessionCap() {
        val h=Harness(); h.ready()
        repeat(3) { index ->
            if(index>0) { h.time+=h.frequency.remainingMs; h.opportunity() }
            h.callbacks!!.shown(); h.callbacks!!.dismissed(); h.loaded("next"); h.frequency.setForeground(true)
        }
        assertEquals(3L,h.frequency.shownCount); assertEquals(120_000L,h.frequency.nextDelayMs)
    }
    @Test fun invalidActivityNeitherConsumesNorPresents() {
        val h=Harness(); h.time=60_000L; h.loaded("ad")
        assertEquals(InterstitialDecision.DueButActivityUnavailable,h.opportunity(false)); assertNull(h.callbacks)
    }
    @Test fun disabledGlobalGateNeitherConsumesNorPresents() {
        val h=Harness(); h.coordinator.updateEligibility(false)
        assertEquals(InterstitialDecision.AdsUnavailable,h.opportunity()); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun dueUnsafeFormSurvivesReturnToDashboardWithoutTransitionCounter() {
        val h=Harness(); h.loaded("ad"); h.time=60_000L
        val form=InterstitialPresentability("vehicle/edit",true,true,true,false)
        assertEquals(InterstitialDecision.DueButUnsafeRoute,form.blocker()); assertTrue(h.frequency.isDue)
        assertNull(form.copy(route="dashboard").blocker())
        assertEquals(InterstitialDecision.EligibleToShow,h.opportunity())
    }
}
