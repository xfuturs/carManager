package com.carmanager.app.core.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.carmanager.app.core.domain.model.PremiumEntitlement
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

/** Process-scoped, application context uniquement ; aucune Activity ni lambda de host stockee. */
@Singleton
class InterstitialAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val initializer: MobileAdsInitializer,
    private val premium: PremiumRepository
) {
    private val clock = MonotonicClock { SystemClock.elapsedRealtime() }
    private val coordinator = InterstitialCoordinator<InterstitialAd>(InterstitialFrequencyPolicy(clock), clock, ::load)

    private fun liveAdsAllowed(): Boolean {
        val state = premium.state.value
        return initializer.ready.value && adsEligible(
            UserMessagingPlatform.getConsentInformation(context).canRequestAds(), state.isPremium
        ) && !state.isLoading && !state.isPurchasing && !state.acknowledgementPending &&
            state.entitlement != PremiumEntitlement.PENDING && state.issue == null
    }

    @MainThread
    fun updateEligibility(globalAdsEligible: Boolean) {
        coordinator.updateEligibility(globalAdsEligible && liveAdsAllowed())
    }

    @MainThread
    fun detachHost() { coordinator.detachHost() }

    @MainThread
    fun onNavigationOpportunity(activity: Activity) {
        if (!liveAdsAllowed()) { coordinator.updateEligibility(false); return }
        val view = activity.window.decorView
        val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) != false
        coordinator.onOpportunity(!activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() && !imeVisible) { ad, callbacks ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() { callbacks.shown() }
                override fun onAdDismissedFullScreenContent() {
                    ad.fullScreenContentCallback = null
                    if (!liveAdsAllowed()) coordinator.updateEligibility(false)
                    callbacks.dismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    ad.fullScreenContentCallback = null
                    callbacks.failed()
                }
            }
            ad.show(activity)
        }
    }

    private fun load(loaded: (InterstitialAd) -> Unit, failed: () -> Unit) {
        if (!liveAdsAllowed()) { failed(); return }
        InterstitialAd.load(context.applicationContext, AdsConfig.interstitialAdUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (!liveAdsAllowed()) coordinator.updateEligibility(false)
                    loaded(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) { failed() }
            })
    }
}
