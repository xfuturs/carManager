package com.carmanager.app.features.documents

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelActionsTest {
    @TempDir lateinit var directory: File
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
    private val context by lazy { mockk<Context>().also { every { it.filesDir } returns directory } }
    private val source by lazy { File(directory, "report.pdf").apply { writeText("%PDF-existing") } }
    private val doc get() = Document(11, 7, "Rapport", DocumentCategory.REPORTS, source.path, 0, "local:device")
    private fun scenario(body: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try { body(store) } finally { store.clear(); Dispatchers.resetMain() }
    }
    private fun documents(store: ViewModelStore, repository: DocumentRepository): DocumentsViewModel {
        val access = mockk<OwnedDatabaseAccess>()
        coEvery { access.write<Unit>(any(), any(), any()) } coAnswers {
            session.requireWritable(firstArg()); thirdArg<suspend () -> Unit>().invoke()
        }
        coEvery { access.documentFile(any(), any(), any(), any()) } returns source
        return DocumentsViewModel(repository, session, access, SavedStateHandle(mapOf("vehicleId" to 7L)), context)
            .also { store.put("documents", it) }
    }
    private fun export(store: ViewModelStore) = ReportActionsViewModel(mockk(), session, context).also { store.put("export", it) }
    @Test fun `existing report deletion needs no Premium and removes file and row`() = scenario { store ->
        val repository = mockk<DocumentRepository>(); coEvery { repository.deleteDocument(any()) } just Runs
        val vm = documents(store, repository); vm.deleteDocument(doc); runCurrent()
        coVerify(exactly = 1) { repository.deleteDocument(doc) }; assertFalse(source.exists())
    }
    @Test fun `stale workspace cannot delete original report`() = scenario { store ->
        val repository = mockk<DocumentRepository>(); val vm = documents(store, repository); val saved = doc
        session.beginBootstrap(); vm.deleteDocument(saved); runCurrent()
        coVerify(exactly = 0) { repository.deleteDocument(any()) }; assertTrue(source.exists())
    }
    @Test fun `wrong vehicle cannot delete report`() = scenario { store ->
        val repository = mockk<DocumentRepository>(); val vm = documents(store, repository)
        vm.deleteDocument(doc.copy(vehicleId = 8)); runCurrent()
        coVerify(exactly = 0) { repository.deleteDocument(any()) }; assertTrue(source.exists())
    }
    @Test fun `cancelled picker releases pending export without touching report`() = scenario { store ->
        val vm = export(store); assertEquals("report.pdf", vm.prepareExport(doc)); assertNull(vm.prepareExport(doc))
        vm.finishExport(null); assertEquals("report.pdf", vm.prepareExport(doc)); assertTrue(source.exists())
    }
    @Test fun `export viewmodel refuses old workspace during garage bootstrap`() = scenario { store ->
        val vm = export(store); val saved = doc; session.beginBootstrap()
        assertNull(vm.prepareExport(saved)); assertTrue(source.exists())
    }
}
