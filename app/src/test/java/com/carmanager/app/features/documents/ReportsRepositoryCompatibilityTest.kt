package com.carmanager.app.features.documents

import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.data.local.dao.DocumentDao
import com.carmanager.app.core.data.local.entity.DocumentEntity
import com.carmanager.app.core.data.repository.DocumentRepositoryImpl
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportsRepositoryCompatibilityTest {
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid("A") }
    private val dao = mockk<DocumentDao>()
    private val access = mockk<OwnedDatabaseAccess>()
    private val repository = DocumentRepositoryImpl(dao, session, access)
    private fun entity(category: String, id: Long = 1) = DocumentEntity(id, 7, "Titre", category, "/private/$id.pdf", id)
    @Test fun `all eight previous categories and REPORTS round trip through actual repository`() = runTest {
        every { dao.observeByVehicle(7, "firebase:A") } returns flowOf(DocumentCategory.entries.map { entity(it.name) })
        assertEquals(DocumentCategory.entries, repository.observeByVehicle(7).first().map { it.category })
    }
    @Test fun `legacy and unknown category fallbacks are preserved`() = runTest {
        every { dao.observeByVehicle(7, "firebase:A") } returns flowOf(listOf("PHOTO", "INVOICE", "future").map { entity(it) })
        assertEquals(listOf(DocumentCategory.PHOTOS, DocumentCategory.MAINTENANCE, DocumentCategory.OTHER), repository.observeByVehicle(7).first().map { it.category })
    }
    @Test fun `REPORTS is stored as enum name under owned write`() = runTest {
        val saved = slot<DocumentEntity>()
        coEvery { access.write<Long>("firebase:A", 7, any()) } coAnswers { thirdArg<suspend () -> Long>().invoke() }
        coEvery { dao.insert(capture(saved)) } returns 41
        assertEquals(41, repository.saveDocument(Document(vehicleId = 7, title = "Rapport", category = DocumentCategory.REPORTS,
            filePath = "/private/report.pdf", date = 100, ownerKey = "firebase:A")))
        assertEquals("REPORTS", saved.captured.category); assertEquals(7, saved.captured.vehicleId)
        coVerify(exactly = 1) { access.write<Long>("firebase:A", 7, any()) }
    }
    @Test fun `vehicle query follows active workspace`() = runTest {
        every { dao.observeByVehicle(7, "firebase:A") } returns flowOf(listOf(entity("REPORTS")))
        every { dao.observeByVehicle(7, "firebase:B") } returns flowOf(emptyList())
        assertEquals("firebase:A", repository.observeByVehicle(7).first().single().ownerKey)
        session.setAuthenticatedUid("B"); assertTrue(repository.observeByVehicle(7).first().isEmpty())
    }
}
