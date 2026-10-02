package com.carmanager.app.premium

import android.app.Activity
import com.carmanager.app.core.data.billing.*
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.model.*
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PremiumRepositoryTest {
    private val activity = mockk<Activity>()
    private fun TestScope.repository(gateway: FakePlayBillingGateway) =
        PremiumRepositoryImpl(gateway, backgroundScope).also { it.initialize(); runCurrent() }

    @Test fun `localized Play price is exposed without granting Premium`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        assertEquals("CHF 12.00", repository.state.value.offer?.formattedPrice)
        assertFalse(repository.isPremium.value)
        assertTrue(repository.state.value.canPurchase)
        assertEquals(1, gateway.connectCalls)
        assertEquals(1, gateway.queryCalls)
    }

    @Test fun `unfetched or unavailable product exposes no fake offer`() = runTest {
        for (outcome in listOf(BillingOutcome.OK, BillingOutcome.UNAVAILABLE)) {
            val gateway = FakePlayBillingGateway().apply { offerReply = BillingReply(outcome) }
            val repository = repository(gateway)
            assertNull(repository.state.value.offer)
            assertEquals(PremiumIssue.OFFER_UNAVAILABLE, repository.state.value.issue)
            assertFalse(repository.isPremium.value)
            repository.launchPurchase(activity)
            assertEquals(0, gateway.launchCalls)
        }
    }

    @Test fun `only premium product with nonblank Play price is offered`() = runTest {
        for (offer in listOf(PremiumOffer("other", "CHF 12.00"), PremiumOffer(PREMIUM_PRODUCT_ID, " "))) {
            val gateway = FakePlayBillingGateway().apply { offerReply = BillingReply(BillingOutcome.OK, offer) }
            val repository = repository(gateway)
            assertNull(repository.state.value.offer)
            assertFalse(repository.state.value.canPurchase)
        }
    }

    @Test fun `acknowledged purchased ownership restores Premium without acknowledgment`() = runTest {
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        val repository = repository(gateway)
        assertTrue(repository.isPremium.value)
        assertEquals(PremiumEntitlement.ACTIVE, repository.state.value.entitlement)
        assertFalse(repository.state.value.acknowledgementPending)
        assertTrue(gateway.ackTokens.isEmpty())
        assertEquals(0, gateway.offerCalls)
    }

    @Test fun `unacknowledged purchased grants immediately then acknowledges once despite stale ack bit`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(acknowledged = false)))
            ackHandler = { gate.await(); BillingOutcome.OK }
        }
        val repository = repository(gateway)
        assertTrue(repository.isPremium.value)
        assertTrue(repository.state.value.acknowledgementPending)
        assertEquals(listOf("test-token"), gateway.ackTokens)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(1, gateway.ackTokens.size)
        gate.complete(Unit); runCurrent()
        assertFalse(repository.state.value.acknowledgementPending)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(1, gateway.ackTokens.size)
    }

    @Test fun `pending ownership stays free and cannot launch or acknowledge`() = runTest {
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(PlayPurchaseState.PENDING, false)))
        }
        val repository = repository(gateway)
        assertEquals(PremiumEntitlement.PENDING, repository.state.value.entitlement)
        assertFalse(repository.isPremium.value)
        repository.launchPurchase(activity)
        assertEquals(0, gateway.launchCalls)
        assertTrue(gateway.ackTokens.isEmpty())
    }

    @Test fun `unrelated unspecified and tokenless purchases never grant or acknowledge`() = runTest {
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(
                premiumPurchase(product = "other", acknowledged = false),
                premiumPurchase(PlayPurchaseState.UNSPECIFIED, false), premiumPurchase(token = "")))
        }
        val repository = repository(gateway)
        assertFalse(repository.isPremium.value)
        assertTrue(gateway.ackTokens.isEmpty())
    }

    @Test fun `ack failure keeps warning and later reconnect retries without revoking purchase`() = runTest {
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(acknowledged = false)))
            ackResult = BillingOutcome.UNAVAILABLE
        }
        val repository = repository(gateway)
        assertTrue(repository.isPremium.value)
        assertTrue(repository.state.value.acknowledgementPending)
        assertEquals(PremiumIssue.ACKNOWLEDGEMENT_FAILED, repository.state.value.issue)
        assertEquals(1, gateway.ackTokens.size)
        gateway.disconnect(); runCurrent()
        gateway.ackResult = BillingOutcome.OK
        repository.checkPremiumStatus(); runCurrent()
        assertTrue(repository.isPremium.value)
        assertFalse(repository.state.value.acknowledgementPending)
        assertNull(repository.state.value.issue)
        assertEquals(2, gateway.ackTokens.size)
        assertEquals(2, gateway.connectCalls)
    }

    @Test fun `failed acknowledgment waits for later explicit refresh instead of callback retry loop`() = runTest {
        val purchase = premiumPurchase(acknowledged = false)
        val gateway = FakePlayBillingGateway().apply { ackResult = BillingOutcome.ERROR }
        val repository = repository(gateway)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(purchase))
        gateway.update(BillingOutcome.OK, listOf(purchase)); runCurrent()
        gateway.update(BillingOutcome.OK, listOf(purchase)); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(PremiumIssue.ACKNOWLEDGEMENT_FAILED, repository.state.value.issue)
        assertEquals(1, gateway.ackTokens.size)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(2, gateway.ackTokens.size)
    }

    @Test fun `duplicate purchased payloads and callbacks do not duplicate acknowledgment`() = runTest {
        val purchase = premiumPurchase(acknowledged = false)
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(purchase, purchase)) }
        val repository = repository(gateway)
        gateway.update(BillingOutcome.OK, listOf(purchase)); gateway.update(BillingOutcome.OK, listOf(purchase)); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(1, gateway.ackTokens.size)
        assertEquals(1, gateway.maxActiveQueries)
    }

    @Test fun `successful purchase update grants acknowledges and refreshes ownership`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        val purchase = premiumPurchase(acknowledged = false)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(purchase))
        gateway.update(BillingOutcome.OK, listOf(purchase)); runCurrent()
        assertTrue(repository.isPremium.value)
        assertFalse(repository.state.value.isPurchasing)
        assertEquals(1, gateway.ackTokens.size)
        assertEquals(2, gateway.queryCalls)
    }

    @Test fun `pending update waits for later resume purchased query`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        val pending = premiumPurchase(PlayPurchaseState.PENDING, false)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(pending))
        gateway.update(BillingOutcome.OK, listOf(pending)); runCurrent()
        assertEquals(PremiumEntitlement.PENDING, repository.state.value.entitlement)
        assertFalse(repository.isPremium.value)
        assertTrue(gateway.ackTokens.isEmpty())
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(acknowledged = false)))
        repository.checkPremiumStatus(); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(1, gateway.ackTokens.size)
    }

    @Test fun `user cancellation clears loading without error and permits later retry`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        gateway.update(BillingOutcome.CANCELED); runCurrent()
        assertFalse(repository.state.value.isPurchasing)
        assertFalse(repository.isPremium.value)
        assertNull(repository.state.value.issue)
        repository.launchPurchase(activity)
        assertEquals(2, gateway.launchCalls)
    }

    @Test fun `already owned update queries and restores without another launch`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase()))
        gateway.update(BillingOutcome.ALREADY_OWNED); runCurrent()
        assertTrue(repository.isPremium.value)
        repository.launchPurchase(activity)
        assertEquals(1, gateway.launchCalls)
        assertEquals(2, gateway.queryCalls)
    }

    @Test fun `purchase callback error clears busy and retains friendly retry state`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        gateway.update(BillingOutcome.ERROR); runCurrent()
        assertFalse(repository.state.value.isPurchasing)
        assertEquals(PremiumIssue.PURCHASE_FAILED, repository.state.value.issue)
        assertTrue(repository.state.value.canPurchase)
        repository.launchPurchase(activity)
        assertEquals(2, gateway.launchCalls)
    }

    @Test fun `rapid double tap launches one purchase flow`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        repository.launchPurchase(activity); repository.launchPurchase(activity); repository.launchPurchase(activity)
        assertEquals(1, gateway.launchCalls)
        assertTrue(repository.state.value.isPurchasing)
        assertFalse(repository.state.value.canPurchase)
    }

    @Test fun `not ready billing cannot launch even with displayed offer`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        gateway.isReady = false
        repository.launchPurchase(activity)
        assertEquals(0, gateway.launchCalls)
        assertFalse(repository.state.value.isPurchasing)
        assertEquals(PremiumIssue.STORE_UNAVAILABLE, repository.state.value.issue)
    }

    @Test fun `immediate launch error clears busy and allows explicit retry`() = runTest {
        val gateway = FakePlayBillingGateway().apply { launchResult = BillingOutcome.ERROR }
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        assertFalse(repository.state.value.isPurchasing)
        assertEquals(PremiumIssue.PURCHASE_FAILED, repository.state.value.issue)
        gateway.launchResult = BillingOutcome.OK
        repository.launchPurchase(activity)
        assertEquals(2, gateway.launchCalls)
    }

    @Test fun `stale offer launch rejection clears busy and refresh can recover`() = runTest {
        val gateway = FakePlayBillingGateway().apply { launchResult = BillingOutcome.UNAVAILABLE }
        val repository = repository(gateway)
        repository.launchPurchase(activity)
        assertFalse(repository.state.value.isPurchasing)
        assertNull(repository.state.value.offer)
        assertFalse(repository.isPremium.value)
        gateway.launchResult = BillingOutcome.OK
        repository.checkPremiumStatus(); runCurrent()
        repository.launchPurchase(activity)
        assertEquals(2, gateway.launchCalls)
    }

    @Test fun `immediate already owned response recovers current ownership`() = runTest {
        val gateway = FakePlayBillingGateway().apply { launchResult = BillingOutcome.ALREADY_OWNED }
        val repository = repository(gateway)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase()))
        repository.launchPurchase(activity); runCurrent()
        assertTrue(repository.isPremium.value)
        assertFalse(repository.state.value.isPurchasing)
        assertEquals(2, gateway.queryCalls)
    }

    @Test fun `cold offline connection or ownership failure cannot invent Premium`() = runTest {
        val gateways = listOf(FakePlayBillingGateway().apply { connectResult = BillingOutcome.UNAVAILABLE },
            FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.ERROR) })
        for (gateway in gateways) {
            val repository = repository(gateway)
            assertFalse(repository.isPremium.value)
            assertFalse(repository.state.value.isLoading)
            assertFalse(repository.state.value.canPurchase)
            assertEquals(PremiumIssue.STORE_UNAVAILABLE, repository.state.value.issue)
        }
    }

    @Test fun `offline retains confirmed session only and successful empty query revokes ownership`() = runTest {
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        val repository = repository(gateway)
        gateway.purchaseReply = BillingReply(BillingOutcome.UNAVAILABLE)
        repository.checkPremiumStatus(); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(PremiumIssue.STORE_UNAVAILABLE, repository.state.value.issue)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, emptyList())
        repository.checkPremiumStatus(); runCurrent()
        assertFalse(repository.isPremium.value)
        assertEquals(PremiumEntitlement.FREE, repository.state.value.entitlement)
    }

    @Test fun `initialization and simultaneous refresh calls coalesce one active query`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakePlayBillingGateway().apply { queryHandler = { gate.await(); purchaseReply } }
        val repository = repository(gateway)
        repeat(10) { repository.initialize(); repository.checkPremiumStatus() }; runCurrent()
        assertEquals(1, gateway.connectCalls)
        assertEquals(1, gateway.queryCalls)
        gate.complete(Unit); runCurrent()
        assertEquals(1, gateway.queryCalls)
        assertEquals(1, gateway.maxActiveQueries)
    }

    @Test fun `in flight old ownership snapshot is processed before queued purchase update`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val purchase = premiumPurchase(acknowledged = false)
        val gateway = FakePlayBillingGateway()
        gateway.queryHandler = {
            if (gateway.queryCalls == 1) { gate.await(); BillingReply(BillingOutcome.OK, emptyList()) }
            else gateway.purchaseReply
        }
        val repository = repository(gateway)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(purchase))
        gateway.update(BillingOutcome.OK, listOf(purchase)); runCurrent()
        assertFalse(repository.isPremium.value)
        gate.complete(Unit); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(2, gateway.queryCalls)
        assertEquals(1, gateway.ackTokens.size)
        assertEquals(1, gateway.maxActiveQueries)
    }

    @Test fun `unrelated callback does not erase confirmed premium ownership`() = runTest {
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        val repository = repository(gateway)
        gateway.update(BillingOutcome.OK, listOf(premiumPurchase(product = "other", acknowledged = false))); runCurrent()
        assertTrue(repository.isPremium.value)
        assertTrue(gateway.ackTokens.isEmpty())
    }

    @Test fun `disconnect never spins reconnect and next explicit refresh restores`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        gateway.disconnect(); runCurrent(); advanceTimeBy(60_000); runCurrent()
        assertEquals(1, gateway.connectCalls)
        assertNull(repository.state.value.offer)
        repository.checkPremiumStatus(); runCurrent()
        assertEquals(2, gateway.connectCalls)
        assertTrue(repository.state.value.canPurchase)
    }

    @Test fun `empty update payload still refreshes purchases from Play`() = runTest {
        val gateway = FakePlayBillingGateway()
        val repository = repository(gateway)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase()))
        gateway.update(BillingOutcome.OK); runCurrent()
        assertTrue(repository.isPremium.value)
        assertEquals(2, gateway.queryCalls)
    }

    @Test fun `new repository session restores through Play query without local authority`() = runTest {
        val firstGateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        assertTrue(repository(firstGateway).isPremium.value)
        val restored = FakePlayBillingGateway().apply { purchaseReply = firstGateway.purchaseReply }
        val second = PremiumRepositoryImpl(restored, backgroundScope)
        assertFalse(second.isPremium.value)
        second.initialize(); runCurrent()
        assertTrue(second.isPremium.value)
        assertEquals(1, restored.queryCalls)
    }

    @Test fun `purchase representation redacts token from accidental diagnostic output`() {
        val purchase = premiumPurchase(token = "sensitive-test-token")
        assertFalse(purchase.toString().contains("sensitive-test-token"))
        assertTrue(purchase.toString().contains("REDACTED"))
    }
}
