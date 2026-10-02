package com.carmanager.app.ownership

import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.data.repository.VehicleRepositoryImpl
import com.carmanager.app.core.domain.session.WorkspaceSession
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VehicleRepositoryOwnershipTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `existing observer switches DAO workspace without late foreign rows`() = runTest {
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(listOf(testVehicle(firstArg<String>()))) }
        val session = WorkspaceSession(TestDeletionRegistry())
        val repository = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val seen = mutableListOf<String>()
        backgroundScope.launch { repository.observeAll().collect { seen += it.single().ownerKey } }
        runCurrent()
        session.setAuthenticatedUid("A"); runCurrent()
        session.setAuthenticatedUid("B"); runCurrent()
        session.setAuthenticatedUid(null); runCurrent()
        assertEquals(listOf("guest:local", "firebase:A", "firebase:B", "guest:local"), seen)
    }

    @Test fun `guest A B transitions request only their DAO scope and preserve rows`() = runTest {
        val rows = listOf(testVehicle("guest:local", 1), testVehicle("firebase:A", 2), testVehicle("firebase:B", 3))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        every { dao.observeById(any(), any()) } answers {
            flowOf(rows.find { it.id == firstArg<Long>() && it.ownerKey == secondArg<String>() })
        }
        val session = WorkspaceSession(TestDeletionRegistry())
        val repository = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        for ((uid, expectedId) in listOf(null to 1L, "A" to 2L, null to 1L, "A" to 2L, "B" to 3L)) {
            session.setAuthenticatedUid(uid)
            assertEquals(listOf(expectedId), repository.observeAll().first().map { it.id })
        }
        assertNull(repository.observeById(2).first())
        verify { dao.observeAll("guest:local"); dao.observeAll("firebase:A"); dao.observeAll("firebase:B") }
        verify { dao.observeById(2, "firebase:B") }
        assertEquals(3, rows.size)
    }
}
