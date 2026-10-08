package com.carmanager.app.core.ads

import android.content.Intent
import android.util.Log
import com.carmanager.app.core.domain.model.PremiumState
import com.carmanager.app.core.domain.model.PremiumEntitlement
import javax.inject.Inject
import javax.inject.Singleton

/** Opt-in de lancement pour les annonces Google test ; aucune licence réelle n'est modifiée. */
@Singleton
class AdsEntitlementGate @Inject constructor() {
    private var testFree = false
    fun configure(intent: Intent) {
        testFree = intent.getBooleanExtra("com.carmanager.ads.TEST_FREE", false)
        if (testFree) Log.d("Ads", "ads: entitlement=DebugTestFree")
    }
    fun decision(state: PremiumState): AdsEntitlement {
        if (testFree && !state.isPremium && state.entitlement != PremiumEntitlement.PENDING &&
            !state.isPurchasing && !state.acknowledgementPending) return AdsEntitlement.AllowedFree
        return premiumAdsEntitlement(state)
    }
}
