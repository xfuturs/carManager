package com.carmanager.app.features.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.features.fuel.*
import com.carmanager.app.features.maintenance.*
import com.carmanager.app.features.mileage.MileageHistoryViewModel
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPresentationStateTest {
    private data class Fixture(
        val vm: ViewModel,
        val state: StateFlow<LocalDataState<List<Any>>>,
        val retry: () -> Unit,
        val emit: suspend (Boolean) -> Unit
    )

    private fun fixtures(session: WorkspaceSession, failFirst: Boolean = false): List<Fixture> {
        val fuelSource = MutableSharedFlow<List<FuelRecord>>()
        val maintenanceSource = MutableSharedFlow<List<MaintenanceRecord>>()
        val mileageSource = MutableSharedFlow<List<MileageRecord>>()
        fun <T> query(source: Flow<T>): () -> Flow<T> {
            var attempts = 0
            return { attempts++; if (failFirst && attempts == 1) flow { throw IOException("unavailable") } else source }
        }
        val fuelQuery = query(fuelSource); val maintenanceQuery = query(maintenanceSource); val mileageQuery = query(mileageSource)
        val fuelRepo = mockk<FuelRepository>(); every { fuelRepo.observeByVehicle(7) } answers { fuelQuery() }
        val maintenanceRepo = mockk<MaintenanceRepository>(); every { maintenanceRepo.observeByVehicle(7) } answers { maintenanceQuery() }
        val mileageRepo = mockk<MileageRepository>(); every { mileageRepo.observeByVehicle(7) } answers { mileageQuery() }
        val fuel = FuelListViewModel(GetFuelRecordsUseCase(fuelRepo), mockk(), SavedStateHandle(mapOf("vehicleId" to 7L)), session)
        val maintenance = MaintenanceListViewModel(GetMaintenanceRecordsUseCase(maintenanceRepo), mockk(), SavedStateHandle(mapOf("vehicleId" to 7L)), session)
        val mileage = MileageHistoryViewModel(mileageRepo, SavedStateHandle(mapOf("vehicleId" to 7L)), session)
        return listOf(
            Fixture(fuel, fuel.uiState, fuel::retryLoading) { empty -> fuelSource.emit(if (empty) emptyList() else listOf(
                FuelRecord(id = 1, vehicleId = 7, date = 100, mileage = 1234, liters = 12.5, totalPrice = 20.0, isElectric = true, ownerKey = session.owner.value))) },
            Fixture(maintenance, maintenance.uiState, maintenance::retryLoading) { empty -> maintenanceSource.emit(if (empty) emptyList() else listOf(
                MaintenanceRecord(id = 2, vehicleId = 7, type = MaintenanceType.INSURANCE, date = 200, mileage = 1200, cost = 0.0,
                    note = "note", nextDueMileage = 2000, ownerKey = session.owner.value))) },
            Fixture(mileage, mileage.uiState, mileage::retryLoading) { empty -> mileageSource.emit(if (empty) emptyList() else MileageSource.entries.mapIndexed { index, source ->
                MileageRecord(id = index + 1L, vehicleId = 7, date = 300 + index.toLong(), mileage = 1300 + index, source = source, ownerKey = session.owner.value) }) }
        )
    }

    private fun scenario(resolved: Boolean = true, failFirst: Boolean = false, body: suspend TestScope.(WorkspaceSession, List<Fixture>) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = WorkspaceSession(TestDeletionRegistry()).apply { if (resolved) completeBootstrap() }
            val fixtures = fixtures(session, failFirst)
            fixtures.forEachIndexed { i, f -> store.put("$i", f.vm) }
            body(session, fixtures)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    private fun TestScope.observe(fixtures: List<Fixture>): List<Job> = fixtures.map { f -> backgroundScope.launch { f.state.collect {} } }

    @Test fun `unknown workspace remains Loading before and after subscription`() = scenario(resolved = false) { _, fixtures ->
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value) }
        observe(fixtures); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value) }
    }

    @Test fun `resolved history waits for actual first emission and retains all record fields`() = scenario { session, fixtures ->
        observe(fixtures); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value); it.emit(false) }
        runCurrent()
        fixtures.forEach { assertEquals(session.owner.value, (it.state.value as LocalDataState.Ready).owner) }
        val fuel = (fixtures[0].state.value as LocalDataState.Ready).data.single() as FuelRecord
        assertTrue(fuel.isElectric); assertEquals(12.5, fuel.liters); assertEquals(20.0, fuel.totalPrice); assertEquals(1234, fuel.mileage)
        val maintenance = (fixtures[1].state.value as LocalDataState.Ready).data.single() as MaintenanceRecord
        assertEquals(MaintenanceType.INSURANCE, maintenance.type); assertEquals("note", maintenance.note); assertEquals(2000, maintenance.nextDueMileage)
        val history = (fixtures[2].state.value as LocalDataState.Ready).data.map { it as MileageRecord }
        assertEquals(MileageSource.entries, history.map { it.source }); assertEquals(listOf(1300, 1301, 1302), history.map { it.mileage })
    }

    @Test fun `empty is Ready only after the real empty snapshot`() = scenario { session, fixtures ->
        observe(fixtures); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value); it.emit(true) }
        runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Ready(session.owner.value, emptyList<Any>()), it.state.value) }
    }

    @Test fun `query failure is Error and explicit retry waits for a new real snapshot`() = scenario(failFirst = true) { session, fixtures ->
        observe(fixtures); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Error(session.owner.value), it.state.value); it.retry() }
        runCurrent(); fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value); it.emit(true) }
        runCurrent(); fixtures.forEach { assertTrue(it.state.value is LocalDataState.Ready) }
    }

    @Test fun `garage bootstrap suppresses cached Ready until resolution`() = scenario { session, fixtures ->
        observe(fixtures); runCurrent(); fixtures.forEach { it.emit(false) }; runCurrent()
        session.beginBootstrap(); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value); it.emit(true) }
        runCurrent(); fixtures.forEach { assertEquals(LocalDataState.Loading,it.state.value) }
        session.completeBootstrap(); runCurrent(); fixtures.forEach { it.emit(true) }; runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Ready("local:device",emptyList<Any>()),it.state.value) }
    }

    @Test fun `stopped histories reset replay and return Loading on resubscription`() = scenario { _, fixtures ->
        val observers = observe(fixtures); runCurrent(); fixtures.forEach { it.emit(false) }; runCurrent()
        observers.forEach { it.cancel() }; runCurrent(); advanceTimeBy(5001); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value) }
        observe(fixtures); runCurrent(); fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value) }
    }

    @Test fun `cancelled subscribers do not turn loading into an error`() = scenario { _, fixtures ->
        val observers = observe(fixtures); runCurrent(); observers.forEach { it.cancel() }
        runCurrent(); advanceTimeBy(5001); runCurrent()
        fixtures.forEach { assertEquals(LocalDataState.Loading, it.state.value) }
    }
}
