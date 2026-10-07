package com.carmanager.app.a14_1

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.util.FileStorageHelper
import com.carmanager.app.features.documents.DocumentsViewModel
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsImportGuardTest {
    @Test fun `arbitrary report import is rejected before copying or database access`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store=ViewModelStore()
        mockkObject(FileStorageHelper)
        try {
            val garage=GarageFixture(); val repo=mockk<DocumentRepository>(); val context=mockk<Context>()
            every { repo.observeByVehicle(1) } returns flowOf(emptyList())
            val vm=DocumentsViewModel(repo,garage.session,garage.access,SavedStateHandle(mapOf("vehicleId" to 1L)),context)
            store.put("documents",vm)
            val events=mutableListOf<com.carmanager.app.core.util.UiEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvent.toList(events) }
            vm.addDocument(mockk<Uri>(),"Imported.pdf",DocumentCategory.REPORTS); runCurrent()
            coVerify(exactly=0) { repo.saveDocument(any()) }
            verify(exactly=0) { FileStorageHelper.saveFileToInternalStorage(any(),any(),any()) }
            coVerify(exactly=0) { garage.access.read<Any>(any(),any(),any()) }
            assertTrue(events.any { it is com.carmanager.app.core.util.UiEvent.ShowSnackbar && it.message.contains("Statistiques") })
        } finally { store.clear(); runCurrent(); unmockkObject(FileStorageHelper); Dispatchers.resetMain() }
    }
}
