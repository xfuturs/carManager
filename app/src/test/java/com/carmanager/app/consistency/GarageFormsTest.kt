package com.carmanager.app.consistency

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.carmanager.app.core.data.local.dao.VehicleReferenceDao
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.NotificationHelper
import com.carmanager.app.core.util.UiEvent
import com.carmanager.app.features.fuel.*
import com.carmanager.app.features.maintenance.*
import com.carmanager.app.features.vehicles.*
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GarageFormsTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<Context>()
    private val vehicles = mockk<VehicleRepository>()
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid("A") }
    private val vehicle = GarageFixture().vehicle().copy(tankCapacity = 100.0, batteryCapacity = 50.0)

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { vehicles.observeById(1) } returns flowOf(vehicle)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }
    @AfterEach fun cleanup() { Dispatchers.resetMain(); unmockkStatic(Log::class); unmockkObject(NotificationHelper) }
    private fun fuelVm(repository: FuelRepository) = AddFuelViewModel(SaveFuelRecordUseCase(repository), vehicles, context, session,
        SavedStateHandle(mapOf("vehicleId" to 1L)))

    @Test fun `fuel comma inputs snapshot one transaction across double tap and consumed success`() = runTest(dispatcher) {
        val repository = mockk<FuelRepository>()
        val gate = CompletableDeferred<Unit>()
        val saved = slot<FuelRecord>()
        coEvery { repository.saveFuelRecord(capture(saved)) } coAnswers { gate.await(); 9L }
        val vm = fuelVm(repository)
        runCurrent()
        vm.onLitersChange("12,5"); vm.onTotalPriceChange("25,5"); vm.onMileageChange("1500")
        vm.save(); vm.save(); runCurrent()
        assertTrue(vm.isSaving)
        vm.onLitersChange("99")
        gate.complete(Unit); runCurrent()
        assertEquals(12.5, saved.captured.liters); assertEquals(25.5, saved.captured.totalPrice)
        assertEquals("firebase:A", saved.captured.ownerKey)
        assertEquals(UiEvent.Success, vm.uiEvent.first())
        assertFalse(vm.isSaving); assertTrue(vm.hasSaved)
        vm.save(); runCurrent()
        coVerify(exactly = 1) { repository.saveFuelRecord(any()) }
    }

    @Test fun `fuel rejects zero negative malformed and overflowing mileage then accepts historical reading`() = runTest(dispatcher) {
        val repository = mockk<FuelRepository>()
        coEvery { repository.saveFuelRecord(any()) } returns 1L
        val vm = fuelVm(repository); runCurrent()
        vm.onTotalPriceChange("20")
        for (quantity in listOf("0", "-1", "NaN", "12,5.6")) {
            vm.onLitersChange(quantity); vm.save(); runCurrent()
            assertInstanceOf(UiEvent.ShowSnackbar::class.java, vm.uiEvent.first())
        }
        vm.onLitersChange("10"); vm.onMileageChange("2147483648"); vm.save(); runCurrent()
        assertInstanceOf(UiEvent.ShowSnackbar::class.java, vm.uiEvent.first())
        coVerify(exactly = 0) { repository.saveFuelRecord(any()) }
        vm.onMileageChange("900"); vm.save(); runCurrent()
        assertEquals(UiEvent.Success, vm.uiEvent.first())
        coVerify(exactly = 1) { repository.saveFuelRecord(match { it.mileage == 900 }) }
    }

    @Test fun `persistence failure resets save state and permits explicit retry without raw SQL message`() = runTest(dispatcher) {
        val repository = mockk<FuelRepository>()
        coEvery { repository.saveFuelRecord(any()) } throws IllegalStateException("raw SQLite detail")
        val vm = fuelVm(repository); runCurrent()
        vm.onLitersChange("10"); vm.onTotalPriceChange("20"); vm.save(); runCurrent()
        val event = vm.uiEvent.first() as UiEvent.ShowSnackbar
        assertFalse(event.message.contains("SQLite"))
        assertFalse(vm.isSaving); assertFalse(vm.hasSaved)
        coEvery { repository.saveFuelRecord(any()) } returns 1L
        vm.save(); runCurrent()
        assertEquals(UiEvent.Success, vm.uiEvent.first())
        coVerify(exactly = 2) { repository.saveFuelRecord(any()) }
    }

    @Test fun `custom brand model stay editable and invalid typed optional or required numerics block save`() = runTest(dispatcher) {
        val references = mockk<VehicleReferenceDao>()
        every { references.getAllBrands() } returns flowOf(listOf("Renault"))
        coEvery { references.getCount() } returns 1
        coEvery { vehicles.saveVehicle(any()) } returns 1L
        val vm = AddEditVehicleViewModel(SaveVehicleUseCase(vehicles), mockk(), vehicles, references, context, session, SavedStateHandle())
        runCurrent()
        vm.onBrandChange("", true); vm.onBrandChange(" Artisanal ", true); vm.onModelChange(" Prototype ", true)
        vm.onBrandChange(" Artisan ", true)
        assertTrue(vm.isCustomBrand); assertTrue(vm.isCustomModel); assertEquals(" Prototype ", vm.model)
        for (year in listOf("", "2147483648", "0")) {
            vm.onYearChange(year); vm.save(); runCurrent()
            assertInstanceOf(UiEvent.ShowSnackbar::class.java, vm.uiEvent.first())
        }
        vm.onYearChange("2020"); vm.onPowerHpChange("abc"); vm.save(); runCurrent()
        assertTrue(vm.isPowerError); vm.uiEvent.first()
        vm.onPowerHpChange("90"); vm.onTankCapacityChange("invalid"); vm.save(); runCurrent()
        assertTrue(vm.isCapacityError); vm.uiEvent.first()
        coVerify(exactly = 0) { vehicles.saveVehicle(any()) }
        vm.onYearChange(" 2020 "); vm.onPowerHpChange(" 90 ")
        vm.onTankCapacityChange("12,5"); vm.save(); runCurrent()
        assertFalse(vm.isYearError); assertFalse(vm.isPowerError); assertFalse(vm.isCapacityError)
        assertEquals(UiEvent.Success, vm.uiEvent.first())
        coVerify(exactly = 1) { vehicles.saveVehicle(match { it.brand == "Artisan" && it.model == "Prototype" && it.tankCapacity == 12.5 }) }
    }

    @Test fun `maintenance locked CT performed date comma cost and post commit alarm warning never resave`() = runTest(dispatcher) {
        val repository = mockk<MaintenanceRepository>()
        val saved = slot<MaintenanceRecord>()
        coEvery { repository.saveMaintenanceRecord(capture(saved)) } returns 8L
        mockkObject(NotificationHelper)
        every { NotificationHelper.scheduleReminder(any(), any(), any(), any(), any(), any()) } throws SecurityException("alarm refused")
        val useCase = SaveMaintenanceUseCase(repository, vehicles, session, context)
        val vm = AddMaintenanceViewModel(useCase, vehicles, context, session, SavedStateHandle(mapOf("vehicleId" to 1L, "initialType" to "TECHNICAL_INSPECTION")))
        runCurrent()
        vm.onTypeChange(MaintenanceType.OIL_CHANGE)
        assertEquals(MaintenanceType.TECHNICAL_INSPECTION, vm.type)
        vm.onDateChange(0)
        vm.onNextDueDateChange(System.currentTimeMillis() + 86_400_000)
        for (cost in listOf("-1", "invalid", "Infinity")) {
            vm.onCostChange(cost); vm.save(); runCurrent()
            assertInstanceOf(UiEvent.ShowSnackbar::class.java, vm.uiEvent.first())
        }
        coVerify(exactly = 0) { repository.saveMaintenanceRecord(any()) }
        vm.onCostChange("12,5"); vm.save(); vm.save(); runCurrent()
        assertEquals(0L, saved.captured.date); assertEquals(12.5, saved.captured.cost)
        assertTrue((vm.uiEvent.first() as UiEvent.ShowSnackbar).message.contains("enregistrée"))
        assertEquals(UiEvent.Success, vm.uiEvent.first()); assertTrue(vm.hasSaved); assertFalse(vm.isSaving)
        vm.save(); runCurrent()
        coVerify(exactly = 1) { repository.saveMaintenanceRecord(any()) }
        verify(exactly = 1) { NotificationHelper.scheduleReminder(any(), any(), any(), any(), "firebase:A", 8L) }
    }
}
