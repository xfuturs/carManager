package com.carmanager.app.a14

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.util.LocalReminderCoordinator
import com.carmanager.app.features.maintenance.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSaveIntegrationTest {
    @Test fun `production save commits before reconciliation and returns disabled permission eligibility`() = runTest {
        val garage = GarageFixture(); val repository = mockk<MaintenanceRepository>(); val coordinator = mockk<LocalReminderCoordinator>()
        val record = garage.maintenance().copy(nextDueDate = System.currentTimeMillis()+86_400_000)
        var committed = false
        coEvery { repository.saveMaintenanceRecord(record) } coAnswers { committed = true; 9L }
        coEvery { coordinator.enabledFor(record) } coAnswers { assertTrue(committed); false }
        coEvery { coordinator.reconcileNow() } coAnswers { assertTrue(committed) }
        val result = SaveMaintenanceUseCase(repository,mockk(),garage.session,coordinator,mockk<Context>())(record)
        assertEquals(9,result.id); assertNull(result.warning); assertFalse(result.reminderEnabled)
        coVerify(exactly=1) { coordinator.reconcileNow() }; coVerify(exactly=1) { repository.saveMaintenanceRecord(record) }
    }
    @Test fun `alarm failure after production commit remains a success with warning`() = runTest {
        mockkStatic(Log::class); every { Log.e(any(),any(),any<Throwable>()) } returns 0
        try {
            val garage = GarageFixture(); val repository = mockk<MaintenanceRepository>(); val coordinator = mockk<LocalReminderCoordinator>()
            coEvery { repository.saveMaintenanceRecord(any()) } returns 9L
            coEvery { coordinator.enabledFor(any()) } returns true; coEvery { coordinator.reconcileNow() } throws IllegalStateException("alarm")
            val result = SaveMaintenanceUseCase(repository,mockk(),garage.session,coordinator,mockk<Context>())(garage.maintenance())
            assertEquals(9,result.id); assertNotNull(result.warning); assertTrue(result.reminderEnabled)
            coVerify(exactly=1) { repository.saveMaintenanceRecord(any()) }
        } finally { unmockkStatic(Log::class) }
    }
    @Test fun `disabled reminders do not prompt after missing Android permission or duplicate save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val garage = GarageFixture(); val repository = mockk<MaintenanceRepository>(); val coordinator = mockk<LocalReminderCoordinator>()
            val vehicles = mockk<VehicleRepository>(); val context = mockk<Context>()
            every { vehicles.observeById(1) } returns flowOf(garage.vehicle())
            coEvery { repository.saveMaintenanceRecord(any()) } returns 9L; coEvery { coordinator.enabledFor(any()) } returns false
            coEvery { coordinator.reconcileNow() } just Runs
            val vm = AddMaintenanceViewModel(SaveMaintenanceUseCase(repository,vehicles,garage.session,coordinator,context),vehicles,context,garage.session,SavedStateHandle(mapOf("vehicleId" to 1L)))
            val requests = mutableListOf<Unit>(); backgroundScope.launch { vm.notificationPermissionRequests.collect { requests += it } }
            backgroundScope.launch { vm.uiEvent.collect {} }; runCurrent()
            vm.onDateChange(0); vm.onNextDueDateChange(System.currentTimeMillis()+86_400_000)
            vm.save(NotificationPermissionStatus.MISSING); runCurrent(); assertTrue(vm.hasSaved); assertTrue(requests.isEmpty())
            vm.save(NotificationPermissionStatus.MISSING); runCurrent(); coVerify(exactly=1) { repository.saveMaintenanceRecord(any()) }
        } finally { Dispatchers.resetMain() }
    }
}
