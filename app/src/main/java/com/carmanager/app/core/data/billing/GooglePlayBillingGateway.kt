package com.carmanager.app.core.data.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.android.billingclient.api.*
import com.carmanager.app.core.domain.model.PREMIUM_PRODUCT_ID
import com.carmanager.app.core.domain.model.PremiumOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GooglePlayBillingGateway @Inject constructor(@ApplicationContext context: Context) :
    PlayBillingGateway, PurchasesUpdatedListener {
    private val updates = Channel<BillingEvent>(Channel.UNLIMITED)
    override val events = updates.receiveAsFlow()
    // Une seule construction par singleton ; aucun contexte Activity conservé.
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    override val isReady: Boolean get() = client.isReady

    private class OfferSnapshot(val details: ProductDetails, val offer: PremiumOffer, val token: String, val fetchedAt: Long)
    private var snapshot: OfferSnapshot? = null

    override suspend fun connect(): BillingOutcome {
        if (client.isReady) return BillingOutcome.OK
        return awaitReply<Unit>("connect") { complete ->
            if (client.connectionState == BillingClient.ConnectionState.CONNECTING) {
                complete(BillingReply(BillingOutcome.UNAVAILABLE))
            } else client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    complete(BillingReply(outcome("connect", result), Unit))
                }
                override fun onBillingServiceDisconnected() {
                    snapshot = null
                    updates.trySend(BillingEvent.Disconnected)
                    // Pas de boucle startConnection ; prochain resume/refresh ou reconnexion SDK.
                }
            })
        }.outcome
    }

    override suspend fun queryPurchases(): BillingReply<List<PlayPurchase>> = awaitReply("ownership") { complete ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            complete(BillingReply(outcome("ownership", result), purchases.map(::purchase)))
        }
    }

    override suspend fun queryOffer(): BillingReply<PremiumOffer> {
        snapshot = null
        val reply = awaitReply<QueryProductDetailsResult>("offer") { complete ->
            val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, details ->
                complete(BillingReply(outcome("offer", result), details))
            }
        }
        if (reply.outcome != BillingOutcome.OK) return BillingReply(reply.outcome)
        val details = reply.data?.productDetailsList?.filter {
            it.productId == PREMIUM_PRODUCT_ID && it.productType == BillingClient.ProductType.INAPP
        }?.singleOrNull() ?: return BillingReply(BillingOutcome.OK)
        val options = details.oneTimePurchaseOfferDetailsList.orEmpty().map {
            OneTimeOption(it.formattedPrice, it.offerToken.orEmpty(), it.offerId, it.rentalDetails != null,
                it.preorderDetails != null, it.discountDisplayInfo != null)
        }
        val selected = selectPremiumOption(details.productId, details.productType, options)
            ?: return BillingReply(BillingOutcome.OK)
        val offer = PremiumOffer(details.productId, selected.formattedPrice)
        snapshot = OfferSnapshot(details, offer, selected.offerToken, SystemClock.elapsedRealtime())
        return BillingReply(BillingOutcome.OK, offer)
    }

    override fun launchPurchase(activity: Activity, expectedOffer: PremiumOffer): BillingOutcome {
        val current = snapshot
        if (!client.isReady || current == null || current.offer != expectedOffer ||
            !isPremiumOfferFresh(current.fetchedAt, SystemClock.elapsedRealtime())) {
            snapshot = null
            return BillingOutcome.UNAVAILABLE
        }
        if (activity.isFinishing || activity.isDestroyed) return BillingOutcome.ERROR
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(current.details)
            .setOfferToken(current.token).build()
        return try {
            outcome("launch", client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(product)).build()))
        } catch (_: Exception) {
            Log.w("PlayBilling", "launch: failure")
            BillingOutcome.ERROR
        }
    }

    override suspend fun acknowledge(token: String): BillingOutcome = awaitReply<Unit>("acknowledge") { complete ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            complete(BillingReply(outcome("acknowledge", result), Unit))
        }
    }.outcome

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        updates.trySend(BillingEvent.PurchasesUpdated(outcome("purchaseUpdate", result), purchases.orEmpty().map(::purchase)))
    }

    private fun purchase(value: Purchase) = PlayPurchase(value.products, when (value.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
        else -> PlayPurchaseState.UNSPECIFIED
    }, value.isAcknowledged, value.purchaseToken)

    private fun outcome(operation: String, result: BillingResult): BillingOutcome {
        val value = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> BillingOutcome.OK
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingOutcome.CANCELED
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingOutcome.ALREADY_OWNED
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingOutcome.UNAVAILABLE
            else -> BillingOutcome.ERROR
        }
        if (value != BillingOutcome.OK && value != BillingOutcome.CANCELED) {
            // Ni debugMessage, ni token, order ID, JSON ou identité de compte.
            Log.w("PlayBilling", "$operation: code=${result.responseCode}")
        }
        return value
    }

    private suspend fun <T> awaitReply(operation: String, request: ((BillingReply<T>) -> Unit) -> Unit): BillingReply<T> =
        withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    request { if (continuation.isActive) continuation.resume(it) }
                } catch (_: Exception) {
                    Log.w("PlayBilling", "$operation: failure")
                    if (continuation.isActive) continuation.resume(BillingReply(BillingOutcome.ERROR))
                }
            }
        } ?: BillingReply(BillingOutcome.UNAVAILABLE)
}
