package com.carmanager.app.a14

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.features.documents.DocumentsViewModel
import com.carmanager.app.consistency.GarageFixture
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDeletionConfirmationTest {
    @TempDir lateinit var directory: File
    private fun scenario(category: DocumentCategory, body: suspend TestScope.(DocumentsViewModel, DocumentRepository, Document, File, File, GarageFixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val garage = GarageFixture(); val repo = mockk<DocumentRepository>(); val context = mockk<Context>()
            every { context.filesDir } returns directory
            val privateDir = File(directory, "vehicle_documents").apply { mkdirs() }
            val privateFile = File(privateDir,"report.pdf").apply { writeText("%PDF-private") }
            val external = File(directory,"external-copy.pdf").apply { writeText("%PDF-external") }
            val document = Document(4,1,"Document", category,privateFile.path,123,"local:device")
            every { repo.observeByVehicle(1) } returns flowOf(listOf(document))
            coEvery { repo.deleteDocument(any()) } just Runs
            coEvery { garage.access.documentFile(any(),any(),any(),any()) } coAnswers {
                garage.session.requireWritable(firstArg()); privateFile
            }
            val vm = DocumentsViewModel(repo,garage.session,garage.access,SavedStateHandle(mapOf("vehicleId" to 1L)),context)
            store.put("documents",vm); backgroundScope.launch { vm.uiEvent.collect {} }; runCurrent()
            body(vm,repo,document,privateFile,external,garage)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @ParameterizedTest @EnumSource(DocumentCategory::class) fun `tap and cancellation never delete any category`(category: DocumentCategory) = scenario(category) { vm,repo,doc,file,copy,_ ->
        vm.requestDeletion(doc); runCurrent(); assertEquals(doc, vm.pendingDeletion)
        assertTrue(file.exists()); assertTrue(copy.exists()); coVerify(exactly = 0) { repo.deleteDocument(any()) }
        vm.cancelDeletion(); vm.confirmDeletion(); runCurrent(); assertNull(vm.pendingDeletion)
        assertTrue(file.exists()); coVerify(exactly = 0) { repo.deleteDocument(any()) }
    }
    @ParameterizedTest @EnumSource(DocumentCategory::class) fun `confirmed delete runs once and retains external copy`(category: DocumentCategory) = scenario(category) { vm,repo,doc,file,copy,_ ->
        vm.requestDeletion(doc); vm.confirmDeletion(); vm.confirmDeletion(); runCurrent()
        assertNull(vm.pendingDeletion); assertFalse(file.exists()); assertEquals("%PDF-external",copy.readText())
        coVerify(exactly = 1) { repo.deleteDocument(doc) }
    }
    @Test fun `garage unavailable while confirmation is open prevents file and row deletion`() = scenario(DocumentCategory.REPORTS) { vm,repo,doc,file,_,garage ->
        vm.requestDeletion(doc); garage.session.beginBootstrap(); vm.confirmDeletion(); runCurrent()
        assertNull(vm.pendingDeletion); assertTrue(file.exists()); coVerify(exactly = 0) { repo.deleteDocument(any()) }
    }
    @Test fun `foreign owner or other vehicle cannot open a confirmation`() = scenario(DocumentCategory.REPORTS) { vm,_,doc,_,_,_ ->
        vm.requestDeletion(doc.copy(ownerKey = "firebase:B")); assertNull(vm.pendingDeletion)
        vm.requestDeletion(doc.copy(vehicleId = 2)); assertNull(vm.pendingDeletion)
    }
    @Test fun `blocked owner after confirmation retains local data`() = scenario(DocumentCategory.REPORTS) { vm,repo,doc,file,_,garage ->
        vm.requestDeletion(doc); garage.registry.block("local:device"); vm.confirmDeletion(); runCurrent()
        assertTrue(file.exists()); coVerify(exactly = 0) { repo.deleteDocument(any()) }
    }
}
