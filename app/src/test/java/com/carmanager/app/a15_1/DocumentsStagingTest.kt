package com.carmanager.app.a15_1

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.util.FileStorageHelper
import com.carmanager.app.core.util.UiEvent
import com.carmanager.app.features.documents.DocumentsViewModel
import io.mockk.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsStagingTest {
    @TempDir lateinit var root: File
    private val garage=GarageFixture()
    private val repository=mockk<DocumentRepository>()
    private val context=mockk<Context>()
    private val uri=mockk<Uri>()
    private var inRead=false
    private var inWrite=false
    private var failInsert=false
    private val saved=mutableListOf<Document>()
    private val source get()=File(root,"vehicle_documents/source.jpg").apply { parentFile!!.mkdirs(); if (!exists()) writeText("original") }
    private val destination get()=File(root,"vehicle_documents/staged.pdf").apply { parentFile!!.mkdirs() }
    private val document get()=Document(4,1,"Photo",DocumentCategory.PHOTOS,source.path,0,"local:device")
    private fun scenario(body: suspend TestScope.(DocumentsViewModel) -> Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); mockkObject(FileStorageHelper); val store=ViewModelStore()
        try {
            every { context.filesDir } returns root
            every { repository.observeByVehicle(1) } returns flowOf(emptyList())
            coEvery { garage.access.read<Any>(any(),any(),any()) } coAnswers {
                garage.session.requireWritable(firstArg()); inRead=true
                try { thirdArg<suspend () -> Any>()() } finally { inRead=false }
            }
            coEvery { garage.access.write<Any>(any(),any(),any()) } coAnswers {
                garage.session.requireWritable(firstArg()); inWrite=true
                try { thirdArg<suspend () -> Any>()().also { garage.session.requireWritable(firstArg()) } } finally { inWrite=false }
            }
            coEvery { garage.access.documentFile(any(),any(),any(),any(),any()) } coAnswers { source.canonicalFile }
            coEvery { repository.saveDocument(any()) } coAnswers {
                assertTrue(inWrite); assertFalse(inRead)
                if (failInsert) throw IOException("private SQL detail")
                val row=firstArg<Document>(); assertEquals("complete",File(row.filePath).readText()); saved += row.copy(id=9); 9L
            }
            every { FileStorageHelper.saveFileToInternalStorage(context,uri,any()) } answers {
                assertFalse(inWrite); assertFalse(inRead); thirdArg<() -> Unit>()()
                destination.apply { writeText("complete") }.path
            }
            every { FileStorageHelper.convertImageToPdf(context,any(),any()) } answers {
                assertFalse(inWrite); assertFalse(inRead); thirdArg<() -> Unit>()()
                destination.apply { writeText("complete") }.path
            }
            every { FileStorageHelper.deleteFile(any()) } answers { File(firstArg<String>()).delete() }
            val vm=DocumentsViewModel(repository,garage.session,garage.access,SavedStateHandle(mapOf("vehicleId" to 1L)),context)
            store.put("documents",vm); body(vm)
        } finally { store.clear(); runCurrent(); unmockkObject(FileStorageHelper); Dispatchers.resetMain() }
    }
    @Test fun `import completes outside owned SQL and indexes exact destination inside write`()=scenario { vm ->
        vm.addDocument(uri,"Facture",DocumentCategory.MAINTENANCE); assertTrue((vm.uiEvent.first() as UiEvent.ShowSnackbar).message.contains("succès"))
        assertEquals(destination.path,saved.single().filePath); assertEquals("local:device",saved.single().ownerKey)
    }
    @Test fun `conversion completes outside SQL and preserves indexed source`()=scenario { vm ->
        vm.convertToPdf(document); assertEquals("Conversion réussie",(vm.uiEvent.first() as UiEvent.ShowSnackbar).message)
        assertEquals(destination.path,saved.single().filePath); assertEquals("original",source.readText())
    }
    @Test fun `failed import insertion removes destination and reports sanitized error`()=scenario { vm ->
        failInsert=true; vm.addDocument(uri,"Facture",DocumentCategory.MAINTENANCE)
        val message=(vm.uiEvent.first() as UiEvent.ShowSnackbar).message
        assertTrue(message.startsWith("Import impossible")); assertFalse(message.contains("SQL")); assertFalse(destination.exists()); assertTrue(saved.isEmpty())
    }
    @Test fun `failed conversion insertion removes PDF and retains source image`()=scenario { vm ->
        failInsert=true; vm.convertToPdf(document); assertTrue((vm.uiEvent.first() as UiEvent.ShowSnackbar).message.startsWith("Conversion impossible"))
        assertFalse(destination.exists()); assertEquals("original",source.readText()); assertTrue(saved.isEmpty())
    }
    @Test fun `owner change after IO staging prevents insert and cleans staged copy`()=scenario { vm ->
        every { FileStorageHelper.saveFileToInternalStorage(context,uri,any()) } answers {
            assertFalse(inWrite); destination.writeText("complete"); garage.session.beginBootstrap(); destination.path
        }
        vm.addDocument(uri,"Facture",DocumentCategory.MAINTENANCE); vm.uiEvent.first()
        assertTrue(saved.isEmpty()); assertFalse(destination.exists()); coVerify(exactly=0) { repository.saveDocument(any()) }
    }
    @Test fun `provider failure cannot create or index fake successful document`()=scenario { vm ->
        every { FileStorageHelper.saveFileToInternalStorage(context,uri,any()) } throws IOException("content URI")
        vm.addDocument(uri,"Facture",DocumentCategory.MAINTENANCE); assertTrue((vm.uiEvent.first() as UiEvent.ShowSnackbar).message.startsWith("Import impossible"))
        assertTrue(saved.isEmpty()); assertFalse(destination.exists())
    }
}
