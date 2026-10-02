package com.carmanager.app.startup

import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.WorkspaceOwner
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.features.dashboard.DashboardViewModel
import com.carmanager.app.features.dashboard.GetDashboardStatsUseCase
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardHydrationTest {
    private class Snapshots {
        val vehicles = MutableSharedFlow<List<Vehicle>>(replay = 1)
        val fuel = MutableSharedFlow<List<FuelRecord>>(replay = 1)
        val maintenance = MutableSharedFlow<List<MaintenanceRecord>>(replay = 1)
        val documents = MutableSharedFlow<List<Document>>(replay = 1)
        val fuelTotal = MutableSharedFlow<Double>(replay = 1)
        val maintenanceTotal = MutableSharedFlow<Double>(replay = 1)
        val next = MutableSharedFlow<MaintenanceRecord?>(replay = 1)
        fun emitSecondary(except: String? = null) {
            fuel.tryEmit(emptyList()); documents.tryEmit(emptyList())
            if (except != "maintenance") maintenance.tryEmit(emptyList())
            if (except != "fuelTotal") fuelTotal.tryEmit(18.0)
            maintenanceTotal.tryEmit(22.0)
            if (except != "next") next.tryEmit(null)
        }
    }

    private class Fixture {
        val session = WorkspaceSession(TestDeletionRegistry())
        val sources = listOf(WorkspaceOwner.GUEST, "firebase:A", "firebase:B").associateWith { Snapshots() }
        val vehicles = mockk<VehicleRepository>()
        val fuel = mockk<FuelRepository>()
        val maintenance = mockk<MaintenanceRepository>()
        val documents = mockk<DocumentRepository>()
        var fail = false
        val useCase: GetDashboardStatsUseCase
        init {
            every { vehicles.observeAll() } answers {
                if (fail) flow { throw IOException("query failed") } else current().vehicles
            }
            every { fuel.observeAll() } answers { current().fuel }
            every { maintenance.observeAll() } answers { current().maintenance }
            every { documents.observeAll() } answers { current().documents }
            every { fuel.observeMonthlyTotal(any()) } answers { current().fuelTotal }
            every { maintenance.observeMonthlyTotal(any()) } answers { current().maintenanceTotal }
            every { maintenance.observeNextUpcoming() } answers { current().next }
            useCase = GetDashboardStatsUseCase(vehicles, fuel, maintenance, documents, session)
        }
        fun current() = sources.getValue(session.owner.value)
        fun vehicle(id: Long = 1) = Vehicle(id = id, brand = "Test", model = "Car", year = 2020,
            currentMileage = 1000, fuelType = FuelType.GASOLINE, powerHp = 100,
            licensePlate = null, createdAt = 0, updatedAt = 0, ownerKey = session.owner.value)
    }

    @Test fun `Dashboard unknown owner and unresolved vehicles never create fake zero summary`() = runTest {
        val fixture = Fixture()
        fixture.current().emitSecondary()
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
        verify(exactly = 0) { fixture.vehicles.observeAll() }
        fixture.session.setAuthenticatedUid(null); runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
    }

    @Test fun `first nonempty guest snapshot goes directly to coherent content`() = runTest {
        val fixture = Fixture().apply { session.setAuthenticatedUid(null) }
        fixture.current().emitSecondary()
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent(); fixture.current().vehicles.emit(listOf(fixture.vehicle())); runCurrent()
        assertEquals(2, states.size)
        assertEquals(LocalDataState.Loading, states.first())
        val ready = states.last() as LocalDataState.Ready
        assertEquals(WorkspaceOwner.GUEST, ready.owner)
        assertEquals(1, ready.data.vehicleCount)
        assertEquals(1, ready.data.vehicles.size)
        assertEquals(18.0, ready.data.monthlyFuelCost)
        assertEquals(22.0, ready.data.monthlyMaintenanceCost)
        verify(exactly = 0) { fixture.vehicles.observeCount() }
    }

    @Test fun `first truly empty snapshot creates the real Empty dashboard`() = runTest {
        val fixture = Fixture().apply { session.setAuthenticatedUid(null) }
        fixture.current().emitSecondary()
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent(); assertEquals(LocalDataState.Loading, states.last())
        fixture.current().vehicles.emit(emptyList()); runCurrent()
        val ready = states.last() as LocalDataState.Ready
        assertEquals(0, ready.data.vehicleCount)
        assertTrue(ready.data.vehicles.isEmpty())
        assertEquals(2, states.size)
    }

    @Test fun `required deadline and total queries remain unresolved instead of showing fake final zeros`() = runTest {
        for (delayed in listOf("maintenance", "fuelTotal", "next")) {
            val fixture = Fixture().apply { session.setAuthenticatedUid("A") }
            fixture.current().emitSecondary(except = delayed)
            fixture.current().vehicles.emit(listOf(fixture.vehicle()))
            val states = mutableListOf<LocalDataState<DashboardStats>>()
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.useCase.observeState(MutableStateFlow(0)).toList(states)
            }
            runCurrent(); assertEquals(listOf(LocalDataState.Loading), states)
            when (delayed) {
                "maintenance" -> fixture.current().maintenance.emit(emptyList())
                "fuelTotal" -> fixture.current().fuelTotal.emit(18.0)
                "next" -> fixture.current().next.emit(null)
            }
            runCurrent()
            val ready = states.last() as LocalDataState.Ready
            assertEquals("firebase:A", ready.owner)
            assertEquals(1, ready.data.vehicleCount)
            assertEquals(18.0, ready.data.monthlyFuelCost)
            observer.cancel(); runCurrent()
        }
    }

    @Test fun `guest A B and logout rehydrate only the current owner's dashboard`() = runTest {
        val fixture = Fixture()
        fixture.sources.values.forEach { it.emitSecondary() }
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent()
        for ((index, uid) in listOf(null, "A", "B", null).withIndex()) {
            fixture.session.setAuthenticatedUid(uid); runCurrent()
            if (index == 3) { // Guest's real earlier snapshot may be immediately available again.
                assertTrue(states.last() is LocalDataState.Loading ||
                    (states.last() as LocalDataState.Ready).owner == WorkspaceOwner.GUEST)
            } else assertEquals(LocalDataState.Loading, states.last())
            val vehicle = fixture.vehicle(index + 1L)
            fixture.current().vehicles.emit(listOf(vehicle)); runCurrent()
            val ready = states.last() as LocalDataState.Ready
            assertEquals(WorkspaceOwner.fromUid(uid), ready.owner)
            assertEquals(vehicle, ready.data.vehicles.single().vehicle)
            assertEquals(1, ready.data.vehicleCount)
        }
        assertTrue(states.filterIsInstance<LocalDataState.Ready<DashboardStats>>().all {
            it.data.vehicles.all { stats -> stats.vehicle.ownerKey == it.owner } && it.data.vehicleCount == 1
        })
    }

    @Test fun `Dashboard query failure is Error and an explicit retry rehydrates real data`() = runTest {
        val fixture = Fixture().apply { session.setAuthenticatedUid("A"); fail = true }
        val retry = MutableStateFlow(0)
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(retry).toList(states) }
        runCurrent(); assertEquals(LocalDataState.Error("firebase:A"), states.last())
        fixture.fail = false; retry.value++; runCurrent()
        assertEquals(LocalDataState.Loading, states.last())
        fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle())); runCurrent()
        assertEquals(1, (states.last() as LocalDataState.Ready).data.vehicleCount)
    }

    @Test fun `Dashboard ViewModel starts Loading without Billing readiness and drops stopped replay`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val fixture = Fixture().apply { session.setAuthenticatedUid("A") }
            val settings = mockk<SettingsRepository>().also {
                every { it.currency } returns flowOf("€")
                every { it.distanceUnit } returns flowOf("km")
            }
            val premium = mockk<PremiumRepository>().also { every { it.isPremium } returns MutableStateFlow(false) }
            val vm = DashboardViewModel(fixture.useCase, mockk(), settings, premium, mockk())
            store.put("dashboard", vm)
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            val observer = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent(); fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle())); runCurrent()
            assertEquals(1, (vm.uiState.value as LocalDataState.Ready).data.vehicleCount)
            observer.cancel(); runCurrent(); advanceTimeBy(5001); runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            fixture.session.setAuthenticatedUid("B")
            backgroundScope.launch { vm.uiState.collect {} }; runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle(2))); runCurrent()
            assertEquals("firebase:B", (vm.uiState.value as LocalDataState.Ready).owner)
            verify(exactly = 0) { premium.initialize() }
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
