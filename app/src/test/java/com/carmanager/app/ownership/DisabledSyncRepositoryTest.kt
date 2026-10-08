package com.carmanager.app.ownership

import com.carmanager.app.core.data.repository.DisabledSyncRepository
import com.carmanager.app.core.domain.repository.GarageSyncStatus
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DisabledSyncRepositoryTest {
    @Test fun `guest and Google sessions never activate Firestore or surface stale sync errors`() = runTest {
        mockkStatic(FirebaseFirestore::class)
        try {
            every { FirebaseFirestore.getInstance() } throws AssertionError("Unexpected network client")
            val sync = DisabledSyncRepository()
            val session = WorkspaceSession(TestDeletionRegistry())
            for (uid in listOf(null, "A", "B", null, "A")) {
                session.completeBootstrap()
                sync.startAutoSync()
                sync.stopSync()
                assertEquals(GarageSyncStatus.DISABLED, sync.status.value)
                assertFalse(sync.isSyncing.value)
                assertNull(sync.syncError.value)
            }
            verify(exactly = 0) { FirebaseFirestore.getInstance() }
        } finally { unmockkStatic(FirebaseFirestore::class) }
    }

    @Test fun `explicit backup request reports disabled and never claims backup success`() = runTest {
        val sync = DisabledSyncRepository()
        assertTrue(sync.syncAll().isFailure)
        assertEquals(GarageSyncStatus.DISABLED, sync.status.value)
        assertFalse(sync.isSyncing.value)
        assertNull(sync.syncError.value)
    }
}
