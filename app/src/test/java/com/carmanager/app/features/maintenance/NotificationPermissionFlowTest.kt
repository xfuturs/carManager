package com.carmanager.app.features.maintenance

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.carmanager.app.R
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.data.repository.MaintenanceRepositoryImpl
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.util.NotificationHelper
import com.carmanager.app.core.util.UiEvent
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationPermissionFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<Context>()
    private val vehicles = mockk<VehicleRepository>()
    private val repository = mockk<MaintenanceRepository>()
    private val garage = GarageFixture()
    private val future = System.currentTimeMillis() + 86_400_000L
    private val denial = "Intervention enregistrée. Les notifications sont désactivées, le rappel peut ne pas s'afficher."
    private val combinedWarning = "Intervention enregistrée. Le rappel n'a pas pu être programmé et les notifications sont désactivées."
    private val saved = slot<MaintenanceRecord>()

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { vehicles.observeById(1) } returns flowOf(garage.vehicle())
        coEvery { repository.saveMaintenanceRecord(capture(saved)) } returns 8L
        every { context.getString(R.string.maintenance_saved_notifications_disabled) } returns denial
        every { context.getString(R.string.maintenance_saved_reminder_failed_notifications_disabled) } returns combinedWarning
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
        mockkObject(NotificationHelper)
        every { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) } just Runs
    }

    @AfterEach fun cleanup() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkObject(NotificationHelper)
    }

    private fun vm(repo: MaintenanceRepository = repository, due: Long? = future, initialType: String? = null) =
        AddMaintenanceViewModel(SaveMaintenanceUseCase(repo, vehicles, garage.session, context),
            vehicles, context, garage.session, SavedStateHandle(mapOf("vehicleId" to 1L, "initialType" to initialType)))
            .apply { onDateChange(0); onNextDueDateChange(due) }

    private fun TestScope.observe(vm: AddMaintenanceViewModel): Pair<MutableList<Unit>, MutableList<UiEvent>> {
        val requests = mutableListOf<Unit>()
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notificationPermissionRequests.collect { requests += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvent.collect { events += it } }
        return requests to events
    }

    private fun assertOneWriteAndReminder() {
        coVerify(exactly = 1) { repository.saveMaintenanceRecord(any()) }
        verify(exactly = 1) { NotificationHelper.scheduleReminder(any(), future, any(), any(), "firebase:A", 8L) }
    }

    @Test fun `permission policy gates API 26 to 32 and recognizes API 33 plus state`() {
        for (api in listOf(26, 32)) {
            assertEquals(NotificationPermissionStatus.NOT_REQUIRED, NotificationPermissionStatus.from(api, false))
            assertEquals(NotificationPermissionStatus.NOT_REQUIRED, NotificationPermissionStatus.from(api, true))
        }
        for (api in listOf(33, 36, 37)) {
            assertEquals(NotificationPermissionStatus.MISSING, NotificationPermissionStatus.from(api, false))
            assertEquals(NotificationPermissionStatus.GRANTED, NotificationPermissionStatus.from(api, true))
        }
    }

    @Test fun `absent or past reminder never requests permission`() = runTest(dispatcher) {
        for (due in listOf(null, System.currentTimeMillis() - 86_400_000L)) {
            val vm = vm(due = due)
            val (requests, events) = observe(vm)
            runCurrent()
            vm.save(NotificationPermissionStatus.MISSING); runCurrent()
            assertTrue(requests.isEmpty())
            assertEquals(listOf(UiEvent.Success), events)
            assertTrue(vm.hasSaved)
        }
        coVerify(exactly = 2) { repository.saveMaintenanceRecord(any()) }
        verify(exactly = 0) { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `API 26 and 32 keep future reminder scheduling without requesting permission`() = runTest(dispatcher) {
        for (api in listOf(26, 32)) {
            val vm = vm()
            val (requests, events) = observe(vm)
            runCurrent()
            vm.save(NotificationPermissionStatus.from(api, false)); runCurrent()
            assertTrue(requests.isEmpty())
            assertEquals(listOf(UiEvent.Success), events)
        }
        coVerify(exactly = 2) { repository.saveMaintenanceRecord(any()) }
        verify(exactly = 2) { NotificationHelper.scheduleReminder(any(), future, any(), any(), "firebase:A", 8L) }
    }

    @Test fun `already granted future reminder completes normally`() = runTest(dispatcher) {
        val vm = vm()
        val (requests, events) = observe(vm)
        runCurrent()
        vm.save(NotificationPermissionStatus.GRANTED); runCurrent()
        assertTrue(requests.isEmpty())
        assertEquals(listOf(UiEvent.Success), events)
        assertOneWriteAndReminder()
    }

    @Test fun `request waits for commit and snapshot survives double tap and editing`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.saveMaintenanceRecord(capture(saved)) } coAnswers { gate.await(); 8L }
        val vm = vm()
        val (requests, events) = observe(vm)
        runCurrent()
        vm.save(NotificationPermissionStatus.MISSING); vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        vm.onNextDueDateChange(null)
        assertTrue(vm.isSaving); assertFalse(vm.hasSaved)
        assertTrue(requests.isEmpty()); assertTrue(events.isEmpty())
        verify(exactly = 0) { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) }
        gate.complete(Unit); runCurrent()
        assertTrue(vm.hasSaved); assertFalse(vm.isSaving)
        assertEquals(future, saved.captured.nextDueDate)
        assertEquals(1, requests.size); assertTrue(events.isEmpty())
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertOneWriteAndReminder()
        vm.onNotificationPermissionResult(true); runCurrent()
        assertEquals(listOf(UiEvent.Success), events)
    }

    @Test fun `denial preserves real local writer maintenance mileage and reminder without retry`() = runTest(dispatcher) {
        val localRepository = MaintenanceRepositoryImpl(garage.maintenanceDao, garage.session, garage.access, garage.writer)
        val vm = vm(repo = localRepository, initialType = "TECHNICAL_INSPECTION")
        val (requests, events) = observe(vm)
        runCurrent()
        vm.onMileageChange("1500")
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertEquals(1, requests.size); assertTrue(events.isEmpty())
        vm.onNotificationPermissionResult(false)
        vm.onNotificationPermissionResult(false)
        vm.onNotificationPermissionResult(true)
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertEquals(listOf(UiEvent.ShowSnackbar(denial), UiEvent.Success), events)
        assertEquals(1, garage.maintenanceRows.size)
        assertEquals(future, garage.maintenanceRows.single().nextDueDate)
        assertEquals(1, garage.history.size)
        assertEquals(1500, garage.vehicle().currentMileage)
        assertEquals(1, garage.transactions)
        assertTrue(vm.hasSaved)
        verify(exactly = 1) { NotificationHelper.scheduleReminder(any(), future, "Rappel : Contrôle Technique", any(), "firebase:A", 1L) }
    }

    @Test fun `delayed UI collection and grant emit one success without resaving or scheduling again`() = runTest(dispatcher) {
        val vm = vm(initialType = "INSURANCE")
        runCurrent()
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        val (requests, events) = observe(vm)
        assertEquals(1, requests.size); assertTrue(events.isEmpty())
        vm.onNotificationPermissionResult(true)
        vm.onNotificationPermissionResult(true)
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertEquals(listOf(UiEvent.Success), events)
        assertOneWriteAndReminder()
        verify { NotificationHelper.scheduleReminder(any(), future, "Rappel : Assurance", any(), any(), any()) }
    }

    @Test fun `validation and database failures never request permission and allow explicit retry`() = runTest(dispatcher) {
        val vm = vm()
        val (requests, events) = observe(vm)
        runCurrent()
        vm.onCostChange("invalid"); vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        coVerify(exactly = 0) { repository.saveMaintenanceRecord(any()) }
        assertTrue(requests.isEmpty()); assertFalse(vm.hasSaved)
        vm.onCostChange("12,5")
        coEvery { repository.saveMaintenanceRecord(any()) } throws IllegalStateException("raw SQLite detail")
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertTrue(requests.isEmpty()); assertFalse(vm.hasSaved); assertFalse(vm.isSaving)
        assertEquals(2, events.size)
        assertFalse((events.last() as UiEvent.ShowSnackbar).message.contains("SQLite"))
        coEvery { repository.saveMaintenanceRecord(any()) } returns 8L
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertEquals(1, requests.size)
        vm.onNotificationPermissionResult(true); runCurrent()
        assertEquals(UiEvent.Success, events.last())
        coVerify(exactly = 2) { repository.saveMaintenanceRecord(any()) }
        verify(exactly = 1) { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `grant keeps post commit scheduling failure as warning without resave`() = runTest(dispatcher) {
        every { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) } throws SecurityException("alarm refused")
        val vm = vm()
        val (requests, events) = observe(vm)
        runCurrent()
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        assertEquals(1, requests.size)
        vm.onNotificationPermissionResult(true); runCurrent()
        assertTrue((events.first() as UiEvent.ShowSnackbar).message.contains("rappel n'a pas pu être programmé"))
        assertEquals(UiEvent.Success, events.last())
        assertOneWriteAndReminder()
    }

    @Test fun `denial combines scheduling warning and consumes callback once`() = runTest(dispatcher) {
        every { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) } throws SecurityException("alarm refused")
        val vm = vm()
        val (requests, events) = observe(vm)
        runCurrent()
        vm.onNotificationPermissionResult(false); runCurrent()
        assertTrue(events.isEmpty())
        vm.save(NotificationPermissionStatus.MISSING); runCurrent()
        vm.onNotificationPermissionResult(false)
        vm.onNotificationPermissionResult(false); runCurrent()
        assertEquals(listOf(UiEvent.ShowSnackbar(combinedWarning), UiEvent.Success), events)
        assertEquals(1, requests.size)
        assertOneWriteAndReminder()
    }
}
