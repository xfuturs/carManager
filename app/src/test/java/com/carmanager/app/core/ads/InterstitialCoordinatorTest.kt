package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialCoordinatorTest {
    private class Clock(var time: Long = 0) : MonotonicClock { override fun now() = time }
    private class Harness {
        val clock = Clock()
        val frequency = InterstitialFrequencyPolicy(clock)
        val loads = mutableListOf<Pair<(String) -> Unit, () -> Unit>>()
        val displays = mutableListOf<InterstitialCoordinator.DisplayCallbacks>()
        val coordinator = InterstitialCoordinator(frequency, clock) { loaded: (String) -> Unit, failed -> loads += loaded to failed }
        fun enable() = coordinator.updateEligibility(true)
        fun loaded(index: Int = loads.lastIndex) { loads[index].first("ad-$index") }
        fun opportunity(valid: Boolean = true) { coordinator.onOpportunity(valid) { _, callbacks -> displays += callbacks } }
        fun ready() { enable(); loaded(); clock.time = 120_000; opportunity(); opportunity() }
    }

    @Test fun preloadIsSingleConcurrentAndSingleCached() {
        val h = Harness(); h.enable(); repeat(4) { h.coordinator.preload() }; assertEquals(1, h.loads.size)
        h.loaded(); repeat(4) { h.coordinator.preload() }; assertEquals(1, h.loads.size)
    }
    @Test fun loadingNeverBlocksOrAutomaticallyShows() {
        val h = Harness(); h.enable(); h.clock.time = 120_000; repeat(3) { h.opportunity() }
        assertEquals(3, h.frequency.eligibleTransitionCount); assertEquals(0, h.frequency.shownCount)
        h.loaded(); assertTrue(h.displays.isEmpty()); h.opportunity(); assertEquals(1, h.displays.size)
    }
    @Test fun earlyCachedOpportunitiesCannotShow() {
        val h = Harness(); h.enable(); h.loaded(); repeat(5) { h.opportunity() }
        assertTrue(h.displays.isEmpty()); h.clock.time = 120_000; h.opportunity(); assertEquals(1, h.displays.size)
    }
    @Test fun invalidHostDoesNotConsumeOrDisplay() {
        val h = Harness(); h.enable(); h.loaded(); h.clock.time = 120_000; repeat(5) { h.opportunity(false) }
        assertEquals(0, h.frequency.eligibleTransitionCount); assertEquals(0, h.frequency.shownCount)
        assertTrue(h.displays.isEmpty())
    }
    @Test fun actualShowCallbackAloneConsumesAndDuplicateIsIgnored() {
        val h = Harness(); h.ready(); assertEquals(0, h.frequency.shownCount)
        val callback = h.displays.single(); callback.shown(); callback.shown()
        assertEquals(1, h.frequency.shownCount); assertEquals(0, h.frequency.eligibleTransitionCount)
        assertEquals(120_000L, h.frequency.lastShownElapsedRealtime)
        callback.dismissed(); assertEquals(2, h.loads.size); assertEquals(1, h.displays.size)
    }
    @Test fun activeFullScreenBlocksConcurrentShowAndPreload() {
        val h = Harness(); h.ready(); repeat(4) { h.opportunity(); h.coordinator.preload() }
        assertEquals(1, h.displays.size); assertEquals(1, h.loads.size); assertEquals(2, h.frequency.eligibleTransitionCount)
    }
    @Test fun failedShowKeepsTransitionsAndSlotWithoutImmediateRetry() {
        val h = Harness(); h.ready(); h.displays.single().failed()
        assertEquals(0, h.frequency.shownCount); assertNull(h.frequency.lastShownElapsedRealtime)
        assertEquals(2, h.frequency.eligibleTransitionCount); assertEquals(1, h.loads.size)
        h.clock.time = 179_999; h.opportunity(); assertEquals(1, h.loads.size)
        h.clock.time = 180_000; h.opportunity(); assertEquals(2, h.loads.size)
        h.loaded(); assertEquals(1, h.displays.size); h.opportunity(); assertEquals(2, h.displays.size)
    }
    @Test fun synchronousShowExceptionAlsoPreservesFrequency() {
        val h = Harness(); h.enable(); h.loaded(); h.clock.time = 120_000; h.opportunity()
        h.coordinator.onOpportunity(true) { _, _ -> throw IllegalStateException("SDK") }
        assertEquals(0, h.frequency.shownCount); assertEquals(2, h.frequency.eligibleTransitionCount)
        h.coordinator.preload(); assertEquals(1, h.loads.size)
    }
    @Test fun loadFailureBacksOffAndNeedsLaterOpportunity() {
        val h = Harness(); h.enable(); h.loads.single().second()
        assertEquals(1, h.loads.size); h.clock.time = 59_999; h.opportunity(); assertEquals(1, h.loads.size)
        h.clock.time = 60_000; assertEquals(1, h.loads.size); h.opportunity(); assertEquals(2, h.loads.size)
        assertEquals(0, h.frequency.shownCount)
    }
    @Test fun thrownLoadFailsSilentlyAndDoesNotRecurse() {
        val clock = Clock(); var count = 0
        val policy = InterstitialFrequencyPolicy(clock)
        val coordinator = InterstitialCoordinator<String>(policy, clock) { _, _ -> count++; error("SDK") }
        coordinator.updateEligibility(true); coordinator.preload(); assertEquals(1, count)
        clock.time = 60_000; coordinator.preload(); assertEquals(2, count); assertEquals(0, policy.shownCount)
    }
    @Test fun falseEligibilityClearsCachedAd() {
        val h = Harness(); h.enable(); h.loaded(); h.coordinator.updateEligibility(false)
        h.clock.time = 120_000; repeat(3) { h.opportunity() }; assertTrue(h.displays.isEmpty())
        assertEquals(0, h.frequency.eligibleTransitionCount)
        h.enable(); assertEquals(2, h.loads.size); h.opportunity(); assertTrue(h.displays.isEmpty())
    }
    @Test fun staleLoadAfterEligibilityCycleCannotCacheOrStartDuplicateLoad() {
        val h = Harness(); h.enable(); h.coordinator.updateEligibility(false); h.enable()
        assertEquals(1, h.loads.size); h.loaded(0); assertEquals(1, h.loads.size)
        h.clock.time = 120_000; h.opportunity(); h.opportunity()
        assertEquals(2, h.loads.size); assertTrue(h.displays.isEmpty())
        h.loaded(0); h.loaded(1); h.opportunity(); assertEquals(1, h.displays.size)
    }
    @Test fun staleCallbackWhileDisabledDoesNotShowOrPreload() {
        val h = Harness(); h.enable(); h.coordinator.updateEligibility(false); h.loaded()
        h.clock.time = 120_000; h.opportunity(); assertEquals(1, h.loads.size); assertTrue(h.displays.isEmpty())
    }
    @Test fun cachedAdExpiresAtOneHourWithoutConsumption() {
        val h = Harness(); h.enable(); h.loaded(); h.clock.time = 3_600_000; repeat(2) { h.opportunity() }
        assertEquals(2, h.loads.size); assertTrue(h.displays.isEmpty()); assertEquals(0, h.frequency.shownCount)
    }
    @Test fun secondAdNeedsCooldownAndTwoNewTransitionsAndThenStopsPreload() {
        val h = Harness(); h.ready(); h.displays[0].shown(); h.displays[0].dismissed(); h.loaded()
        h.clock.time = 360_000; h.opportunity(); assertEquals(1, h.displays.size)
        h.opportunity(); assertEquals(2, h.displays.size); h.displays[1].shown(); h.displays[1].dismissed()
        h.clock.time = 999_999; repeat(10) { h.opportunity(); h.coordinator.preload() }
        assertEquals(2, h.frequency.shownCount); assertEquals(2, h.loads.size); assertEquals(2, h.displays.size)
    }
    @Test fun hostRecreationPreservesSessionButDiscardsCacheAndNeedsNavigation() {
        val h = Harness(); h.ready(); h.displays[0].shown(); h.displays[0].dismissed(); h.loaded()
        h.coordinator.detachHost(); h.enable(); assertEquals(1, h.frequency.shownCount)
        assertEquals(120_000L, h.frequency.lastShownElapsedRealtime)
        h.loaded(); h.clock.time = 360_000; assertEquals(1, h.displays.size)
        h.opportunity(); assertEquals(1, h.displays.size); h.opportunity(); assertEquals(2, h.displays.size)
    }
}
