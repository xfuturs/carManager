package com.carmanager.app.premium

import com.carmanager.app.core.ads.*
import com.carmanager.app.core.data.billing.*
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.model.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PremiumAdsSettlementTest {
    @Test fun `verified empty ownership allows Ads even when product offer is unavailable`() = runTest {
        val gateway=FakePlayBillingGateway().apply { offerReply=BillingReply(BillingOutcome.OK) }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent()
        val state=repository.state.value
        assertEquals(PremiumEntitlement.FREE,state.entitlement)
        assertEquals(PremiumIssue.OFFER_UNAVAILABLE,state.issue)
        val entitlement=premiumAdsEntitlement(state)
        val decision=interstitialGateDecision(true,true,state.isPremium,
            entitlement==AdsEntitlement.AllowedFree)
        assertEquals(InterstitialDecision.EligibleToShow,decision)
    }
    @Test fun `failed ownership query cannot allow the banner as if Free was verified`() = runTest {
        val gateway=FakePlayBillingGateway().apply { purchaseReply=BillingReply(BillingOutcome.UNAVAILABLE) }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent()
        assertFalse(repository.state.value.isLoading)
        assertEquals(PremiumIssue.STORE_UNAVAILABLE,repository.state.value.issue)
        assertFalse(adsEligible(true,premiumAdsEntitlement(repository.state.value)))
    }
    @Test fun `initial state and suspended ownership query remain unsettled`() = runTest {
        val gate=CompletableDeferred<Unit>()
        val gateway=FakePlayBillingGateway().apply { queryHandler={ gate.await(); purchaseReply } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        repository.initialize(); runCurrent()
        assertTrue(gateway.isReady); assertFalse(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        gate.complete(Unit); runCurrent()
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `ownership Free is published before offer query completes`() = runTest {
        val gate=CompletableDeferred<Unit>()
        val gateway=FakePlayBillingGateway().apply { offerHandler={ gate.await(); offerReply } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent()
        assertTrue(repository.state.value.ownershipVerified); assertFalse(repository.state.value.isLoading)
        assertNull(repository.state.value.offer)
        assertTrue(adsEligible(true,premiumAdsEntitlement(repository.state.value)))
        gate.complete(Unit); runCurrent()
        assertTrue(repository.state.value.canPurchase)
    }
    @Test fun `purchased ownership blocks both formats even when acknowledgment is pending`() = runTest {
        val gate=CompletableDeferred<Unit>()
        val gateway=FakePlayBillingGateway().apply {
            purchaseReply=BillingReply(BillingOutcome.OK,listOf(premiumPurchase(acknowledged=false)))
            ackHandler={ gate.await(); BillingOutcome.OK }
        }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent()
        assertTrue(repository.isPremium.value); assertTrue(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedPremium,premiumAdsEntitlement(repository.state.value))
        assertFalse(adsEligible(true,premiumAdsEntitlement(repository.state.value)))
        gate.complete(Unit); runCurrent(); assertTrue(repository.isPremium.value)
    }
    @Test fun `pending ownership remains unsettled without granting Premium`() = runTest {
        val gateway=FakePlayBillingGateway().apply { purchaseReply=BillingReply(BillingOutcome.OK,listOf(premiumPurchase(PlayPurchaseState.PENDING))) }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        assertFalse(repository.isPremium.value); assertTrue(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        assertFalse(adsEligible(true,premiumAdsEntitlement(repository.state.value)))
    }
    @Test fun `disconnect before ownership result cannot publish verified Free`() = runTest {
        val gate=CompletableDeferred<Unit>()
        val gateway=FakePlayBillingGateway().apply { queryHandler={ gate.await(); purchaseReply } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        gateway.disconnect(); gate.complete(Unit); runCurrent()
        assertFalse(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `disconnect reconnect and successful empty query settle again without retry spin`() = runTest {
        val gateway=FakePlayBillingGateway()
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        gateway.disconnect(); runCurrent(); advanceTimeBy(60_000); runCurrent()
        assertEquals(1,gateway.connectCalls); assertFalse(repository.state.value.ownershipVerified)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(2,gateway.connectCalls); assertTrue(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `failed refresh retains known Premium and never downgrades to Free`() = runTest {
        val gateway=FakePlayBillingGateway().apply { purchaseReply=BillingReply(BillingOutcome.OK,listOf(premiumPurchase())) }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        gateway.purchaseReply=BillingReply(BillingOutcome.ERROR); repository.checkPremiumStatus(); runCurrent()
        assertTrue(repository.isPremium.value); assertFalse(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedPremium,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `failed refresh invalidates known Free and later success recovers`() = runTest {
        val gateway=FakePlayBillingGateway(); val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent(); gateway.purchaseReply=BillingReply(BillingOutcome.UNAVAILABLE)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        gateway.purchaseReply=BillingReply(BillingOutcome.OK,emptyList()); repository.checkPremiumStatus(); runCurrent()
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `operation cancellation is fail closed and does not permanently kill restore worker`() = runTest {
        val gateway=FakePlayBillingGateway().apply { queryHandler={ throw CancellationException("synthetic cancellation") } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        assertFalse(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        gateway.queryHandler=null; repository.checkPremiumStatus(); runCurrent()
        assertEquals(2,gateway.queryCalls); assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `scope cancellation never falsely settles Free`() = runTest {
        val gate=CompletableDeferred<Unit>(); val childScope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val gateway=FakePlayBillingGateway().apply { queryHandler={ gate.await(); purchaseReply } }
        val repository=PremiumRepositoryImpl(gateway,childScope); repository.initialize(); runCurrent()
        childScope.cancel(); gate.complete(Unit); runCurrent()
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `purchase failures are not ownership failures after a successful query`() = runTest {
        val gateway=FakePlayBillingGateway(); val repository=PremiumRepositoryImpl(gateway,backgroundScope)
        repository.initialize(); runCurrent(); gateway.update(BillingOutcome.ERROR); runCurrent()
        assertEquals(PremiumIssue.PURCHASE_FAILED,repository.state.value.issue)
        assertTrue(repository.state.value.ownershipVerified)
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `verified Free still obeys UMP and diagnostics expose no purchase representation`() = runTest {
        val events=mutableListOf<PremiumDiagnostic>()
        val gateway=FakePlayBillingGateway(); val repository=PremiumRepositoryImpl(gateway,backgroundScope,PremiumDiagnostics { event,_ -> events+=event })
        repository.initialize(); runCurrent()
        assertFalse(adsEligible(false,premiumAdsEntitlement(repository.state.value)))
        assertTrue(PremiumDiagnostic.OwnershipFree in events); assertTrue(PremiumDiagnostic.StateFree in events)
    }
    @Test fun `offer exception or operation cancellation does not erase verified empty ownership`() = runTest {
        for(error in listOf(IllegalStateException("synthetic offer error"),CancellationException("synthetic offer cancellation"))) {
            val gateway=FakePlayBillingGateway().apply { offerHandler={ throw error } }
            val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
            assertEquals(PremiumIssue.OFFER_UNAVAILABLE,repository.state.value.issue)
            assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
        }
    }
    @Test fun `disconnect during suspended offer lookup closes Ads immediately`() = runTest {
        val gate=CompletableDeferred<Unit>(); val gateway=FakePlayBillingGateway().apply { offerHandler={ gate.await(); offerReply } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
        gateway.disconnect(); runCurrent()
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        gate.complete(Unit); runCurrent()
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
    }
    @Test fun `ownership response from before disconnect cannot settle after reconnect`() = runTest {
        val gate=CompletableDeferred<Unit>(); val gateway=FakePlayBillingGateway().apply { queryHandler={ gate.await(); purchaseReply } }
        val repository=PremiumRepositoryImpl(gateway,backgroundScope); repository.initialize(); runCurrent()
        gateway.disconnect(); runCurrent(); gateway.isReady=true; gate.complete(Unit); runCurrent()
        assertEquals(AdsEntitlement.BlockedUnsettled,premiumAdsEntitlement(repository.state.value))
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(AdsEntitlement.AllowedFree,premiumAdsEntitlement(repository.state.value))
    }
}
