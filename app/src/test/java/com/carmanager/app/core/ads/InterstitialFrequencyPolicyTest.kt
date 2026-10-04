package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialFrequencyPolicyTest {
    private class Clock(var time: Long = 0) : MonotonicClock { override fun now() = time }
    private val clock = Clock()
    private val policy = InterstitialFrequencyPolicy(clock)
    private fun transitions(n: Int = 2) { repeat(n) { policy.onEligibleTransition() } }

    @Test fun constantsAreTheRequestedProductLimits() {
        assertEquals(120_000L, InterstitialFrequencyPolicy.MIN_SESSION_AGE_MS)
        assertEquals(2, InterstitialFrequencyPolicy.MIN_ELIGIBLE_TRANSITIONS)
        assertEquals(240_000L, InterstitialFrequencyPolicy.INTERSTITIAL_COOLDOWN_MS)
        assertEquals(2, InterstitialFrequencyPolicy.MAX_INTERSTITIALS_PER_SESSION)
    }
    @Test fun manyTransitionsCannotBypassFirstDelay() { transitions(20); clock.time = 119_999; assertFalse(policy.canShow(true)) }
    @Test fun exactFirstDelayAllowsTwoTransitions() { transitions(); clock.time = 120_000; assertTrue(policy.canShow(true)) }
    @Test fun timeWithoutTransitionsCannotShow() { clock.time = 900_000; assertFalse(policy.canShow(true)) }
    @Test fun oneTransitionCannotShow() { clock.time = 120_000; transitions(1); assertFalse(policy.canShow(true)) }
    @Test fun ineligibleCannotShowEvenWhenReady() { clock.time = 120_000; transitions(); assertFalse(policy.canShow(false)) }
    @Test fun checkingOrRecordingTransitionsDoesNotConsumeSlot() {
        clock.time = 120_000; transitions(); repeat(3) { assertTrue(policy.canShow(true)) }
        assertEquals(0, policy.shownCount); assertNull(policy.lastShownElapsedRealtime)
    }
    @Test fun actualDisplayConsumesAndResetsTransitions() {
        clock.time = 120_000; transitions(); policy.onActuallyDisplayed()
        assertEquals(1, policy.shownCount); assertEquals(0, policy.eligibleTransitionCount)
        assertEquals(120_000L, policy.lastShownElapsedRealtime)
    }
    @Test fun twoNewTransitionsCannotBypassCooldown() {
        clock.time = 120_000; policy.onActuallyDisplayed(); transitions(20)
        clock.time = 359_999; assertFalse(policy.canShow(true))
        clock.time = 360_000; assertTrue(policy.canShow(true))
    }
    @Test fun cooldownStillRequiresTwoNewTransitions() {
        clock.time = 120_000; policy.onActuallyDisplayed(); clock.time = 360_000
        assertFalse(policy.canShow(true)); transitions(1); assertFalse(policy.canShow(true))
        transitions(1); assertTrue(policy.canShow(true))
    }
    @Test fun maximumTwoActualDisplaysIsFinalForProcess() {
        policy.onActuallyDisplayed(); policy.onActuallyDisplayed(); clock.time = Long.MAX_VALUE / 2
        transitions(50); policy.onActuallyDisplayed()
        assertEquals(2, policy.shownCount); assertFalse(policy.hasCapacity); assertFalse(policy.canShow(true))
    }
    @Test fun sessionUsesRelativeMonotoneTimeAndNewProcessResets() {
        val nonzero = Clock(500_000); val first = InterstitialFrequencyPolicy(nonzero)
        repeat(2) { first.onEligibleTransition() }; nonzero.time = 619_999; assertFalse(first.canShow(true))
        nonzero.time++; assertTrue(first.canShow(true)); first.onActuallyDisplayed()
        val restarted = InterstitialFrequencyPolicy(nonzero)
        assertEquals(0, restarted.shownCount); assertEquals(0, restarted.eligibleTransitionCount)
        repeat(2) { restarted.onEligibleTransition() }; assertFalse(restarted.canShow(true))
    }
}
