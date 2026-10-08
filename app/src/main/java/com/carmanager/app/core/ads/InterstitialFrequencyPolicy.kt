package com.carmanager.app.core.ads

/** Temps monotone uniquement ; aucune horloge calendrier ni persistance. */
fun interface MonotonicClock { fun now(): Long }

/** Motifs sans identifiant ni donnée utilisateur, utilisés uniquement pour le diagnostic debug. */
internal enum class InterstitialDecision {
    NotDueYet, DueButUnsafeRoute, DueButModalActive, DueButImeVisible,
    DueButActivityUnavailable, DueButTransitionActive, DueButBlockingFlow,
    Premium, ConsentUnavailable, SdkUnavailable, PremiumUnsettled, AdsUnavailable, NotLoaded, LoadBackoff, Showing,
    EligibleToShow, AdLoaded, LoadFailed, ShowFailed, ActuallyDisplayed
}

internal fun interstitialGateDecision(ready: Boolean, consent: Boolean, premium: Boolean,
    premiumSettled: Boolean): InterstitialDecision = when {
    premium -> InterstitialDecision.Premium
    !premiumSettled -> InterstitialDecision.PremiumUnsettled
    !consent -> InterstitialDecision.ConsentUnavailable
    !ready -> InterstitialDecision.SdkUnavailable
    else -> InterstitialDecision.EligibleToShow
}

/** Le délai dépend exclusivement des annonces réellement affichées dans ce processus. */
internal fun delayAfterDisplayedCount(displayedCount: Long): Long {
    require(displayedCount >= 0)
    return 60_000L + displayedCount.coerceAtMost(6L) * 20_000L
}

/** Une instance par processus ; seules les périodes de premier plan éligible s'accumulent. */
class InterstitialFrequencyPolicy(private val clock: MonotonicClock) {
    private var accumulatedForegroundMs = 0L
    private var foregroundSegmentStart: Long? = null
    private var lastDisplayForegroundMs = 0L
    var shownCount = 0L
        private set
    val isForeground: Boolean get() = foregroundSegmentStart != null
    val foregroundMs: Long get() = accumulatedForegroundMs +
        (foregroundSegmentStart?.let { (clock.now() - it).coerceAtLeast(0L) } ?: 0L)
    val nextDelayMs: Long get() = delayAfterDisplayedCount(shownCount)
    val remainingMs: Long get() = (nextDelayMs - (foregroundMs - lastDisplayForegroundMs)).coerceAtLeast(0L)
    val isDue: Boolean get() = remainingMs == 0L

    fun setForeground(value: Boolean) {
        if (value == isForeground) return
        if (value) foregroundSegmentStart = clock.now()
        else {
            accumulatedForegroundMs = foregroundMs
            foregroundSegmentStart = null
        }
    }

    fun canShow(adsEligible: Boolean): Boolean = decision(adsEligible) == InterstitialDecision.EligibleToShow

    internal fun decision(adsEligible: Boolean): InterstitialDecision {
        return when {
            !adsEligible -> InterstitialDecision.AdsUnavailable
            !isForeground -> InterstitialDecision.DueButActivityUnavailable
            !isDue -> InterstitialDecision.NotDueYet
            else -> InterstitialDecision.EligibleToShow
        }
    }

    /** Seul onAdShowed consomme l'échéance ; le temps du plein écran est suspendu. */
    fun onActuallyDisplayed() {
        if (shownCount < Long.MAX_VALUE) shownCount++
        lastDisplayForegroundMs = foregroundMs
        setForeground(false)
    }
}
