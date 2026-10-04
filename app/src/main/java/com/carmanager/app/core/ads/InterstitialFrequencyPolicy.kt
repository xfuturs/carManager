package com.carmanager.app.core.ads

/** Temps monotone uniquement ; aucune horloge calendrier ni persistance. */
fun interface MonotonicClock { fun now(): Long }

/** Une instance par processus, partagee entre toutes ses Activity. */
class InterstitialFrequencyPolicy(private val clock: MonotonicClock) {
    companion object {
        const val MIN_SESSION_AGE_MS = 120_000L
        const val MIN_ELIGIBLE_TRANSITIONS = 2
        const val INTERSTITIAL_COOLDOWN_MS = 240_000L
        const val MAX_INTERSTITIALS_PER_SESSION = 2
    }

    private val sessionStart = clock.now()
    var eligibleTransitionCount = 0
        private set
    var shownCount = 0
        private set
    var lastShownElapsedRealtime: Long? = null
        private set
    val hasCapacity: Boolean get() = shownCount < MAX_INTERSTITIALS_PER_SESSION

    fun onEligibleTransition() {
        if (hasCapacity && eligibleTransitionCount < Int.MAX_VALUE) eligibleTransitionCount++
    }

    fun canShow(adsEligible: Boolean): Boolean {
        val now = clock.now()
        return adsEligible && hasCapacity && now - sessionStart >= MIN_SESSION_AGE_MS &&
            eligibleTransitionCount >= MIN_ELIGIBLE_TRANSITIONS &&
            (lastShownElapsedRealtime?.let { now - it >= INTERSTITIAL_COOLDOWN_MS } ?: true)
    }

    /** Appele uniquement apres onAdShowedFullScreenContent, jamais a la demande show(). */
    fun onActuallyDisplayed() {
        if (!hasCapacity) return
        shownCount++
        eligibleTransitionCount = 0
        lastShownElapsedRealtime = clock.now()
    }
}
