package com.carmanager.app.core.data.billing

import android.app.Activity
import com.carmanager.app.core.domain.model.PremiumOffer
import kotlinx.coroutines.flow.Flow

enum class BillingOutcome { OK, CANCELED, ALREADY_OWNED, UNAVAILABLE, ERROR }
enum class PremiumDiagnostic {
    Connecting, Ready, OwnershipQueryStart, OwnershipFree, OwnershipActive, OwnershipPending,
    OwnershipFailed, StaleOwnershipReply, OfferQueryStart, OfferUnavailable, Disconnected,
    ReplyTimeout, ReplyCanceled, StateFree, StateActive, StateUnsettled
}
fun interface PremiumDiagnostics {
    fun record(event: PremiumDiagnostic, outcome: BillingOutcome?)
    companion object { val NONE = PremiumDiagnostics { _, _ -> } }
}
enum class PlayPurchaseState { PURCHASED, PENDING, UNSPECIFIED }
data class BillingReply<T>(val outcome: BillingOutcome, val data: T? = null)

/** Token en mémoire uniquement, jamais publié dans l'état UI ni imprimé. */
class PlayPurchase(val products: List<String>, val state: PlayPurchaseState, val acknowledged: Boolean, val token: String) {
    override fun toString() = "PlayPurchase(state=$state, acknowledged=$acknowledged, token=REDACTED)"
}

sealed interface BillingEvent {
    data class PurchasesUpdated(val outcome: BillingOutcome, val purchases: List<PlayPurchase>) : BillingEvent
    data object Disconnected : BillingEvent
}

interface PlayBillingGateway {
    val events: Flow<BillingEvent>
    val isReady: Boolean
    suspend fun connect(): BillingOutcome
    suspend fun queryPurchases(): BillingReply<List<PlayPurchase>>
    suspend fun queryOffer(): BillingReply<PremiumOffer>
    suspend fun acknowledge(token: String): BillingOutcome
    /** Appel synchrone, l'Activity ne doit jamais être conservée. */
    fun launchPurchase(activity: Activity, expectedOffer: PremiumOffer): BillingOutcome
}
