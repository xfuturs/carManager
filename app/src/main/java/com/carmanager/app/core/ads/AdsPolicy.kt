package com.carmanager.app.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import com.carmanager.app.core.domain.model.PremiumState
import com.carmanager.app.core.domain.model.PremiumEntitlement

enum class AdsEntitlement { AllowedFree, BlockedPremium, BlockedUnsettled }

/** Disponibilité du produit/prix distincte de la propriété vérifiée auprès de Play. */
fun premiumAdsEntitlement(state: PremiumState): AdsEntitlement = when {
    state.isPremium -> AdsEntitlement.BlockedPremium
    !state.ownershipVerified || state.isPurchasing || state.acknowledgementPending ||
        state.entitlement != PremiumEntitlement.FREE -> AdsEntitlement.BlockedUnsettled
    else -> AdsEntitlement.AllowedFree
}

fun adsEligible(canRequestAds: Boolean, entitlement: AdsEntitlement): Boolean =
    canRequestAds && entitlement == AdsEntitlement.AllowedFree

enum class AdsPrivacyOptions { REQUIRED, NOT_REQUIRED, UNKNOWN }

data class AdsConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptions: AdsPrivacyOptions = AdsPrivacyOptions.UNKNOWN
) {
    val showPrivacyOptions: Boolean get() = privacyOptions == AdsPrivacyOptions.REQUIRED
}

fun adsEligible(canRequestAds: Boolean, isPremium: Boolean): Boolean = canRequestAds && !isPremium

/** Une garde par processus ; les callbacks UMP ou Activity ne relancent pas l'initialisation. */
class AdsInitializationGate {
    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    fun initializeIfEligible(canRequestAds: Boolean, isPremium: Boolean, initialize: (() -> Unit) -> Unit) {
        if (!adsEligible(canRequestAds, isPremium) || !started.compareAndSet(false, true)) return
        initialize { _ready.value = true }
    }
}
