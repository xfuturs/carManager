package com.carmanager.app.core.ads

/** Petit coeur de cycle de vie testable sans SDK/Activity ; tous les appels sont serialises sur main. */
internal class InterstitialCoordinator<Ad : Any>(
    val frequency: InterstitialFrequencyPolicy,
    private val clock: MonotonicClock,
    private val load: (loaded: (Ad) -> Unit, failed: () -> Unit) -> Unit
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

    fun updateEligibility(value: Boolean) {
        if (eligible == value) return
        eligible = value
        if (!value) {
            epoch++
            cached = null
            // Le SDK n'expose pas d'annulation load : garder la garde jusqu'au callback stale.
        } else preload()
    }

    fun detachHost() { updateEligibility(false) }

    fun preload() {
        if (!eligible || !frequency.hasCapacity || loadingId != null || showingId != null ||
            cached != null || clock.now() < nextLoadAt) return
        val id = ++sequence
        val requestEpoch = epoch
        loadingId = id
        try {
            load({ ad ->
                if (loadingId == id) {
                    loadingId = null
                    if (requestEpoch == epoch && eligible && frequency.hasCapacity) {
                        cached = ad
                        loadedAt = clock.now()
                    }
                }
            }, {
                if (loadingId == id) {
                    loadingId = null
                    nextLoadAt = clock.now() + RETRY_BACKOFF_MS
                }
            })
        } catch (_: Exception) {
            if (loadingId == id) {
                loadingId = null
                nextLoadAt = clock.now() + RETRY_BACKOFF_MS
            }
        }
    }

    /** Aucun callback present ni host n'est conserve : appel synchrone et retour immediat. */
    fun onOpportunity(canPresent: Boolean, present: (Ad, DisplayCallbacks) -> Unit) {
        if (!eligible || !canPresent || showingId != null || !frequency.hasCapacity) return
        frequency.onEligibleTransition()
        if (cached != null && clock.now() - loadedAt >= CACHE_MAX_AGE_MS) cached = null
        val ad = cached
        if (!frequency.canShow(eligible) || ad == null) { preload(); return }
        cached = null
        val id = ++sequence
        showingId = id
        var counted = false
        val callbacks = DisplayCallbacks(
            shown = {
                if (showingId == id && !counted) {
                    counted = true
                    frequency.onActuallyDisplayed()
                }
            },
            dismissed = {
                if (showingId == id) {
                    showingId = null
                    preload()
                }
            },
            failed = {
                if (showingId == id) {
                    showingId = null
                    nextLoadAt = clock.now() + RETRY_BACKOFF_MS
                }
            }
        )
        try { present(ad, callbacks) }
        catch (_: Exception) { callbacks.failed() }
    }
}
