package com.carmanager.app.ads

import android.content.Intent
import android.util.Log
import com.carmanager.app.core.ads.*
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.ui.navigation.canShowBannerOnRoute
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class AdsEntitlementContractTest {
    @BeforeEach fun logs() { mockkStatic(Log::class); every { Log.d(any(),any()) } returns 0 }
    @AfterEach fun cleanup() { unmockkStatic(Log::class) }
    private fun configure(gate: AdsEntitlementGate, enabled: Boolean) {
        val intent=mockk<Intent>(); every { intent.getBooleanExtra("com.carmanager.ads.TEST_FREE",false) } returns enabled
        gate.configure(intent)
    }
    @Test fun `unverified and pending ownership block all existing banner surfaces`() {
        for(state in listOf(PremiumState(),PremiumState(isLoading=false,issue=PremiumIssue.STORE_UNAVAILABLE),
            PremiumState(entitlement=PremiumEntitlement.PENDING,isLoading=false,ownershipVerified=true))) {
            val gate=AdsEntitlementGate(); assertEquals(AdsEntitlement.BlockedUnsettled,gate.decision(state))
            for(route in listOf("dashboard","calculators","vehicles")) assertFalse(canShowBannerOnRoute(route,adsEligible(true,gate.decision(state))))
        }
    }
    @Test fun `Free with offer issue permits banner but Settings stays ad free`() {
        val state=PremiumState(isLoading=false,ownershipVerified=true,issue=PremiumIssue.OFFER_UNAVAILABLE)
        val allowed=adsEligible(true,AdsEntitlementGate().decision(state))
        assertTrue(allowed); assertTrue(canShowBannerOnRoute("dashboard",allowed)); assertTrue(canShowBannerOnRoute("calculators",allowed))
        assertTrue(canShowBannerOnRoute("vehicles",allowed)); assertFalse(canShowBannerOnRoute("settings",allowed))
    }
    @Test fun `real Premium blocks banner and explicit debug testing cannot override it`() {
        val gate=AdsEntitlementGate(); configure(gate,true)
        val active=PremiumState(entitlement=PremiumEntitlement.ACTIVE,isLoading=false,ownershipVerified=true)
        assertEquals(AdsEntitlement.BlockedPremium,gate.decision(active)); assertFalse(adsEligible(true,gate.decision(active)))
    }
    @Test fun `debug Free requires explicit opt in and does not mutate real Premium ownership`() {
        val state=PremiumState(isLoading=false,issue=PremiumIssue.STORE_UNAVAILABLE)
        val gate=AdsEntitlementGate(); assertEquals(AdsEntitlement.BlockedUnsettled,gate.decision(state))
        configure(gate,true); assertEquals(AdsEntitlement.AllowedFree,gate.decision(state))
        assertFalse(state.ownershipVerified); assertFalse(state.isPremium)
        configure(gate,false); assertEquals(AdsEntitlement.BlockedUnsettled,gate.decision(state))
    }
    @Test fun `debug testing remains subject to consent and purchase flow safety`() {
        val gate=AdsEntitlementGate(); configure(gate,true)
        assertFalse(adsEligible(false,gate.decision(PremiumState())))
        assertEquals(AdsEntitlement.BlockedUnsettled,gate.decision(PremiumState(isPurchasing=true)))
        assertEquals(AdsEntitlement.BlockedUnsettled,gate.decision(PremiumState(entitlement=PremiumEntitlement.PENDING)))
    }
    @Test fun `new process gate forgets debug test selection`() {
        val first=AdsEntitlementGate(); configure(first,true); assertEquals(AdsEntitlement.AllowedFree,first.decision(PremiumState()))
        assertEquals(AdsEntitlement.BlockedUnsettled,AdsEntitlementGate().decision(PremiumState()))
    }
}
