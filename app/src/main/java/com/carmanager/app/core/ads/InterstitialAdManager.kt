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
    private val coordinator = InterstitialCoordinator<InterstitialAd>(InterstitialFrequencyPolicy(clock), clock, ::load,
        changed = { _changes.tryEmit(Unit) }, diagnostic = { logInterstitialDecision(it) })
    private var globalAdsEligible = false
    private var hostGeneration = 0L
    private var hostBlocked = false

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
        if (changed && !globalAdsEligible) hostGeneration++
        this.globalAdsEligible = globalAdsEligible
        coordinator.updateEligibility(globalAdsEligible && liveAdsAllowed())
        if (changed) _changes.tryEmit(Unit)
        if (!globalAdsEligible || !liveAdsAllowed()) {
            val reason = liveDecision()
            logInterstitialDecision(if (reason == InterstitialDecision.EligibleToShow) InterstitialDecision.AdsUnavailable else reason)
        }
    }

    @MainThread
    fun detachHost() { hostGeneration++; coordinator.detachHost() }

    @MainThread
    internal fun pauseForeground() { coordinator.frequency.setForeground(false) }

    @MainThread
    internal fun updateForeground(state: InterstitialPresentability) {
        val allowed = globalAdsEligible && liveAdsAllowed()
        val blocked = !allowed || !state.hostResumed || !state.windowFocused || state.modalActive || state.blockingFlow
        if (blocked && !hostBlocked) hostGeneration++
        hostBlocked = blocked
        coordinator.updateEligibility(allowed)
        coordinator.frequency.setForeground(allowed && state.interactiveForeground && !coordinator.isShowing)
    }

    /** Le scheduler ne sait que comptabiliser, précharger et annoncer l'éligibilité. */
    internal fun nextWakeDelay(): Long? = coordinator.nextWakeDelay(dueBlocked = false)

    private fun record(reason: InterstitialDecision, code: Int? = null) { logInterstitialDecision(reason, code) }

    @MainThread
    internal fun onClockOrHostChanged(state: InterstitialPresentability) {
        updateForeground(state)
        coordinator.preload()
        record(when {
            !globalAdsEligible -> InterstitialDecision.AdsUnavailable
            !liveAdsAllowed() -> liveDecision()
            coordinator.isShowing -> InterstitialDecision.Showing
            coordinator.frequency.isDue -> InterstitialDecision.DueAwaitingNaturalBreak
            else -> InterstitialDecision.NotDueYet
        })
    }

    internal fun captureHostGeneration(): Long = hostGeneration

    internal fun createNaturalBreak(workflow: NaturalBreakWorkflow, completedAtMs: Long, destinationId: String,
        capturedGeneration: Long = hostGeneration) = NaturalBreakOpportunity(workflow, completedAtMs, destinationId, capturedGeneration)

    @MainThread
    internal fun userInteraction() { hostGeneration++ }

    @MainThread
    internal fun discardCompletion(expired: Boolean = false) {
        record(if (expired) InterstitialDecision.NaturalBreakExpired else InterstitialDecision.NaturalBreakRejected)
    }

    @MainThread
    internal fun onNaturalBreak(activity: ComponentActivity, state: InterstitialPresentability, opportunity: NaturalBreakOpportunity) {
        val gate = liveDecision()
        if (!globalAdsEligible || gate != InterstitialDecision.EligibleToShow) coordinator.updateEligibility(false)
        val view = activity.window.decorView
        val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) != false
        val fresh = state.copy(
            hostResumed = state.hostResumed && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !activity.isFinishing && !activity.isDestroyed,
            windowFocused = state.windowFocused && activity.hasWindowFocus(),
            imeVisible = state.imeVisible || imeVisible
        )
        updateForeground(fresh)
        val reason = coordinator.onOpportunity(opportunity, blocker = {
            when {
                opportunity.hostGeneration != hostGeneration || opportunity.destinationId != fresh.destinationId ||
                    fresh.route != com.carmanager.app.core.ui.navigation.Screen.FuelList.route -> InterstitialDecision.NaturalBreakRejected
                gate != InterstitialDecision.EligibleToShow -> gate
                !globalAdsEligible -> InterstitialDecision.AdsUnavailable
                else -> fresh.blocker()
            }
        }) { ad, callbacks ->
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
