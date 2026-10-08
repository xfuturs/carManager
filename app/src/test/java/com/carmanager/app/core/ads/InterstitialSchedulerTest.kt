package com.carmanager.app.core.ads

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterstitialSchedulerTest {
    private class Harness(private val scope:TestScope) {
        private val clock=MonotonicClock { scope.testScheduler.currentTime }
        val frequency=InterstitialFrequencyPolicy(clock)
        val state=MutableStateFlow(InterstitialPresentability("dashboard",true,true,true,false))
        val entitlement=MutableStateFlow(AdsEntitlement.AllowedFree)
        val consent=MutableStateFlow(true)
        val changes=MutableSharedFlow<Unit>(extraBufferCapacity=1)
        val loads=mutableListOf<Pair<(String)->Unit,()->Unit>>()
        val displays=mutableListOf<InterstitialCoordinator.DisplayCallbacks>()
        var evaluations=0
        var reason=InterstitialDecision.NotDueYet
        val coordinator=InterstitialCoordinator(frequency,clock,
            load={ loaded:(String)->Unit,failed -> loads+=loaded to failed },changed={ changes.tryEmit(Unit) })
        val job=scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            combine(state,entitlement,consent,changes.onStart { emit(Unit) }) { _,_,_,_ -> Unit }.collectLatest {
                try {
                    InterstitialScheduler().run(evaluate={
                        evaluations++
                        val allowed=entitlement.value==AdsEntitlement.AllowedFree && consent.value
                        coordinator.updateEligibility(allowed)
                        frequency.setForeground(allowed && state.value.interactiveForeground && !coordinator.isShowing)
                        coordinator.preload()
                        reason=when {
                            entitlement.value==AdsEntitlement.BlockedPremium -> InterstitialDecision.Premium
                            entitlement.value==AdsEntitlement.BlockedUnsettled -> InterstitialDecision.PremiumUnsettled
                            !consent.value -> InterstitialDecision.ConsentUnavailable
                            coordinator.isShowing -> InterstitialDecision.Showing
                            !frequency.isDue -> { coordinator.preload(); InterstitialDecision.NotDueYet }
                            state.value.blocker()!=null -> state.value.blocker()!!
                            else -> InterstitialDecision.DueAwaitingNaturalBreak
                        }
                    },nextDelay={ coordinator.nextWakeDelay(false) })
                } finally { frequency.setForeground(false) }
            }
        }
        fun opportunity() = coordinator.onOpportunity(
            NaturalBreakOpportunity(NaturalBreakWorkflow.FuelRecordSaved, scope.testScheduler.currentTime, "history", 0),
            { state.value.blocker() }) { _, callbacks -> displays += callbacks }
        fun loaded() { loads.last().first("ad") }
        fun failLoad() { loads.last().second() }
        fun finishDisplay() { displays.last().shown(); displays.last().dismissed() }
    }
    @Test fun firstTimerNeverShowsOnIdleDashboardWithoutNaturalBreak()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent()
        advanceTimeBy(59_999L); runCurrent(); assertTrue(h.displays.isEmpty())
        advanceTimeBy(1L); runCurrent(); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun exactProgressiveTimersContinueBeyondTenDisplays()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent()
        repeat(10) { index ->
            advanceTimeBy(delayAfterDisplayedCount(index.toLong())); runCurrent()
            assertEquals(index,h.displays.size); h.opportunity(); assertEquals(index+1,h.displays.size)
            h.finishDisplay(); h.loaded(); runCurrent()
        }
        assertEquals(10L,h.frequency.shownCount); assertEquals(180_000L,h.frequency.remainingMs)
    }
    @Test fun lifecycleBackgroundTenMinutesDoesNotCompleteFirstDelay()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(30_000L)
        h.state.value=h.state.value.copy(hostResumed=false); runCurrent(); advanceTimeBy(600_000L); runCurrent()
        assertTrue(h.displays.isEmpty()); assertEquals(30_000L,h.frequency.remainingMs)
        h.state.value=h.state.value.copy(hostResumed=true); runCurrent(); advanceTimeBy(29_999L); runCurrent()
        assertTrue(h.displays.isEmpty()); advanceTimeBy(1L); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun dueOnFormNeverShowsAfterUnrelatedSafeRouteEvent()=runTest {
        val h=Harness(this); h.state.value=h.state.value.copy(route="vehicle/edit"); runCurrent(); h.loaded(); runCurrent()
        advanceTimeBy(60_000L); runCurrent(); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue)
        assertEquals(InterstitialDecision.DueButUnsafeRoute,h.reason)
        h.state.value=h.state.value.copy(route="dashboard"); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun modalCloseNeverPresentsWithoutFreshNaturalBreak()=runTest {
        val h=Harness(this); h.state.value=h.state.value.copy(modalActive=true,windowFocused=false); runCurrent(); h.loaded(); runCurrent()
        advanceTimeBy(60_000L); runCurrent(); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue)
        h.state.value=h.state.value.copy(modalActive=false,windowFocused=true); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun imeDismissalNeverPresentsWithoutFreshNaturalBreak()=runTest {
        val h=Harness(this); h.state.value=h.state.value.copy(imeVisible=true); runCurrent(); h.loaded(); runCurrent()
        advanceTimeBy(60_000L); runCurrent(); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue)
        h.state.value=h.state.value.copy(imeVisible=false); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun activePremiumBeforeDueEvaluationStopsCachedAdAndPreservesDue()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(60_000L)
        h.entitlement.value=AdsEntitlement.BlockedPremium; runCurrent()
        assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue); assertFalse(h.frequency.isForeground)
        h.entitlement.value=AdsEntitlement.AllowedFree; runCurrent(); h.loaded(); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun unsettledEntitlementNeverShowsAndDoesNotStartForegroundClock()=runTest {
        val h=Harness(this); h.entitlement.value=AdsEntitlement.BlockedUnsettled; runCurrent()
        advanceTimeBy(600_000L); runCurrent(); assertTrue(h.displays.isEmpty()); assertEquals(60_000L,h.frequency.remainingMs)
    }
    @Test fun umpBlocksAnAlreadyDueAdUntilConsentAllowsAgain()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(60_000L)
        h.consent.value=false; runCurrent(); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue)
        h.consent.value=true; runCurrent(); h.loaded(); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun delayedLoadCompletionCannotPresentWithoutFreshBreak()=runTest {
        val h=Harness(this); runCurrent(); advanceTimeBy(60_000L); runCurrent()
        assertTrue(h.frequency.isDue); assertTrue(h.displays.isEmpty()); assertEquals(InterstitialDecision.DueAwaitingNaturalBreak,h.reason)
        advanceTimeBy(20_000L); h.loaded(); runCurrent(); assertTrue(h.displays.isEmpty())
    }
    @Test fun loadFailureUsesExactBackoffAndNeverRestartsSatisfiedCadence()=runTest {
        val h=Harness(this); runCurrent(); h.failLoad(); runCurrent()
        advanceTimeBy(60_000L); runCurrent(); assertEquals(2,h.loads.size); h.failLoad(); runCurrent()
        assertTrue(h.frequency.isDue); assertEquals(0L,h.frequency.shownCount)
        advanceTimeBy(59_999L); runCurrent(); assertEquals(2,h.loads.size)
        advanceTimeBy(1L); runCurrent(); assertEquals(3,h.loads.size)
        h.loaded(); runCurrent(); assertTrue(h.displays.isEmpty()); h.opportunity(); assertEquals(1,h.displays.size); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun showFailureRetainsFirstDelayDueAndRetriesAfterSixtySeconds()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(60_000L); runCurrent()
        h.opportunity(); h.displays.last().failed(); runCurrent(); assertTrue(h.frequency.isDue); assertEquals(60_000L,h.frequency.nextDelayMs)
        advanceTimeBy(59_999L); runCurrent(); assertEquals(1,h.loads.size)
        advanceTimeBy(1L); runCurrent(); assertEquals(2,h.loads.size)
        h.loaded(); runCurrent(); assertEquals(1,h.displays.size); h.opportunity(); assertEquals(2,h.displays.size); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun dueButUnsafeWaitsForEventsWithoutPeriodicPolling()=runTest {
        val h=Harness(this); h.state.value=h.state.value.copy(route="settings"); runCurrent(); h.loaded(); runCurrent()
        advanceTimeBy(60_000L); runCurrent(); val count=h.evaluations
        advanceTimeBy(600_000L); runCurrent(); assertEquals(count,h.evaluations); assertTrue(h.frequency.isDue)
    }
    @Test fun scopeDestructionCancelsTimersAndDoesNotRetainAHostOpportunity()=runTest {
        val h=Harness(this); runCurrent(); advanceTimeBy(30_000L); h.job.cancel(); runCurrent()
        h.loaded(); advanceTimeBy(600_000L); runCurrent()
        assertTrue(h.displays.isEmpty()); assertFalse(h.frequency.isForeground); assertEquals(30_000L,h.frequency.remainingMs)
    }
    @Test fun fullscreenDisplayTimeIsExcludedEvenWithoutLifecyclePause()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(60_000L); runCurrent()
        h.opportunity(); h.displays.last().shown(); runCurrent(); advanceTimeBy(600_000L); runCurrent()
        assertEquals(80_000L,h.frequency.remainingMs); h.displays.last().dismissed(); h.loaded(); runCurrent()
        advanceTimeBy(79_999L); runCurrent(); assertEquals(1,h.displays.size)
        advanceTimeBy(1L); runCurrent(); assertEquals(1,h.displays.size); h.opportunity(); assertEquals(2,h.displays.size)
    }
    @Test fun repeatedSafetyEventsDoNotRestartADeadline()=runTest {
        val h=Harness(this); runCurrent(); h.loaded(); runCurrent(); advanceTimeBy(30_000L)
        repeat(5) { h.changes.tryEmit(Unit); runCurrent() }
        advanceTimeBy(30_000L); runCurrent(); assertTrue(h.displays.isEmpty())
    }
}
