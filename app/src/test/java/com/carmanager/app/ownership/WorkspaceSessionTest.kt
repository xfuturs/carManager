package com.carmanager.app.ownership

import com.carmanager.app.core.domain.session.WorkspaceOwner
import com.carmanager.app.core.domain.session.WorkspaceSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkspaceSessionTest {
    @Test fun `guest and Firebase identifiers cannot collide`() {
        assertEquals("guest:local", WorkspaceOwner.fromUid(null))
        assertEquals("firebase:A", WorkspaceOwner.fromUid("A"))
        assertEquals("firebase:guest:local", WorkspaceOwner.fromUid("guest:local"))
        assertNotEquals(WorkspaceOwner.fromUid("A"), WorkspaceOwner.fromUid("B"))
        assertNull(WorkspaceOwner.uid(WorkspaceOwner.GUEST))
        assertEquals("A", WorkspaceOwner.uid("firebase:A"))
        assertThrows(IllegalArgumentException::class.java) { WorkspaceOwner.fromUid("") }
    }

    @Test fun `stale forms and deletion blocked owners cannot write`() = runTest {
        val registry = TestDeletionRegistry()
        val session = WorkspaceSession(registry)
        val guest = session.owner.value
        session.setAuthenticatedUid("A")
        assertThrows(IllegalStateException::class.java) { session.requireWritable(guest) }
        session.requireWritable("firebase:A")
        registry.block("firebase:A")
        assertThrows(IllegalStateException::class.java) { session.requireWritable("firebase:A") }
        session.setAuthenticatedUid("B")
        session.requireWritable("firebase:B")
        session.setAuthenticatedUid(null)
        session.requireWritable(guest)
    }
}
