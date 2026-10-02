package com.carmanager.app.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

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
