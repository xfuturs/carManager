package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialCoordinatorTest {
    private class Clock(var time: Long=0L) : MonotonicClock { override fun now()=time }
    private class Harness {
        val clock=Clock()
        val frequency=InterstitialFrequencyPolicy(clock)
        val loads=mutableListOf<Pair<(String)->Unit,()->Unit>>()
        val displays=mutableListOf<InterstitialCoordinator.DisplayCallbacks>()
        var changes=0
        val coordinator=InterstitialCoordinator(frequency,clock,load={ loaded:(String)->Unit, failed -> loads+=loaded to failed },changed={ changes++ })
        fun enable() { coordinator.updateEligibility(true); frequency.setForeground(true) }
        fun loaded(index:Int=loads.lastIndex) { loads[index].first("ad") }
        fun event() = NaturalBreakOpportunity(NaturalBreakWorkflow.FuelRecordSaved, clock.time, "history", 0)
        fun opportunity(valid:Boolean=true):InterstitialDecision {
            frequency.setForeground(valid && !coordinator.isShowing)
            return coordinator.onOpportunity(event(), { if (valid) null else InterstitialDecision.DueButActivityUnavailable }) { _, callbacks -> displays+=callbacks }
        }
        fun ready() { enable(); loaded(); clock.time=60_000L; opportunity() }
    }

    @Test fun preloadIsSingleConcurrentAndSingleCached() {
        val h=Harness(); h.enable(); repeat(4) { h.coordinator.preload() }; assertEquals(1,h.loads.size)
        h.loaded(); repeat(4) { h.coordinator.preload() }; assertEquals(1,h.loads.size)
    }
    @Test fun lateLoadKeepsDueAndDoesNotConsumeBeforeActualDisplay() {
        val h=Harness(); h.enable(); h.clock.time=60_000L; h.opportunity()
        assertTrue(h.frequency.isDue); assertEquals(0L,h.frequency.shownCount)
        h.loaded(); assertTrue(h.displays.isEmpty()); h.opportunity(); assertEquals(1,h.displays.size)
    }
    @Test fun earlyCachedOpportunityCannotShow() {
        val h=Harness(); h.enable(); h.loaded(); repeat(5) { assertEquals(InterstitialDecision.NotDueYet,h.opportunity()) }
        h.clock.time=60_000L; h.opportunity(); assertEquals(1,h.displays.size)
    }
    @Test fun invalidHostDoesNotConsumeOrDisplay() {
        val h=Harness(); h.enable(); h.loaded(); h.clock.time=60_000L; repeat(5) { h.opportunity(false) }
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.displays.isEmpty()); assertTrue(h.frequency.isDue)
    }
    @Test fun actualShowCallbackAloneConsumesAndDuplicateIsIgnored() {
        val h=Harness(); h.ready(); assertEquals(0L,h.frequency.shownCount)
        val cb=h.displays.single(); cb.shown(); cb.shown()
        assertEquals(1L,h.frequency.shownCount); assertEquals(80_000L,h.frequency.remainingMs)
        cb.dismissed(); assertEquals(2,h.loads.size); assertEquals(1,h.displays.size)
    }
    @Test fun activeFullScreenBlocksConcurrentShowAndPreload() {
        val h=Harness(); h.ready(); repeat(4) { h.opportunity(); h.coordinator.preload() }
        assertEquals(1,h.displays.size); assertEquals(1,h.loads.size); assertFalse(h.frequency.isForeground)
    }
    @Test fun failedShowKeepsDueAndDelayWithoutImmediateRetry() {
        val h=Harness(); h.ready(); h.displays.single().failed(); h.frequency.setForeground(true)
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.frequency.isDue); assertEquals(60_000L,h.frequency.nextDelayMs)
        h.clock.time=119_999L; assertEquals(InterstitialDecision.LoadBackoff,h.opportunity()); assertEquals(1,h.loads.size)
        h.clock.time=120_000L; h.opportunity(); assertEquals(2,h.loads.size)
        h.loaded(); h.opportunity(); assertEquals(2,h.displays.size)
    }
    @Test fun synchronousShowExceptionAlsoPreservesDue() {
        val h=Harness(); h.enable(); h.loaded(); h.clock.time=60_000L
        assertEquals(InterstitialDecision.ShowFailed,h.coordinator.onOpportunity(h.event(), { null }) { _, _ -> error("SDK") })
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.frequency.isDue)
        h.coordinator.preload(); assertEquals(1,h.loads.size)
    }
    @Test fun loadFailureBacksOffAndExposesOneExactRetryDeadline() {
        val h=Harness(); h.enable(); h.loads.single().second()
        assertEquals(60_000L,h.coordinator.retryDelayMs)
        h.clock.time=59_999L; h.opportunity(); assertEquals(1,h.loads.size)
        h.clock.time=60_000L; h.opportunity(); assertEquals(2,h.loads.size)
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.frequency.isDue)
    }
    @Test fun thrownLoadFailsSilentlyAndDoesNotRecurse() {
        val clock=Clock(); var count=0
        val policy=InterstitialFrequencyPolicy(clock)
        val coordinator=InterstitialCoordinator<String>(policy,clock,load={ _, _ -> count++; error("SDK") })
        coordinator.updateEligibility(true); policy.setForeground(true); coordinator.preload(); assertEquals(1,count)
        clock.time=60_000L; coordinator.preload(); assertEquals(2,count); assertEquals(0L,policy.shownCount)
    }
    @Test fun falseEligibilityClearsCacheAndPausesClock() {
        val h=Harness(); h.enable(); h.loaded(); h.coordinator.updateEligibility(false)
        assertFalse(h.frequency.isForeground); h.clock.time=60_000L
        assertEquals(InterstitialDecision.AdsUnavailable,h.coordinator.onOpportunity(h.event(), { null }) { _, _ -> fail<Unit>("display") })
        h.enable(); assertEquals(2,h.loads.size); assertFalse(h.frequency.isDue)
    }
    @Test fun staleLoadAfterEligibilityCycleCannotCacheOrStartDuplicateLoad() {
        val h=Harness(); h.enable(); h.coordinator.updateEligibility(false); h.enable()
        assertEquals(1,h.loads.size); h.loaded(0); assertEquals(1,h.loads.size)
        h.clock.time=60_000L; h.opportunity(); assertEquals(2,h.loads.size); assertTrue(h.displays.isEmpty())
        h.loaded(0); h.loaded(1); h.opportunity(); assertEquals(1,h.displays.size)
    }
    @Test fun staleCallbackWhileDisabledDoesNotShowOrPreload() {
        val h=Harness(); h.enable(); h.coordinator.updateEligibility(false); h.loaded()
        h.clock.time=60_000L; assertEquals(InterstitialDecision.AdsUnavailable,h.coordinator.onOpportunity(h.event(), { null }) { _, _ -> fail<Unit>("display") })
        assertEquals(1,h.loads.size); assertTrue(h.displays.isEmpty())
    }
    @Test fun cachedAdExpiresAtOneHourWithoutConsumption() {
        val h=Harness(); h.enable(); h.loaded(); h.clock.time=3_600_000L; h.opportunity()
        assertEquals(2,h.loads.size); assertTrue(h.displays.isEmpty()); assertEquals(0L,h.frequency.shownCount)
    }
    @Test fun moreThanTenActualDisplaysContinuePreloadingWithoutCap() {
        val h=Harness(); h.enable(); h.loaded()
        repeat(10) { index ->
            h.clock.time+=h.frequency.remainingMs; h.opportunity()
            assertEquals(index+1,h.displays.size)
            h.displays.last().shown(); h.displays.last().dismissed(); h.loaded(); h.frequency.setForeground(true)
        }
        assertEquals(10L,h.frequency.shownCount); assertEquals(11,h.loads.size)
        h.clock.time+=180_000L; h.opportunity(); assertEquals(11,h.displays.size)
    }
    @Test fun hostRecreationPreservesCadenceAndDiscardsCache() {
        val h=Harness(); h.ready(); h.displays.single().shown(); h.displays.single().dismissed(); h.loaded()
        h.frequency.setForeground(true); h.clock.time+=30_000L; h.coordinator.detachHost()
        h.clock.time+=600_000L; h.enable(); h.loaded()
        assertEquals(1L,h.frequency.shownCount); assertEquals(50_000L,h.frequency.remainingMs)
        h.clock.time+=50_000L; h.opportunity(); assertEquals(2,h.displays.size)
    }
    @Test fun loadCompletionAndDismissalSignalSchedulerChanges() {
        val h=Harness(); h.enable(); val before=h.changes
        h.loaded(); assertEquals(before+1,h.changes)
        h.clock.time=60_000L; h.opportunity(); h.displays.single().shown(); h.displays.single().dismissed()
        assertTrue(h.changes>=before+3)
    }
    @Test fun retryDeadlineCanPrecedeRemainingCadence() {
        val h=Harness(); h.ready(); h.displays.single().shown(); h.displays.single().dismissed()
        h.frequency.setForeground(true); h.loads.last().second()
        assertEquals(60_000L,h.coordinator.nextWakeDelay(false)); assertEquals(80_000L,h.frequency.remainingMs)
    }
    @Test fun callbackAfterFailedRequestCannotConsume() {
        val h=Harness(); h.ready(); val cb=h.displays.single(); cb.failed(); cb.shown()
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.frequency.isDue)
    }
    @Test fun dismissalWithoutShownDoesNotAdvanceCadence() {
        val h=Harness(); h.ready(); h.displays.single().dismissed()
        assertEquals(0L,h.frequency.shownCount); assertTrue(h.frequency.isDue)
    }
    @Test fun duplicateFailureCannotExtendRetryBackoff() {
        val h=Harness(); h.ready(); val cb=h.displays.single(); cb.failed()
        h.clock.time++; cb.failed(); assertEquals(59_999L,h.coordinator.retryDelayMs)
    }
    @Test fun realDisplayStillCountsIfEligibilityWasRevokedAfterRequest() {
        val h=Harness(); h.ready(); h.coordinator.updateEligibility(false); h.displays.single().shown()
        assertEquals(1L,h.frequency.shownCount); assertFalse(h.frequency.isForeground)
        h.displays.single().dismissed(); assertEquals(1,h.loads.size)
    }
}
