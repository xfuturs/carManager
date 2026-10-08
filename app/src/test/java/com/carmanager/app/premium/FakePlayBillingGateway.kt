package com.carmanager.app.premium

import android.app.Activity
import com.carmanager.app.core.data.billing.*
import com.carmanager.app.core.domain.model.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class FakePlayBillingGateway : PlayBillingGateway {
    private val channel = Channel<BillingEvent>(Channel.UNLIMITED)
    override val events = channel.receiveAsFlow()
    override var isReady = false
    var connectResult = BillingOutcome.OK
    var purchaseReply = BillingReply(BillingOutcome.OK, emptyList<PlayPurchase>())
    var offerReply = BillingReply(BillingOutcome.OK, PremiumOffer(PREMIUM_PRODUCT_ID, "CHF 12.00"))
    var launchResult = BillingOutcome.OK
    var ackResult = BillingOutcome.OK
    var connectCalls = 0
    var queryCalls = 0
    var offerCalls = 0
    var launchCalls = 0
    var activeQueries = 0
    var maxActiveQueries = 0
    val ackTokens = mutableListOf<String>()
    var queryHandler: (suspend () -> BillingReply<List<PlayPurchase>>)? = null
    var ackHandler: (suspend (String) -> BillingOutcome)? = null
    var offerHandler: (suspend () -> BillingReply<PremiumOffer>)? = null

    override suspend fun connect(): BillingOutcome {
        connectCalls++
        isReady = connectResult == BillingOutcome.OK
        return connectResult
    }
    override suspend fun queryPurchases(): BillingReply<List<PlayPurchase>> {
        queryCalls++
        activeQueries++
        maxActiveQueries = maxOf(maxActiveQueries, activeQueries)
        return try { queryHandler?.invoke() ?: purchaseReply } finally { activeQueries-- }
    }
    override suspend fun queryOffer(): BillingReply<PremiumOffer> {
        offerCalls++
        return offerHandler?.invoke() ?: offerReply
    }
    override suspend fun acknowledge(token: String): BillingOutcome {
        ackTokens += token
        return ackHandler?.invoke(token) ?: ackResult
    }
    override fun launchPurchase(activity: Activity, expectedOffer: PremiumOffer): BillingOutcome {
        // Aucun host conservé, même dans le fake.
        launchCalls++
        return launchResult
    }
    fun update(outcome: BillingOutcome, purchases: List<PlayPurchase> = emptyList()) {
        check(channel.trySend(BillingEvent.PurchasesUpdated(outcome, purchases)).isSuccess)
    }
    fun disconnect() {
        isReady = false
        check(channel.trySend(BillingEvent.Disconnected).isSuccess)
    }
}

fun premiumPurchase(state: PlayPurchaseState = PlayPurchaseState.PURCHASED, acknowledged: Boolean = true,
    product: String = PREMIUM_PRODUCT_ID, token: String = "test-token") =
    PlayPurchase(listOf(product), state, acknowledged, token)
