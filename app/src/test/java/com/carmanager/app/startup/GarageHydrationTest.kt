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
        session.setAuthenticatedUid(null); runCurrent()
        assertEquals(listOf(LocalDataState.Loading), states)
        snapshots.emit(listOf("vehicle")); runCurrent()
        assertEquals(listOf(LocalDataState.Loading, LocalDataState.Ready(WorkspaceOwner.GUEST, listOf("vehicle"))), states)
    }

    @Test fun `real empty snapshot becomes Ready empty only after Room emits`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid(null) }
        val snapshots = MutableSharedFlow<List<String>>()
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { snapshots }.toList(states)
        }
        runCurrent(); assertEquals(LocalDataState.Loading, states.last())
        snapshots.emit(emptyList()); runCurrent()
        assertEquals(LocalDataState.Ready(WorkspaceOwner.GUEST, emptyList<String>()), states.last())
    }

    @Test fun `guest A B and logout discard old snapshots before the new owner's first data`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid(null) }
        val sources = listOf(WorkspaceOwner.GUEST, "firebase:A", "firebase:B").associateWith { MutableSharedFlow<List<String>>() }
        val states = mutableListOf<LocalDataState<List<String>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeLocalState(session, MutableStateFlow(0)) { sources.getValue(it) }.toList(states)
        }
        runCurrent()
        var previous: String? = null
        for (uid in listOf(null, "A", "B", null)) {
            val owner = WorkspaceOwner.fromUid(uid)
            session.setAuthenticatedUid(uid); runCurrent()
            assertEquals(LocalDataState.Loading, states.last())
            previous?.let { sources.getValue(it).emit(listOf("stale")) }; runCurrent()
            assertEquals(LocalDataState.Loading, states.last())
            sources.getValue(owner).emit(listOf(owner)); runCurrent()
            assertEquals(LocalDataState.Ready(owner, listOf(owner)), states.last())
            previous = owner
        }
        assertFalse(states.filterIsInstance<LocalDataState.Ready<List<String>>>().any { it.data.isEmpty() || it.data == listOf("stale") })
    }

    @Test fun `owner guard suppresses a cached Ready or Error during UI recomposition`() {
        val ready = LocalDataState.Ready("firebase:A", listOf("A"))
        assertSame(ready, ready.forOwner("firebase:A"))
        assertEquals(LocalDataState.Loading, ready.forOwner("firebase:B"))
        assertEquals(LocalDataState.Loading, ready.forOwner(WorkspaceOwner.GUEST))
        assertEquals(LocalDataState.Loading, LocalDataState.Error("firebase:A").forOwner("firebase:B"))
    }

    @Test fun `local failure becomes Error with explicit retry and no automatic retry loop`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid("A") }
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
        runCurrent(); assertEquals(LocalDataState.Error("firebase:A"), states.last())
        advanceTimeBy(10_000); runCurrent(); assertEquals(1, attempts)
        retry.value++; runCurrent(); assertEquals(LocalDataState.Loading, states.last())
        snapshots.emit(listOf("A")); runCurrent()
        assertEquals(LocalDataState.Ready("firebase:A", listOf("A")), states.last())
        assertEquals(2, attempts)
    }

    @Test fun `query cancellation is never shown as a local error`() = runTest {
        val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid(null) }
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
            val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid(null) }
            val source = MutableSharedFlow<List<Vehicle>>()
            val repository = mockk<VehicleRepository>()
            every { repository.observeAll() } returns source
            val vm = VehiclesViewModel(GetVehiclesUseCase(repository), mockk(), session)
            store.put("vehicles", vm)
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            val observer = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent(); source.emit(emptyList()); runCurrent()
            assertEquals(LocalDataState.Ready(WorkspaceOwner.GUEST, emptyList<Vehicle>()), vm.uiState.value)
            observer.cancel(); runCurrent(); advanceTimeBy(5001); runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            session.setAuthenticatedUid("B")
            backgroundScope.launch { vm.uiState.collect {} }; runCurrent()
            assertEquals(LocalDataState.Loading, vm.uiState.value)
            source.emit(emptyList()); runCurrent()
            assertEquals(LocalDataState.Ready("firebase:B", emptyList<Vehicle>()), vm.uiState.value)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
