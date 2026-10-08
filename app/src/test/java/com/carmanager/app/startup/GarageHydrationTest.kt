package com.carmanager.app.startup

import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.features.vehicles.GetVehiclesUseCase
import com.carmanager.app.features.vehicles.VehiclesViewModel
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
class GarageHydrationTest {
    @Test fun `unknown local owner is Loading without querying provisional guest`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry())
        var queries = 0
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { queries++; flowOf(emptyList<String>()) }.toList(states)
        }
        runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
        assertEquals(0, queries)
        assertFalse(session.isResolved.value)
    }

    @Test fun `resolved guest waits for actual first snapshot then goes directly to content`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry())
        val snapshots = MutableSharedFlow<List<String>>()
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { snapshots }.toList(states)
        }
        session.completeBootstrap(); runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
        snapshots.emit(listOf("vehicle")); runCurrent()
        assertEquals(listOf(LocalDataState.Loading, LocalDataState.Ready(com.carmanager.app.core.domain.session.LocalGarageOwner.KEY, listOf("vehicle"))), states)
    }

    @Test fun `real empty snapshot becomes Ready empty only after Room emits`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val snapshots = MutableSharedFlow<List<String>>()
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { snapshots }.toList(states)
        }
        runCurrent(); assertEquals(LocalDataState.Loading, states.last())
        snapshots.emit(emptyList()); runCurrent()
        assertEquals(LocalDataState.Ready(com.carmanager.app.core.domain.session.LocalGarageOwner.KEY, emptyList<String>()), states.last())
    }

    @Test fun `guest A B and logout keep the same Ready without querying legacy owners`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val identity = AuthSession()
        var queries = 0
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { owner ->
                assertEquals("local:device", owner); queries++; flowOf(listOf("vehicle"))
            }.toList(states)
        }
        runCurrent()
        val ready = states.last()
        for (uid in listOf(null, "A", "B", null)) { identity.setUid(uid); runCurrent(); assertSame(ready, states.last()) }
        assertEquals(1, queries)
        assertEquals(listOf(LocalDataState.Loading, LocalDataState.Ready("local:device", listOf("vehicle"))), states)
    }

    @Test fun `owner guard suppresses a cached Ready or Error during UI recomposition`() {
        val ready = LocalDataState.Ready("local:device", listOf("A"))
        assertSame(ready, ready.forOwner("local:device"))
        assertEquals(LocalDataState.Loading, ready.forOwner("firebase:B"))
        assertEquals(LocalDataState.Loading, ready.forOwner(WorkspaceOwner.GUEST))
        assertEquals(LocalDataState.Loading, LocalDataState.Error("local:device").forOwner("firebase:B"))
    }

    @Test fun `local failure becomes Error with explicit retry and no automatic retry loop`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val retry = MutableStateFlow(0)
        val snapshots = MutableSharedFlow<List<String>>()
        val states = mutableListOf<LocalDataState<List<String>>>()
        var attempts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, retry) {
                attempts++
                if (attempts == 1) flow { throw IOException("database unavailable") } else snapshots
            }.toList(states)
        }
        runCurrent(); assertEquals(LocalDataState.Error("local:device"), states.last())
        advanceTimeBy(10_000); runCurrent(); assertEquals(1, attempts)
        retry.value++; runCurrent(); assertEquals(LocalDataState.Loading, states.last())
        snapshots.emit(listOf("A")); runCurrent()
        assertEquals(LocalDataState.Ready("local:device", listOf("A")), states.last())
        assertEquals(2, attempts)
    }

    @Test fun `query cancellation is never shown as a local error`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val states = mutableListOf<LocalDataState<List<String>>>()
        val job = launch {
            observeLocalState(session, MutableStateFlow(0)) { flow<List<String>> { awaitCancellation() } }.toList(states)
        }
        runCurrent(); job.cancelAndJoin()
        assertEquals(listOf(LocalDataState.Loading), states)
    }

    @Test fun `Vehicles ViewModel starts Loading and resets stopped replay before new owner`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
            val source = MutableSharedFlow<List<Vehicle>>()
            val repository = mockk<VehicleRepository>()
            every { repository.observeAll() } returns source
            val vm = VehiclesViewModel(GetVehiclesUseCase(repository), mockk(), session)
            store.put("vehicles", vm)
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            val observer = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent(); source.emit(emptyList()); runCurrent()
            assertEquals(LocalDataState.Ready(com.carmanager.app.core.domain.session.LocalGarageOwner.KEY, emptyList<Vehicle>()), vm.uiState.value)
            observer.cancel(); runCurrent(); advanceTimeBy(5001); runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            com.carmanager.app.core.domain.session.AuthSession().setUid("B")
            backgroundScope.launch { vm.uiState.collect {} }; runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            source.emit(emptyList()); runCurrent()
            assertEquals(LocalDataState.Ready("local:device", emptyList<Vehicle>()), vm.uiState.value)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
