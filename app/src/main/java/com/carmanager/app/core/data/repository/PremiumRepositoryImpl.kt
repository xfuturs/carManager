package com.carmanager.app.core.data.repository

import android.app.Activity
import com.carmanager.app.core.data.billing.*
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.PremiumRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepositoryImpl internal constructor(
    private val gateway: PlayBillingGateway,
    private val scope: CoroutineScope,
    private val diagnostics: PremiumDiagnostics = PremiumDiagnostics.NONE
) : PremiumRepository {
    @Inject constructor(gateway: PlayBillingGateway, diagnostics: AndroidPremiumDiagnostics) :
        this(gateway, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), diagnostics)

    private val _state = MutableStateFlow(PremiumState())
    override val state = _state.asStateFlow()
    private val _isPremium = MutableStateFlow(false)
    override val isPremium = _isPremium.asStateFlow()
    private val initialized = AtomicBoolean(false)
    private val refreshQueued = AtomicBoolean(false)
    private val launching = AtomicBoolean(false)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val acknowledgedTokens = mutableSetOf<String>()
    private val failedAcknowledgements = mutableSetOf<String>()
    private var connectionEpoch = 0L

    private sealed interface Command {
        data class Refresh(val keepIssue: PremiumIssue? = null, val retryAcknowledgement: Boolean = true) : Command
        data class Event(val value: BillingEvent) : Command
    }

    override fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch { gateway.events.collect { event ->
            if (event == BillingEvent.Disconnected) {
                // Ne pas attendre une offre/ack en cours pour refermer les portes publicitaires.
                connectionEpoch++
                unavailable()
            } else commands.send(Command.Event(event))
        } }
        scope.launch {
            // Un seul worker : requêtes et acknowledgements sérialisés.
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Refresh -> try { refresh(command) } finally { refreshQueued.set(false) }
                        is Command.Event -> handleEvent(command.value)
                    }
                } catch (error: Exception) {
                    // Une opération annulée ne doit pas tuer définitivement le worker de restauration.
                    if (error is CancellationException && !currentCoroutineContext().isActive) throw error
                    unavailable()
                }
            }
        }
        enqueueRefresh()
    }

    override fun checkPremiumStatus() {
        initialize()
        enqueueRefresh()
    }

    private fun enqueueRefresh(keepIssue: PremiumIssue? = null, retryAcknowledgement: Boolean = true) {
        if (refreshQueued.compareAndSet(false, true)) commands.trySend(Command.Refresh(keepIssue, retryAcknowledgement))
    }

    override fun launchPurchase(activity: Activity) {
        val current = state.value
        if (!current.canPurchase || !launching.compareAndSet(false, true)) return
        if (!gateway.isReady) {
            unavailable()
            return
        }
        publish(current.copy(isPurchasing = true, issue = null))
        // Synchrone : aucun job ou closure ne capture cette Activity.
        val outcome = try { gateway.launchPurchase(activity, checkNotNull(current.offer)) }
            catch (_: Exception) { BillingOutcome.ERROR }
        if (outcome != BillingOutcome.OK) {
            launching.set(false)
            when (outcome) {
                BillingOutcome.ALREADY_OWNED -> {
                    publish(state.value.copy(isPurchasing = false, isLoading = true, ownershipVerified = false, offer = null, issue = null))
                    enqueueRefresh()
                }
                BillingOutcome.CANCELED -> publish(state.value.copy(isPurchasing = false, issue = null))
                BillingOutcome.UNAVAILABLE -> unavailable()
                else -> publish(state.value.copy(isPurchasing = false, issue = PremiumIssue.PURCHASE_FAILED))
            }
        }
    }

    private suspend fun refresh(command: Command.Refresh) {
        val epoch = connectionEpoch
        publish(state.value.copy(isLoading = true, ownershipVerified = false, offer = null))
        if (!gateway.isReady && gateway.connect() != BillingOutcome.OK) {
            unavailable()
            return
        }
        diagnostics.record(PremiumDiagnostic.OwnershipQueryStart, null)
        val purchases = gateway.queryPurchases()
        if (purchases.outcome != BillingOutcome.OK || purchases.data == null || !gateway.isReady || epoch != connectionEpoch) {
            // État déjà confirmé conservé en mémoire ; aucune propriété inventée au démarrage.
            diagnostics.record(PremiumDiagnostic.OwnershipFailed, purchases.outcome)
            unavailable()
            return
        }
        launching.set(false)
        publish(state.value.copy(isPurchasing = false))
        processPurchases(purchases.data, replace = true, retryAcknowledgement = command.retryAcknowledgement)
        if (state.value.entitlement == PremiumEntitlement.FREE) {
            diagnostics.record(PremiumDiagnostic.OfferQueryStart, null)
            val reply = try { gateway.queryOffer() } catch (error: Exception) {
                if (error is CancellationException && !currentCoroutineContext().isActive) throw error
                BillingReply<PremiumOffer>(BillingOutcome.ERROR)
            }
            if (epoch != connectionEpoch || !gateway.isReady) { unavailable(); return }
            val offer = reply.data?.takeIf { it.productId == PREMIUM_PRODUCT_ID && it.formattedPrice.isNotBlank() }
            publish(state.value.copy(isLoading = false, offer = if (reply.outcome == BillingOutcome.OK) offer else null,
                issue = if (reply.outcome == BillingOutcome.OK && offer != null) command.keepIssue else PremiumIssue.OFFER_UNAVAILABLE))
            if (reply.outcome != BillingOutcome.OK || offer == null) diagnostics.record(PremiumDiagnostic.OfferUnavailable, reply.outcome)
        } else publish(state.value.copy(isLoading = false))
    }

    private suspend fun handleEvent(event: BillingEvent) {
        when (event) {
            BillingEvent.Disconnected -> unavailable()
            is BillingEvent.PurchasesUpdated -> {
                launching.set(false)
                publish(state.value.copy(isPurchasing = false))
                val issue = when (event.outcome) {
                    BillingOutcome.OK -> {
                        processPurchases(event.purchases, replace = false, retryAcknowledgement = false)
                        null
                    }
                    BillingOutcome.CANCELED, BillingOutcome.ALREADY_OWNED -> null
                    BillingOutcome.UNAVAILABLE -> PremiumIssue.STORE_UNAVAILABLE
                    BillingOutcome.ERROR -> PremiumIssue.PURCHASE_FAILED
                }
                if (event.outcome == BillingOutcome.UNAVAILABLE) unavailable()
                else if (event.outcome != BillingOutcome.OK) publish(state.value.copy(issue = issue))
                // Les échecs d'ack attendent un prochain resume/refresh explicite, sans boucle immédiate.
                enqueueRefresh(issue, retryAcknowledgement = false)
            }
        }
    }

    private suspend fun processPurchases(purchases: List<PlayPurchase>, replace: Boolean, retryAcknowledgement: Boolean) {
        val matching = purchases.filter { PREMIUM_PRODUCT_ID in it.products && it.token.isNotBlank() }
        val purchased = matching.filter { it.state == PlayPurchaseState.PURCHASED }.distinctBy { it.token }
        val pending = matching.any { it.state == PlayPurchaseState.PENDING }
        if (!replace && purchased.isEmpty() && !pending) return
        val entitlement = when {
            purchased.isNotEmpty() -> PremiumEntitlement.ACTIVE
            !replace && state.value.isPremium -> PremiumEntitlement.ACTIVE
            pending -> PremiumEntitlement.PENDING
            else -> PremiumEntitlement.FREE
        }
        if (replace) {
            val liveTokens = purchased.map { it.token }.toSet()
            acknowledgedTokens.retainAll(liveTokens)
            failedAcknowledgements.retainAll(liveTokens)
        }
        val awaiting = purchased.filter { !it.acknowledged && it.token !in acknowledgedTokens }
        if (replace) diagnostics.record(when (entitlement) {
            PremiumEntitlement.FREE -> PremiumDiagnostic.OwnershipFree
            PremiumEntitlement.ACTIVE -> PremiumDiagnostic.OwnershipActive
            PremiumEntitlement.PENDING -> PremiumDiagnostic.OwnershipPending
        }, BillingOutcome.OK)
        // Publier la propriété dès la réponse achats, sans attendre prix/offre ou acknowledgement.
        publish(state.value.copy(entitlement = entitlement, offer = null, isLoading = false, ownershipVerified = true,
            acknowledgementPending = awaiting.isNotEmpty(), issue = null))
        for (purchase in awaiting) {
            if (!retryAcknowledgement && purchase.token in failedAcknowledgements) continue
            val result = try { gateway.acknowledge(purchase.token) } catch (error: Exception) {
                if (error is CancellationException) throw error
                BillingOutcome.ERROR
            }
            if (result == BillingOutcome.OK) {
                acknowledgedTokens += purchase.token
                failedAcknowledgements -= purchase.token
            } else failedAcknowledgements += purchase.token
        }
        val remaining = awaiting.any { it.token !in acknowledgedTokens }
        publish(state.value.copy(acknowledgementPending = remaining,
            issue = if (remaining) PremiumIssue.ACKNOWLEDGEMENT_FAILED else null))
    }

    private fun unavailable() {
        launching.set(false)
        publish(state.value.copy(isLoading = false, isPurchasing = false, ownershipVerified = false,
            offer = null, issue = PremiumIssue.STORE_UNAVAILABLE))
    }

    private fun publish(value: PremiumState) {
        _state.value = value
        _isPremium.value = value.isPremium
        diagnostics.record(when {
            value.isPremium -> PremiumDiagnostic.StateActive
            value.ownershipVerified && value.entitlement == PremiumEntitlement.FREE && !value.isPurchasing -> PremiumDiagnostic.StateFree
            else -> PremiumDiagnostic.StateUnsettled
        }, null)
    }
}
