package com.carmanager.app.core.ads

import androidx.activity.ComponentActivity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.carmanager.app.BuildConfig
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal fun logInterstitialDecision(reason: InterstitialDecision, code: Int? = null) {
    if (BuildConfig.DEBUG) Log.d("Ads", "interstitial: decision=${reason.name}" + (code?.let { " code=$it" } ?: ""))
}

/** Process-scoped, application context uniquement ; aucune Activity ni lambda de host stockee. */
@Singleton
class InterstitialAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val initializer: MobileAdsInitializer,
    private val premium: PremiumRepository,
    private val entitlementGate: AdsEntitlementGate
) {
    private val clock = MonotonicClock { SystemClock.elapsedRealtime() }
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val changes = _changes.asSharedFlow()
    private val coordinator = InterstitialCoordinator<InterstitialAd>(InterstitialFrequencyPolicy(clock), clock, ::load) { _changes.tryEmit(Unit) }
    private var globalAdsEligible = false
    private var lastDecision = InterstitialDecision.AdsUnavailable

    private fun liveDecision(): InterstitialDecision {
        val entitlement = entitlementGate.decision(premium.state.value)
        return interstitialGateDecision(initializer.ready.value,
            UserMessagingPlatform.getConsentInformation(context).canRequestAds(),
            entitlement == AdsEntitlement.BlockedPremium, entitlement == AdsEntitlement.AllowedFree)
    }
    private fun liveAdsAllowed() = liveDecision() == InterstitialDecision.EligibleToShow

    @MainThread
    fun updateEligibility(globalAdsEligible: Boolean) {
        val changed = this.globalAdsEligible != globalAdsEligible
        this.globalAdsEligible = globalAdsEligible
        coordinator.updateEligibility(globalAdsEligible && liveAdsAllowed())
        if (changed) _changes.tryEmit(Unit)
        if (!globalAdsEligible || !liveAdsAllowed()) {
            val reason = liveDecision()
            logInterstitialDecision(if (reason == InterstitialDecision.EligibleToShow) InterstitialDecision.AdsUnavailable else reason)
        }
    }

    @MainThread
    fun detachHost() { coordinator.detachHost() }

    @MainThread
    internal fun pauseForeground() { coordinator.frequency.setForeground(false) }

    @MainThread
    internal fun updateForeground(state: InterstitialPresentability) {
        val allowed = globalAdsEligible && liveAdsAllowed()
        coordinator.updateEligibility(allowed)
        coordinator.frequency.setForeground(allowed && state.interactiveForeground && !coordinator.isShowing)
    }

    /** Échéance cadence/backoff uniquement ; un dû bloqué attend un changement d'état. */
    internal fun nextWakeDelay(): Long? {
        if (!coordinator.frequency.isForeground || coordinator.isShowing || !globalAdsEligible || !liveAdsAllowed()) return null
        val deferredBySafety = lastDecision !in setOf(InterstitialDecision.NotLoaded, InterstitialDecision.LoadBackoff,
            InterstitialDecision.ShowFailed, InterstitialDecision.EligibleToShow)
        return coordinator.nextWakeDelay(deferredBySafety)
    }

    private fun record(reason: InterstitialDecision, code: Int? = null) { lastDecision = reason; logInterstitialDecision(reason, code) }

    @MainThread
    internal fun onTimedOpportunity(activity: ComponentActivity, state: InterstitialPresentability) {
        val gate = liveDecision()
        if (gate != InterstitialDecision.EligibleToShow) {
            coordinator.updateEligibility(false); record(gate); return
        }
        if (!globalAdsEligible) { coordinator.updateEligibility(false); record(InterstitialDecision.AdsUnavailable); return }
        val view = activity.window.decorView
        val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) != false
        // Dernière vérification réelle du host, sans faire confiance à un snapshot de composition seul.
        val fresh = state.copy(
            hostResumed = state.hostResumed && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !activity.isFinishing && !activity.isDestroyed,
            windowFocused = state.windowFocused && activity.hasWindowFocus(),
            imeVisible = state.imeVisible || imeVisible
        )
        updateForeground(fresh)
        if (coordinator.isShowing) { record(InterstitialDecision.Showing); return }
        if (!coordinator.frequency.isDue) { coordinator.preload(); record(InterstitialDecision.NotDueYet); return }
        fresh.blocker()?.let { record(it); return }
        val reason = coordinator.onOpportunity(true) { ad, callbacks ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() { callbacks.shown(); logInterstitialDecision(InterstitialDecision.ActuallyDisplayed) }
                override fun onAdDismissedFullScreenContent() {
                    ad.fullScreenContentCallback = null
                    if (!liveAdsAllowed()) coordinator.updateEligibility(false)
                    callbacks.dismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    ad.fullScreenContentCallback = null
                    callbacks.failed()
                    record(InterstitialDecision.ShowFailed, error.code)
                }
            }
            ad.show(activity)
        }
        record(reason)
    }

    private fun load(loaded: (InterstitialAd) -> Unit, failed: () -> Unit) {
        if (!liveAdsAllowed()) { failed(); return }
        InterstitialAd.load(context.applicationContext, AdsConfig.interstitialAdUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (!liveAdsAllowed()) coordinator.updateEligibility(false)
                    loaded(ad)
                    logInterstitialDecision(InterstitialDecision.AdLoaded)
                }
                override fun onAdFailedToLoad(error: LoadAdError) { failed(); logInterstitialDecision(InterstitialDecision.LoadFailed, error.code) }
            })
    }
}
