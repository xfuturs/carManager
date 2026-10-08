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
    private class Snapshots(private val owner: String) {
        val vehicles = MutableSharedFlow<List<Vehicle>>(replay = 1)
        val fuel = MutableSharedFlow<List<FuelRecord>>(replay = 1)
        val maintenance = MutableSharedFlow<List<MaintenanceRecord>>(replay = 1)
        val documents = MutableSharedFlow<List<Document>>(replay = 1)
        fun emitSecondary(except: String? = null, vehicleId: Long = 1) {
            if (except != "fuel") fuel.tryEmit(listOf(FuelRecord(vehicleId=vehicleId,date=java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli(),mileage=1000,liters=10.0,totalPrice=18.0,ownerKey=owner)))
            if (except != "documents") documents.tryEmit(emptyList())
            if (except != "maintenance") maintenance.tryEmit(listOf(MaintenanceRecord(vehicleId=vehicleId,type=MaintenanceType.OIL_CHANGE,date=java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli(),mileage=1000,cost=22.0,ownerKey=owner)))
        }
    }

    private class Fixture(scheduler: TestCoroutineScheduler) {
        val session = WorkspaceSession(TestDeletionRegistry())
        val sources = listOf(com.carmanager.app.core.domain.session.LocalGarageOwner.KEY, "local:device", "firebase:B").associateWith { Snapshots(it) }
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
            useCase = GetDashboardStatsUseCase(vehicles, fuel, maintenance, documents, session,
                com.carmanager.app.features.dashboard.DashboardTimeSource({java.time.Instant.parse("2026-10-07T10:00:00Z")},{java.time.ZoneId.of("Europe/Paris")}),
                StandardTestDispatcher(scheduler))
        }
        fun current() = sources.getValue(session.owner.value)
        fun vehicle(id: Long = 1) = Vehicle(id = id, brand = "Test", model = "Car", year = 2020,
            currentMileage = 1000, fuelType = FuelType.GASOLINE, powerHp = 100,
            licensePlate = null, createdAt = 0, updatedAt = 0, ownerKey = session.owner.value)
    }

    @Test fun `Dashboard unknown owner and unresolved vehicles never create fake zero summary`() = runTest {
        val fixture = Fixture(testScheduler)
        fixture.current().emitSecondary()
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
        verify(exactly = 0) { fixture.vehicles.observeAll() }
        fixture.session.completeBootstrap(); runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
    }

    @Test fun `first nonempty guest snapshot goes directly to coherent content`() = runTest {
        val fixture = Fixture(testScheduler).apply { session.completeBootstrap() }
        fixture.current().emitSecondary()
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent(); fixture.current().vehicles.emit(listOf(fixture.vehicle())); runCurrent()
        assertEquals(2, states.size)
        assertEquals(LocalDataState.Loading, states.first())
        val ready = states.last() as LocalDataState.Ready
        assertEquals(com.carmanager.app.core.domain.session.LocalGarageOwner.KEY, ready.owner)
        assertEquals(1, ready.data.vehicleCount)
        assertEquals(1, ready.data.vehicles.size)
        assertEquals(18.0, ready.data.monthlyFuelCost)
        assertEquals(22.0, ready.data.monthlyMaintenanceCost)
        verify(exactly = 0) { fixture.vehicles.observeCount() }
    }

    @Test fun `first truly empty snapshot creates the real Empty dashboard`() = runTest {
        val fixture = Fixture(testScheduler).apply { session.completeBootstrap() }
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

    @Test fun `required history queries remain unresolved instead of showing fake final zeros`() = runTest {
        for (delayed in listOf("maintenance", "fuel", "documents")) {
            val fixture = Fixture(testScheduler).apply { session.completeBootstrap() }
            fixture.current().emitSecondary(except = delayed)
            fixture.current().vehicles.emit(listOf(fixture.vehicle()))
            val states = mutableListOf<LocalDataState<DashboardStats>>()
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.useCase.observeState(MutableStateFlow(0)).toList(states)
            }
            runCurrent(); assertEquals(listOf(LocalDataState.Loading), states)
            when (delayed) {
                "maintenance" -> fixture.current().emitSecondary()
                "fuel" -> fixture.current().emitSecondary()
                "documents" -> fixture.current().emitSecondary()
            }
            runCurrent()
            val ready = states.last() as LocalDataState.Ready
            assertEquals("local:device", ready.owner)
            assertEquals(1, ready.data.vehicleCount)
            assertEquals(18.0, ready.data.monthlyFuelCost)
            observer.cancel(); runCurrent()
        }
    }

    @Test fun `guest A B and logout retain the same coherent dashboard without rehydration`() = runTest {
        val fixture = Fixture(testScheduler).apply { session.completeBootstrap() }
        fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle()))
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(MutableStateFlow(0)).toList(states) }
        runCurrent()
        val ready = states.last() as LocalDataState.Ready
        val identity = com.carmanager.app.core.domain.session.AuthSession()
        for (uid in listOf(null, "A", "B", null)) {
            identity.setUid(uid); runCurrent(); assertSame(ready, states.last())
            assertEquals(1, ready.data.vehicleCount); assertEquals(18.0, ready.data.monthlyFuelCost)
            assertEquals(22.0, ready.data.monthlyMaintenanceCost)
        }
        assertEquals(2, states.size)
        verify(exactly = 1) { fixture.vehicles.observeAll() }
    }

    @Test fun `Dashboard query failure is Error and an explicit retry rehydrates real data`() = runTest {
        val fixture = Fixture(testScheduler).apply { session.completeBootstrap(); fail = true }
        val retry = MutableStateFlow(0)
        val states = mutableListOf<LocalDataState<DashboardStats>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase.observeState(retry).toList(states) }
        runCurrent(); assertEquals(LocalDataState.Error("local:device"), states.last())
        fixture.fail = false; retry.value++; runCurrent()
        assertEquals(LocalDataState.Loading, states.last())
        fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle())); runCurrent()
        assertEquals(1, (states.last() as LocalDataState.Ready).data.vehicleCount)
    }

    @Test fun `Dashboard ViewModel starts Loading without Billing readiness and drops stopped replay`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val fixture = Fixture(testScheduler).apply { session.completeBootstrap() }
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
            com.carmanager.app.core.domain.session.AuthSession().setUid("B")
            backgroundScope.launch { vm.uiState.collect {} }; runCurrent()
            assertEquals(1L, (vm.uiState.value as LocalDataState.Ready).data.vehicles.single().vehicle.id)
            fixture.current().emitSecondary(); fixture.current().vehicles.emit(listOf(fixture.vehicle(2))); runCurrent()
            assertEquals("local:device", (vm.uiState.value as LocalDataState.Ready).owner)
            verify(exactly = 0) { premium.initialize() }
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
