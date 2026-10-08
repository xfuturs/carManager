package com.carmanager.app.features.dashboard

import com.carmanager.app.core.ads.adsEligible
import com.carmanager.app.core.data.billing.BillingOutcome
import com.carmanager.app.core.data.billing.BillingReply
import com.carmanager.app.core.data.billing.PlayPurchaseState
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.model.PremiumEntitlement
import com.carmanager.app.core.ui.navigation.InterstitialNavigationPolicy
import com.carmanager.app.core.ui.navigation.Screen
import com.carmanager.app.premium.FakePlayBillingGateway
import com.carmanager.app.premium.premiumPurchase
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfAccessPolicyTest {
    @Test fun `release FREE remains locked`() {
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(false))
    }

    @Test fun `release Premium retains normal PDF access`() {
        assertEquals(PdfAccess.PREMIUM, PdfAccessPolicy.resolve(true))
    }

    @Test fun `FREE has no PDF test access`() {
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(false))
    }

    @Test fun `Premium has normal PDF access only`() {
        assertEquals(PdfAccess.PREMIUM, PdfAccessPolicy.resolve(true))
    }

    @Test fun `PDF access evaluation never mutates FREE repository or calls Billing`() = runTest {
        val gateway = FakePlayBillingGateway()
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        premium.initialize(); runCurrent()
        val original = premium.state.value
        val calls = listOf(gateway.queryCalls, gateway.offerCalls, gateway.launchCalls, gateway.ackTokens.size)
        repeat(5) { assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(premium.isPremium.value)) }
        runCurrent()
        assertSame(original, premium.state.value)
        assertEquals(PremiumEntitlement.FREE, premium.state.value.entitlement)
        assertFalse(premium.isPremium.value)
        assertEquals(calls, listOf(gateway.queryCalls, gateway.offerCalls, gateway.launchCalls, gateway.ackTokens.size))
    }

    @Test fun `pending purchase remains non Premium and PDF generation stays locked`() = runTest {
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(PlayPurchaseState.PENDING, false)))
        }
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        premium.initialize(); runCurrent()
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(premium.isPremium.value))
        assertEquals(PremiumEntitlement.PENDING, premium.state.value.entitlement)
        assertFalse(premium.isPremium.value)
        assertTrue(gateway.ackTokens.isEmpty())
    }

    @Test fun `denied PDF access leaves FREE Ads eligibility and UMP requirement intact`() {
        val realPremium = false
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(realPremium))
        assertTrue(adsEligible(canRequestAds = true, isPremium = realPremium))
        assertFalse(adsEligible(canRequestAds = false, isPremium = realPremium))
    }

    @Test fun `Stats PDF remains unsafe and returning to dashboard permits deferred cadence`() {
        assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Stats.route))
        assertFalse(InterstitialNavigationPolicy.isSafeRoute(Screen.Documents.route))
        assertTrue(InterstitialNavigationPolicy.isSafeRoute(Screen.Dashboard.route))
    }

    @Test fun `policy accepts only real entitlement without build identity or persisted state`() {
        val methods = PdfAccessPolicy::class.java.declaredMethods.filter { !it.isSynthetic }
        assertEquals(1, methods.size)
        assertEquals(listOf(Boolean::class.javaPrimitiveType), methods.single().parameterTypes.toList())
        val fields = PdfAccessPolicy::class.java.declaredFields
        assertTrue(fields.all { java.lang.reflect.Modifier.isStatic(it.modifiers) && java.lang.reflect.Modifier.isFinal(it.modifiers) })
        assertTrue(fields.all { it.name == "INSTANCE" || (it.name == "\$stable" && it.type == Int::class.javaPrimitiveType) })
    }

    @Test fun `fresh repository after restart stays FREE without inheriting PDF generation access`() = runTest {
        val first = PremiumRepositoryImpl(FakePlayBillingGateway(), backgroundScope)
        first.initialize(); runCurrent()
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(first.isPremium.value))
        val restarted = PremiumRepositoryImpl(FakePlayBillingGateway(), backgroundScope)
        restarted.initialize(); runCurrent()
        assertFalse(restarted.isPremium.value)
        assertEquals(PdfAccess.LOCKED, PdfAccessPolicy.resolve(restarted.isPremium.value))
        assertEquals(PremiumEntitlement.FREE, restarted.state.value.entitlement)
    }
}
