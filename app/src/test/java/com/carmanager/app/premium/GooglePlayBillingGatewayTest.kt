package com.carmanager.app.premium

import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.carmanager.app.core.data.billing.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class GooglePlayBillingGatewayTest {
    private val client=mockk<BillingClient>()
    private val connection=slot<BillingClientStateListener>()
    private val ownership=slot<PurchasesResponseListener>()
    private var ready=false
    private lateinit var gateway: GooglePlayBillingGateway
    private val events=mutableListOf<PremiumDiagnostic>()
    private fun result(code: Int)=mockk<BillingResult> { every { responseCode } returns code }
    @BeforeEach fun setup() {
        mockkStatic(BillingClient::class,Log::class)
        every { Log.w(any(),any<String>()) } returns 0
        val context=mockk<Context>(); every { context.applicationContext } returns context
        val builder=mockk<BillingClient.Builder>()
        every { BillingClient.newBuilder(context) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.enableAutoServiceReconnection() } returns builder
        every { builder.build() } returns client
        every { client.isReady } answers { ready }
        every { client.connectionState } returns BillingClient.ConnectionState.DISCONNECTED
        every { client.startConnection(capture(connection)) } just Runs
        every { client.queryPurchasesAsync(any<QueryPurchasesParams>(),capture(ownership)) } just Runs
        val diagnostics=mockk<AndroidPremiumDiagnostics>()
        every { diagnostics.record(any(),any()) } answers { events+=firstArg<PremiumDiagnostic>(); Unit }
        gateway=GooglePlayBillingGateway(context,diagnostics)
    }
    @AfterEach fun cleanup() { unmockkStatic(BillingClient::class,Log::class) }
    private suspend fun TestScope.connect() {
        val pending=async { gateway.connect() }; runCurrent(); ready=true
        connection.captured.onBillingSetupFinished(result(BillingClient.BillingResponseCode.OK)); runCurrent()
        assertEquals(BillingOutcome.OK,pending.await())
    }
    @Test fun `connection ready and successful empty ownership are definitive results`()=runTest {
        connect(); val pending=async { gateway.queryPurchases() }; runCurrent()
        ownership.captured.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList()); runCurrent()
        assertEquals(BillingOutcome.OK,pending.await().outcome); assertEquals(emptyList<PlayPurchase>(),pending.await().data)
        assertTrue(PremiumDiagnostic.Ready in events)
    }
    @Test fun `query timeout and late callback cannot create a successful ownership result`()=runTest {
        connect(); val pending=async { gateway.queryPurchases() }; runCurrent(); val old=ownership.captured
        advanceTimeBy(15_000); runCurrent(); assertEquals(BillingOutcome.UNAVAILABLE,pending.await().outcome)
        old.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList()); runCurrent()
        assertEquals(BillingOutcome.UNAVAILABLE,pending.await().outcome); assertTrue(PremiumDiagnostic.ReplyTimeout in events)
    }
    @Test fun `duplicate ownership callback completes only once`()=runTest {
        connect(); val pending=async { gateway.queryPurchases() }; runCurrent(); val callback=ownership.captured
        callback.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList())
        callback.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.ERROR),emptyList()); runCurrent()
        assertEquals(BillingOutcome.OK,pending.await().outcome)
    }
    @Test fun `cancelled query late reply cannot affect a later query`()=runTest {
        connect(); val canceled=async { gateway.queryPurchases() }; runCurrent(); val old=ownership.captured
        canceled.cancel(); runCurrent(); val next=async { gateway.queryPurchases() }; runCurrent()
        old.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList()); runCurrent(); assertFalse(next.isCompleted)
        ownership.captured.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList()); runCurrent()
        assertEquals(BillingOutcome.OK,next.await().outcome); assertTrue(PremiumDiagnostic.ReplyCanceled in events)
    }
    @Test fun `disconnect then reconnect cannot accept ownership from the older connection`()=runTest {
        connect(); val old=async { gateway.queryPurchases() }; runCurrent(); val callback=ownership.captured
        ready=false; connection.captured.onBillingServiceDisconnected(); ready=true
        callback.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.OK),emptyList()); runCurrent()
        assertEquals(BillingOutcome.UNAVAILABLE,old.await().outcome); assertTrue(PremiumDiagnostic.StaleOwnershipReply in events)
    }
    @Test fun `setup failure and timeout never synthesize Free ownership`()=runTest {
        val failed=async { gateway.connect() }; runCurrent()
        connection.captured.onBillingSetupFinished(result(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)); runCurrent()
        assertEquals(BillingOutcome.UNAVAILABLE,failed.await())
        val timeout=async { gateway.connect() }; runCurrent(); advanceTimeBy(15_000); runCurrent()
        assertEquals(BillingOutcome.UNAVAILABLE,timeout.await()); assertFalse(ready)
    }
}
