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

    @Test fun `canonical guards block legacy owners and unresolved writes but not account deletion`() = runTest {
        val registry = TestDeletionRegistry()
        val session = WorkspaceSession(registry)
        val local = session.owner.value
        assertEquals("local:device", local)
        assertThrows(IllegalStateException::class.java) { session.requireWritable(local) }
        session.completeBootstrap()
        session.requireWritable(local)
        for (owner in listOf("guest:local", "firebase:A", "firebase:B")) {
            assertThrows(IllegalStateException::class.java) { session.requireWritable(owner) }
            registry.block(owner)
        }
        session.requireWritable(local)
        session.beginBootstrap()
        assertThrows(IllegalStateException::class.java) { session.requireWritable(local) }
        session.completeBootstrap(); registry.block(local)
        assertThrows(IllegalStateException::class.java) { session.requireWritable(local) }
    }
}
