package com.carmanager.app.core.ads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NaturalBreakOpportunityTest {
    private fun event(time: Long = 60_000L) = NaturalBreakOpportunity(NaturalBreakWorkflow.FuelRecordSaved, time, "history", 0)
    @Test fun eventIsAcceptedOnceAndRejectedOnReplay() {
        val event = event(); assertEquals(InterstitialDecision.NaturalBreakAccepted,event.consume(60_000))
        repeat(5) { assertEquals(InterstitialDecision.NaturalBreakRejected,event.consume(60_001)) }
    }
    @Test fun expiryIsBoundedAtExactly1500Milliseconds() {
        assertEquals(InterstitialDecision.NaturalBreakAccepted,event().consume(61_499))
        val event=event(); assertEquals(InterstitialDecision.NaturalBreakExpired,event.consume(61_500))
        assertEquals(InterstitialDecision.NaturalBreakRejected,event.consume(61_500))
    }
    @Test fun backwardsClockFailsClosed() { assertEquals(InterstitialDecision.NaturalBreakExpired,event().consume(59_999)) }
    @Test fun discardedEventCannotBeReplayed() { val event=event(); event.discard(); assertEquals(InterstitialDecision.NaturalBreakRejected,event.consume(60_000)) }
    @Test fun noAdAtBreakConsumesOnlyEventAndKeepsFrequencyDue() {
        var now=0L; var loaded: ((String)->Unit)?=null; var shows=0
        val clock=MonotonicClock { now }; val frequency=InterstitialFrequencyPolicy(clock)
        val coordinator=InterstitialCoordinator(frequency,clock,load={ success:(String)->Unit,_ -> loaded=success })
        coordinator.updateEligibility(true); frequency.setForeground(true); now=60_000
        val event=event(); assertEquals(InterstitialDecision.AdNotReadyAtBreak,coordinator.onOpportunity(event,{null}) {_,_->shows++})
        assertTrue(frequency.isDue); assertEquals(0L,frequency.shownCount)
        loaded!!("ad"); assertEquals(0,shows)
        assertEquals(InterstitialDecision.NaturalBreakRejected,coordinator.onOpportunity(event,{null}) {_,_->shows++})
        coordinator.onOpportunity(event(),{null}) {_,_->shows++}; assertEquals(1,shows); assertEquals(0L,frequency.shownCount)
    }
    @Test fun blockedHostConsumesEventWithoutResettingDue() {
        var now=0L; val clock=MonotonicClock { now }; val frequency=InterstitialFrequencyPolicy(clock)
        val coordinator=InterstitialCoordinator(frequency,clock,load={ success:(String)->Unit,_ -> success("ad") })
        coordinator.updateEligibility(true); frequency.setForeground(true); now=60_000
        val event=event()
        assertEquals(InterstitialDecision.DueButImeVisible,coordinator.onOpportunity(event,{InterstitialDecision.DueButImeVisible}) {_,_->fail<Unit>()})
        assertEquals(InterstitialDecision.NaturalBreakRejected,coordinator.onOpportunity(event,{null}) {_,_->fail<Unit>()})
        assertTrue(frequency.isDue); assertEquals(0L,frequency.shownCount)
    }
}
