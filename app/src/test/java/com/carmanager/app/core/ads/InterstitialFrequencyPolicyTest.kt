package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialFrequencyPolicyTest {
    private class Clock(var time: Long = 0L) : MonotonicClock { override fun now() = time }
    private val clock = Clock()
    private val policy = InterstitialFrequencyPolicy(clock)
    private fun resume() = policy.setForeground(true)

    @Test fun exactProgressiveSeriesSaturatesAt180Seconds() {
        assertEquals(listOf(60_000L,80_000L,100_000L,120_000L,140_000L,160_000L,180_000L,180_000L,180_000L),
            (0L..8L).map(::delayAfterDisplayedCount))
        assertEquals(180_000L, delayAfterDisplayedCount(Long.MAX_VALUE))
    }
    @Test fun negativeDisplayCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { delayAfterDisplayedCount(-1L) }
    }
    @Test fun initialProcessDoesNotCountUntilForeground() {
        clock.time=600_000L
        assertEquals(0L,policy.foregroundMs); assertEquals(60_000L,policy.remainingMs)
        assertFalse(policy.isDue); assertFalse(policy.canShow(true))
    }
    @Test fun firstExactDeadlineNeedsZeroNavigationTransitions() {
        resume(); clock.time=59_999L; assertFalse(policy.canShow(true))
        clock.time=60_000L; assertTrue(policy.canShow(true))
    }
    @Test fun timeInBackgroundCannotMakeAnInitialSessionDue() {
        clock.time=900_000L; assertFalse(policy.isDue)
        resume(); assertEquals(60_000L,policy.remainingMs)
    }
    @Test fun backgroundTenMinutesPreservesTheRemainingThirtySeconds() {
        resume(); clock.time=30_000L; policy.setForeground(false)
        clock.time+=600_000L; resume(); clock.time+=29_999L
        assertFalse(policy.isDue); assertEquals(1L,policy.remainingMs)
        clock.time++; assertTrue(policy.canShow(true)); assertEquals(60_000L,policy.foregroundMs)
    }
    @Test fun repeatedForegroundAndRecompositionDoNotResetSegment() {
        resume(); clock.time=30_000L; repeat(10) { resume() }
        clock.time=60_000L; assertTrue(policy.isDue)
    }
    @Test fun evaluatingDueDoesNotAdvanceDisplayCountOrDelay() {
        resume(); clock.time=60_000L
        repeat(10) { assertTrue(policy.canShow(true)) }
        assertEquals(0L,policy.shownCount); assertEquals(60_000L,policy.nextDelayMs)
    }
    @Test fun actualDisplayStarts80SecondsAndPausesFullscreen() {
        resume(); clock.time=60_000L; policy.onActuallyDisplayed()
        assertEquals(1L,policy.shownCount); assertEquals(80_000L,policy.nextDelayMs)
        assertFalse(policy.isForeground); assertFalse(policy.isDue)
        resume(); clock.time+=79_999L; assertFalse(policy.isDue)
        clock.time++; assertTrue(policy.isDue)
    }
    @Test fun everyExactDeadlineUsesTheRealDisplayMilestone() {
        resume()
        for (delay in listOf(60_000L,80_000L,100_000L,120_000L,140_000L,160_000L,180_000L,180_000L,180_000L)) {
            assertEquals(delay,policy.remainingMs)
            clock.time+=delay-1L; assertFalse(policy.isDue)
            clock.time++; assertTrue(policy.isDue)
            policy.onActuallyDisplayed(); resume()
        }
        assertEquals(9L,policy.shownCount)
    }
    @Test fun tenActualDisplaysHaveNoNumericSessionCap() {
        resume(); repeat(10) { clock.time+=policy.remainingMs; assertTrue(policy.canShow(true)); policy.onActuallyDisplayed(); resume() }
        assertEquals(10L,policy.shownCount); assertEquals(180_000L,policy.remainingMs)
        clock.time+=180_000L; assertTrue(policy.canShow(true))
    }
    @Test fun fullScreenTimeNeverCompletesNextInterval() {
        resume(); clock.time=60_000L; policy.onActuallyDisplayed()
        clock.time+=900_000L; assertEquals(80_000L,policy.remainingMs)
        resume(); clock.time+=80_000L; assertTrue(policy.isDue)
    }
    @Test fun transientHostPausePreservesAccumulatedTime() {
        resume(); clock.time=20_000L; policy.setForeground(false); clock.time+=50_000L
        resume(); clock.time+=40_000L; assertTrue(policy.isDue)
    }
    @Test fun processRestartAfterEightDisplaysStartsAtZeroAnd60Seconds() {
        resume(); repeat(8) { clock.time+=policy.remainingMs; policy.onActuallyDisplayed(); resume() }
        val restarted=InterstitialFrequencyPolicy(clock)
        assertEquals(0L,restarted.shownCount); assertEquals(0L,restarted.foregroundMs)
        assertEquals(60_000L,restarted.remainingMs); assertFalse(restarted.isDue)
    }
    @Test fun timingIsRelativeToNonzeroMonotonicForegroundStart() {
        clock.time=500_000L; resume(); clock.time=559_999L; assertFalse(policy.isDue)
        clock.time++; assertTrue(policy.canShow(true))
    }
    @Test fun adsGateStillBlocksAnOverdueSession() {
        resume(); clock.time=60_000L
        assertEquals(InterstitialDecision.AdsUnavailable,policy.decision(false))
        assertTrue(policy.isDue); assertEquals(0L,policy.shownCount)
    }
    @Test fun deferredDisplayResetsAtActualDisplayInsteadOfOriginalDeadline() {
        resume(); clock.time=100_000L; assertTrue(policy.isDue); policy.onActuallyDisplayed(); resume()
        clock.time=179_999L; assertFalse(policy.isDue)
        clock.time=180_000L; assertTrue(policy.isDue)
    }
    @Test fun backgroundPreservesProgressionAfterFirstDisplay() {
        resume(); clock.time=60_000L; policy.onActuallyDisplayed(); resume()
        clock.time+=40_000L; policy.setForeground(false); clock.time+=600_000L
        assertEquals(1L,policy.shownCount); assertEquals(40_000L,policy.remainingMs)
        resume(); clock.time+=40_000L; assertTrue(policy.isDue)
    }
}
