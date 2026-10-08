package com.carmanager.app.core.ads

/** Petit coeur de cycle de vie testable sans SDK/Activity ; tous les appels sont serialises sur main. */
internal class InterstitialCoordinator<Ad : Any>(
    val frequency: InterstitialFrequencyPolicy,
    private val clock: MonotonicClock,
    private val load: (loaded: (Ad) -> Unit, failed: () -> Unit) -> Unit,
    private val changed: () -> Unit = {}
) {
    companion object {
        const val RETRY_BACKOFF_MS = 60_000L
        const val CACHE_MAX_AGE_MS = 3_600_000L
    }
    internal class DisplayCallbacks(val shown: () -> Unit, val dismissed: () -> Unit, val failed: () -> Unit)
    private var eligible = false
    private var epoch = 0L
    private var sequence = 0L
    private var loadingId: Long? = null
    private var cached: Ad? = null
    private var loadedAt = 0L
    private var nextLoadAt = 0L
    private var showingId: Long? = null
    val isShowing: Boolean get() = showingId != null
    val retryDelayMs: Long? get() = if (eligible && !isShowing && loadingId == null && cached == null && clock.now() < nextLoadAt)
        nextLoadAt - clock.now() else null

    /** Un dû non présentable attend un événement, tandis que la cadence continue sur les formulaires. */
    fun nextWakeDelay(dueBlocked: Boolean): Long? {
        if (!eligible || !frequency.isForeground || isShowing) return null
        val remaining = frequency.remainingMs
        if (remaining > 0L) return listOfNotNull(remaining, retryDelayMs).minOrNull()
        return if (dueBlocked) null else retryDelayMs
    }

    fun updateEligibility(value: Boolean) {
        if (eligible == value) return
        eligible = value
        if (!value) {
            frequency.setForeground(false)
            epoch++
            cached = null
            // Le SDK n'expose pas d'annulation load : garder la garde jusqu'au callback stale.
        } else preload()
        changed()
    }

    fun detachHost() { frequency.setForeground(false); updateEligibility(false) }

    fun preload() {
        if (!eligible || loadingId != null || showingId != null ||
            cached != null || clock.now() < nextLoadAt) return
        val id = ++sequence
        val requestEpoch = epoch
        loadingId = id
        try {
            load({ ad ->
                if (loadingId == id) {
                    loadingId = null
                    if (requestEpoch == epoch && eligible) {
                        cached = ad
                        loadedAt = clock.now()
                    }
                    changed()
                }
            }, {
                if (loadingId == id) {
                    loadingId = null
                    nextLoadAt = clock.now() + RETRY_BACKOFF_MS
                    changed()
                }
            })
        } catch (_: Exception) {
            if (loadingId == id) {
                loadingId = null
                nextLoadAt = clock.now() + RETRY_BACKOFF_MS
                changed()
            }
        }
    }

    /** Aucun callback present ni host n'est conserve : appel synchrone et retour immediat. */
    fun onOpportunity(canPresent: Boolean, present: (Ad, DisplayCallbacks) -> Unit): InterstitialDecision {
        if (!eligible) return InterstitialDecision.AdsUnavailable
        if (!canPresent) return InterstitialDecision.DueButActivityUnavailable
        if (showingId != null) return InterstitialDecision.Showing
        if (cached != null && clock.now() - loadedAt >= CACHE_MAX_AGE_MS) cached = null
        val ad = cached
        val decision = frequency.decision(eligible)
        if (decision != InterstitialDecision.EligibleToShow || ad == null) {
            val reason = if (decision != InterstitialDecision.EligibleToShow) decision
                else if (clock.now() < nextLoadAt) InterstitialDecision.LoadBackoff else InterstitialDecision.NotLoaded
            preload()
            return reason
        }
        cached = null
        val id = ++sequence
        showingId = id
        frequency.setForeground(false)
        var counted = false
        val callbacks = DisplayCallbacks(
            shown = {
                if (showingId == id && !counted) {
                    counted = true
                    frequency.onActuallyDisplayed()
                    changed()
                }
            },
            dismissed = {
                if (showingId == id) {
                    showingId = null
                    preload()
                    changed()
                }
            },
            failed = {
                if (showingId == id) {
                    showingId = null
                    nextLoadAt = clock.now() + RETRY_BACKOFF_MS
                    changed()
                }
            }
        )
        try { present(ad, callbacks) }
        catch (_: Exception) { callbacks.failed(); return InterstitialDecision.ShowFailed }
        return InterstitialDecision.EligibleToShow
    }
}
