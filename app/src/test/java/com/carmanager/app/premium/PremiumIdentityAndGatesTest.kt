package com.carmanager.app.premium

import com.carmanager.app.core.data.billing.BillingOutcome
import com.carmanager.app.core.data.billing.BillingReply
import com.carmanager.app.core.data.billing.PlayPurchaseState
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.model.DashboardStats
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.AccountDeletion
import com.carmanager.app.core.domain.session.DeletionState
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.features.dashboard.DashboardViewModel
import com.carmanager.app.features.dashboard.GetDashboardStatsUseCase
import com.carmanager.app.features.settings.SettingsViewModel
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PremiumIdentityAndGatesTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun setMain() { Dispatchers.setMain(dispatcher) }
    @AfterEach fun resetMain() { Dispatchers.resetMain() }

    private val users = MutableStateFlow<User?>(null)
    private val auth = mockk<AuthRepository>().also { every { it.currentUser } returns users }
    private val registry = TestDeletionRegistry()
    private val session = WorkspaceSession(registry)
    private val settings = mockk<SettingsRepository>().also {
        every { it.themePreference } returns flowOf(AppTheme.SYSTEM)
        every { it.currency } returns flowOf("€")
        every { it.distanceUnit } returns flowOf("km")
    }
    private fun settingsViewModel(premium: PremiumRepository) = SettingsViewModel(
        settings, auth, premium,
        mockk<AccountDeletion>().also { every { it.state } returns MutableStateFlow(DeletionState()) },
        session, registry
    )

    @Test fun `guest and Firebase workspaces share the same restored Play ownership`() = runTest(dispatcher) {
        val gateway = FakePlayBillingGateway().apply {
            purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase()))
        }
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        val viewModel = settingsViewModel(premium)
        premium.initialize(); runCurrent()
        assertNull(viewModel.currentUser.value)
        assertTrue(viewModel.premiumState.value.isPremium)
        for (uid in listOf("account-A", "account-B", null)) {
            users.value = uid?.let { User(it, "$it@example.test") }
            session.setAuthenticatedUid(uid)
            runCurrent()
            assertSame(premium.state, viewModel.premiumState)
            assertTrue(viewModel.isPremium.value)
            assertEquals(1, gateway.queryCalls)
        }
    }

    @Test fun `historical whitelist emails never grant Premium without Play ownership`() = runTest(dispatcher) {
        val premium = PremiumRepositoryImpl(FakePlayBillingGateway(), backgroundScope)
        val viewModel = settingsViewModel(premium)
        premium.initialize(); runCurrent()
        for (email in listOf("admin@xfuturs.com", "tester@xfuturs.com")) {
            users.value = User("account-A", email)
            session.setAuthenticatedUid("account-A")
            runCurrent()
            assertFalse(viewModel.isPremium.value)
            assertFalse(viewModel.premiumState.value.isPremium)
        }
    }

    @Test fun `existing Premium gate inputs stay locked for pending and unlock for purchased`() = runTest(dispatcher) {
        val gateway = FakePlayBillingGateway()
        val premium = PremiumRepositoryImpl(gateway, backgroundScope)
        val settingsViewModel = settingsViewModel(premium)
        val stats = mockk<GetDashboardStatsUseCase>().also { every { it() } returns flowOf(DashboardStats()) }
        val dashboard = DashboardViewModel(stats, mockk(), settings, premium, mockk())
        premium.initialize(); runCurrent()
        assertSame(premium.isPremium, dashboard.isPremium)
        assertSame(premium.isPremium, settingsViewModel.isPremium)
        assertFalse(dashboard.isPremium.value)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase(PlayPurchaseState.PENDING, false)))
        premium.checkPremiumStatus(); runCurrent()
        assertFalse(premium.isPremium.value)
        assertFalse(dashboard.isPremium.value)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase()))
        premium.checkPremiumStatus(); runCurrent()
        assertTrue(premium.isPremium.value)
        assertTrue(dashboard.isPremium.value)
        assertTrue(settingsViewModel.isPremium.value)
    }
}
