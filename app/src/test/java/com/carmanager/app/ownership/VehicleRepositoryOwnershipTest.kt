package com.carmanager.app.ownership

import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.data.repository.VehicleRepositoryImpl
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.AuthSession
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
    @Test fun `existing observer keeps canonical DAO across auth identities`() = runTest {
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(listOf(testVehicle(firstArg<String>()))) }
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val repository = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val seen = mutableListOf<String>()
        backgroundScope.launch { repository.observeAll().collect { seen += it.single().ownerKey } }
        runCurrent()
        AuthSession().setUid("A"); runCurrent()
        AuthSession().setUid("B"); runCurrent()
        AuthSession().setUid(null); runCurrent()
        assertEquals(listOf("local:device"), seen)
    }

    @Test fun `guest A B transitions request canonical DAO and guard stale legacy rows`() = runTest {
        val rows = listOf(testVehicle("local:device", 1), testVehicle("local:device", 2), testVehicle("firebase:stale", 3))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        every { dao.observeById(any(), any()) } answers {
            flowOf(rows.find { it.id == firstArg<Long>() && it.ownerKey == secondArg<String>() })
        }
        val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val repository = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        for ((uid, expectedId) in listOf(null to 1L, "A" to 2L, null to 1L, "A" to 2L, "B" to 3L)) {
            AuthSession().setUid(uid)
            assertEquals(listOf(1L, 2L), repository.observeAll().first().map { it.id })
        }
        assertNull(repository.observeById(3).first())
        verify { dao.observeAll("local:device") }
        verify { dao.observeById(3, "local:device") }
        assertEquals(3, rows.size)
    }
}
