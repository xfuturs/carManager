package com.carmanager.app.core.ads

import android.content.Intent
import com.carmanager.app.core.domain.model.PremiumState
import javax.inject.Inject
import javax.inject.Singleton

/** Seule la propriété réelle vérifiée auprès de Play fait autorité en release. */
@Singleton
class AdsEntitlementGate @Inject constructor() {
    fun configure(@Suppress("UNUSED_PARAMETER") intent: Intent) = Unit
    fun decision(state: PremiumState): AdsEntitlement = premiumAdsEntitlement(state)
}
