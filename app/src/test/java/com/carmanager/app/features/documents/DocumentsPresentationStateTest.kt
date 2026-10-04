package com.carmanager.app.features.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
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
class DocumentsPresentationStateTest {
    private fun scenario(resolved: Boolean = true, failFirst: Boolean = false,
        body: suspend TestScope.(WorkspaceSession, DocumentsViewModel, MutableSharedFlow<List<Document>>, DocumentRepository) -> Unit
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = WorkspaceSession(TestDeletionRegistry()).apply { if (resolved) setAuthenticatedUid(null) }
            val source = MutableSharedFlow<List<Document>>()
            var attempts = 0
            val repository = mockk<DocumentRepository>()
            every { repository.observeByVehicle(7) } answers {
                attempts++
                if (failFirst && attempts == 1) flow { throw IOException("unavailable") } else source
            }
            val vm = DocumentsViewModel(repository, session, mockk(), SavedStateHandle(mapOf("vehicleId" to 7L)), mockk())
            store.put("documents", vm)
            body(session, vm, source, repository)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    private fun TestScope.observe(vm: DocumentsViewModel) = backgroundScope.launch { vm.uiState.collect {} }
    private fun document(owner: String, id: Long, title: String, category: DocumentCategory) =
        Document(id, 7, title, category, "/internal/$id.pdf", 123456, owner)

    @Test fun `unresolved workspace never exposes an empty folder snapshot`() = scenario(resolved = false) { _, vm, _, repo ->
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        observe(vm); runCurrent(); vm.onSearchQueryChange("facture"); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        verify(exactly = 0) { repo.observeByVehicle(any()) }
    }

    @Test fun `ready empty requires an actual repository snapshot`() = scenario { session, vm, source, _ ->
        observe(vm); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        source.emit(emptyList()); runCurrent()
        assertEquals(LocalDataState.Ready(session.owner.value, emptyList<Document>()), vm.uiState.value)
    }

    @Test fun `search preserves case insensitive blank and unmatched behavior without reopening the query`() = scenario { session, vm, source, repo ->
        observe(vm); runCurrent()
        vm.onSearchQueryChange("FACTURE"); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        val docs = DocumentCategory.entries.mapIndexed { i, category -> document(session.owner.value, i + 1L,
            if (i == 0) "Facture complète" else "Document $i", category) }
        source.emit(docs); runCurrent()
        assertEquals(LocalDataState.Ready(session.owner.value, listOf(docs.first())), vm.uiState.value)
        vm.onSearchQueryChange("absent"); runCurrent()
        assertEquals(LocalDataState.Ready(session.owner.value, emptyList<Document>()), vm.uiState.value)
        for (blank in listOf("  ", "")) {
            vm.onSearchQueryChange(blank); runCurrent()
            assertEquals(LocalDataState.Ready(session.owner.value, docs), vm.uiState.value)
        }
        assertTrue(vm.canOpen(docs.first()))
        verify(exactly = 1) { repo.observeByVehicle(7) }
    }

    @Test fun `read error remains distinct from empty and retry keeps the search`() = scenario(failFirst = true) { session, vm, source, _ ->
        vm.onSearchQueryChange("facture"); observe(vm); runCurrent()
        assertEquals(LocalDataState.Error(session.owner.value), vm.uiState.value)
        vm.retryLoading(); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        assertEquals("facture", vm.searchQuery.value)
        source.emit(listOf(document(session.owner.value, 1, "Facture", DocumentCategory.OTHER))); runCurrent()
        assertTrue(vm.uiState.value is LocalDataState.Ready)
    }

    @Test fun `owner switch clears old Ready and existing file guard rejects the previous workspace`() = scenario { session, vm, source, _ ->
        observe(vm); runCurrent()
        val old = document(session.owner.value, 1, "Ancien document", DocumentCategory.PHOTOS)
        source.emit(listOf(old)); runCurrent()
        session.setAuthenticatedUid("B"); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        assertFalse(vm.canOpen(old))
        source.emit(emptyList()); runCurrent()
        assertEquals(LocalDataState.Ready("firebase:B", emptyList<Document>()), vm.uiState.value)
    }

    @Test fun `stopped screen discards replay and waits for fresh documents`() = scenario { _, vm, source, _ ->
        val observer = observe(vm); runCurrent(); source.emit(emptyList()); runCurrent()
        observer.cancel(); runCurrent(); advanceTimeBy(5001); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
        observe(vm); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
    }

    @Test fun `cancellation does not turn loading into a document error`() = scenario { _, vm, _, _ ->
        val observer = observe(vm); runCurrent(); observer.cancel(); runCurrent()
        advanceTimeBy(5001); runCurrent()
        assertEquals(LocalDataState.Loading, vm.uiState.value)
    }
}
