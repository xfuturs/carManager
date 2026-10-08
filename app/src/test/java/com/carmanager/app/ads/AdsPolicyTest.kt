package com.carmanager.app.ads

import com.carmanager.app.core.ads.*
import com.carmanager.app.core.data.billing.BillingOutcome
import com.carmanager.app.core.data.billing.BillingReply
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry
import com.carmanager.app.premium.FakePlayBillingGateway
import com.carmanager.app.premium.premiumPurchase
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdsPolicyTest {
    @Test fun `Free requests Ads only when UMP permits and Premium always blocks`() {
        assertTrue(adsEligible(true, false))
        assertFalse(adsEligible(false, false))
        assertFalse(adsEligible(true, true))
        assertFalse(adsEligible(false, true))
    }
    @Test fun `Premium to Free transition follows current consent after Play refresh`() = runTest {
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        premium.initialize(); runCurrent()
        assertFalse(adsEligible(true, premium.isPremium.value))
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, emptyList())
        premium.checkPremiumStatus(); runCurrent()
        assertTrue(adsEligible(true, premium.isPremium.value))
        assertFalse(adsEligible(false, premium.isPremium.value))
    }
    @Test fun `workspace switching does not alter Play based ad eligibility`() = runTest {
        val gateway = FakePlayBillingGateway()
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        val workspace = WorkspaceSession(TestDeletionRegistry())
        premium.initialize(); runCurrent()
        for (uid in listOf(null, "firebase-A", "firebase-B", null)) {
            workspace.completeBootstrap()
            runCurrent()
            assertTrue(adsEligible(true, premium.isPremium.value))
            assertEquals(1, gateway.queryCalls)
        }
    }
    @Test fun `privacy options are actionable only when UMP requires them`() {
        assertTrue(AdsConsentState(false, AdsPrivacyOptions.REQUIRED).showPrivacyOptions)
        assertFalse(AdsConsentState(true, AdsPrivacyOptions.NOT_REQUIRED).showPrivacyOptions)
        assertFalse(AdsConsentState(true, AdsPrivacyOptions.UNKNOWN).showPrivacyOptions)
    }
    @Test fun `initialization waits for consent and Free eligibility`() {
        val gate = AdsInitializationGate()
        var calls = 0
        val initialize: (() -> Unit) -> Unit = { calls++; it() }
        gate.initializeIfEligible(false, false, initialize)
        gate.initializeIfEligible(true, true, initialize)
        assertEquals(0, calls)
        assertFalse(gate.ready.value)
        gate.initializeIfEligible(true, false, initialize)
        assertEquals(1, calls)
        assertTrue(gate.ready.value)
    }
    @Test fun `multiple consent callbacks and recreated Activity callers initialize once per process`() {
        val gate = AdsInitializationGate()
        var calls = 0
        var complete: (() -> Unit)? = null
        repeat(10) { gate.initializeIfEligible(true, false) { callback -> calls++; complete = callback } }
        assertEquals(1, calls)
        assertFalse(gate.ready.value)
        complete!!(); complete!!()
        gate.initializeIfEligible(true, false) { fail("duplicate initialization") }
        assertTrue(gate.ready.value)
        assertEquals(1, calls)
    }
    @Test fun `initialized SDK never replaces current consent or Premium gate`() {
        val gate = AdsInitializationGate()
        gate.initializeIfEligible(true, false) { it() }
        assertTrue(gate.ready.value)
        assertFalse(gate.ready.value && adsEligible(false, false))
        assertFalse(gate.ready.value && adsEligible(true, true))
    }
}
