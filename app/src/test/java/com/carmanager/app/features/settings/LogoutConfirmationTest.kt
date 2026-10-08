package com.carmanager.app.features.settings

import androidx.lifecycle.ViewModelStore
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.model.PremiumState
import com.carmanager.app.core.domain.session.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutConfirmationTest {
    private val dispatcher = StandardTestDispatcher()
    private val garage = GarageFixture()
    private val users = MutableStateFlow<User?>(User("synthetic-A", "driver@example.test"))
    private val auth = mockk<AuthRepository>()
    private val deletionState = MutableStateFlow(DeletionState())
    private val store = ViewModelStore()
    private lateinit var vm: SettingsViewModel
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.currentUser } returns users
        coEvery { auth.signOut() } coAnswers { users.value = null }
        val settings = mockk<SettingsRepository> {
            every { themePreference } returns flowOf(AppTheme.LIGHT)
            every { currency } returns flowOf("€")
            every { distanceUnit } returns flowOf("km")
        }
        val premium = mockk<PremiumRepository> {
            every { isPremium } returns MutableStateFlow(false)
            every { state } returns MutableStateFlow(PremiumState())
        }
        val deletion = mockk<AccountDeletion> { every { state } returns deletionState }
        vm = SettingsViewModel(settings, auth, premium, deletion, garage.session, garage.registry)
        store.put("settings", vm)
    }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }
    @Test fun `opening confirmation never logs out`() = runTest(dispatcher) {
        vm.requestSignOut(); runCurrent()
        assertTrue(vm.logoutConfirmation.value); assertNotNull(users.value)
        coVerify(exactly = 0) { auth.signOut() }
    }
    @Test fun `cancel retains authentication and garage`() = runTest(dispatcher) {
        val before = garage.vehicles.toMap()
        vm.requestSignOut(); vm.cancelSignOut(); runCurrent()
        assertFalse(vm.logoutConfirmation.value); assertNotNull(users.value)
        assertEquals(before, garage.vehicles); coVerify(exactly = 0) { auth.signOut() }
    }
    @Test fun `dismiss uses cancellation and later stale confirm cannot log out`() = runTest(dispatcher) {
        vm.requestSignOut(); vm.cancelSignOut(); vm.confirmSignOut(); runCurrent()
        assertNotNull(users.value); coVerify(exactly = 0) { auth.signOut() }
    }
    @Test fun `confirmed logout invokes existing repository once`() = runTest(dispatcher) {
        vm.requestSignOut(); vm.confirmSignOut()
        assertFalse(vm.logoutConfirmation.value); assertTrue(vm.logoutRunning.value)
        runCurrent(); assertNull(users.value); assertFalse(vm.logoutRunning.value)
        coVerify(exactly = 1) { auth.signOut() }
    }
    @Test fun `double confirm and reopen while pending cannot resubmit`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { auth.signOut() } coAnswers { gate.await(); users.value = null }
        vm.requestSignOut(); vm.confirmSignOut(); vm.confirmSignOut(); runCurrent()
        vm.requestSignOut(); vm.confirmSignOut(); runCurrent()
        assertTrue(vm.logoutRunning.value); assertFalse(vm.logoutConfirmation.value)
        coVerify(exactly = 1) { auth.signOut() }
        gate.complete(Unit); runCurrent(); vm.confirmSignOut(); runCurrent()
        coVerify(exactly = 1) { auth.signOut() }
    }
    @Test fun `success preserves canonical garage and its journals`() = runTest(dispatcher) {
        garage.writer.saveFuel(garage.fuel()); garage.writer.saveMaintenance(garage.maintenance())
        val before = listOf(garage.vehicles.toMap(), garage.fuelRows.toList(), garage.maintenanceRows.toList(), garage.history.toList())
        vm.requestSignOut(); vm.confirmSignOut(); runCurrent()
        assertNull(users.value); garage.session.requireWritable(LocalGarageOwner.KEY)
        assertEquals("local:device", garage.session.owner.value)
        assertEquals(before, listOf(garage.vehicles.toMap(), garage.fuelRows.toList(), garage.maintenanceRows.toList(), garage.history.toList()))
    }
    @Test fun `failure preserves auth garage and error and permits ordinary retry`() = runTest(dispatcher) {
        val before = garage.vehicles.toMap()
        coEvery { auth.signOut() } throws IllegalStateException("Déconnexion impossible.")
        vm.requestSignOut(); vm.confirmSignOut(); runCurrent()
        assertNotNull(users.value); assertEquals(before, garage.vehicles)
        assertEquals("Déconnexion impossible.", vm.accountError.value); assertFalse(vm.logoutRunning.value)
        coEvery { auth.signOut() } coAnswers { users.value = null }
        vm.requestSignOut(); vm.confirmSignOut(); runCurrent()
        assertNull(users.value); assertNull(vm.accountError.value)
        coVerify(exactly = 2) { auth.signOut() }
    }
    @Test fun `wording explicitly preserves local garage`() {
        assertEquals("Se déconnecter ?", LogoutConfirmationCopy.TITLE)
        assertEquals("Votre garage restera enregistré localement sur cet appareil.", LogoutConfirmationCopy.BODY)
    }
    @Test fun `account deletion already running blocks confirmation`() = runTest(dispatcher) {
        deletionState.value = DeletionState(running = true)
        vm.requestSignOut(); vm.confirmSignOut(); runCurrent()
        assertFalse(vm.logoutConfirmation.value); coVerify(exactly = 0) { auth.signOut() }
    }
    @Test fun `guest or auth lost before confirm cannot submit`() = runTest(dispatcher) {
        vm.requestSignOut(); users.value = null; vm.confirmSignOut(); runCurrent()
        vm.requestSignOut(); assertFalse(vm.logoutConfirmation.value)
        coVerify(exactly = 0) { auth.signOut() }
    }
}
