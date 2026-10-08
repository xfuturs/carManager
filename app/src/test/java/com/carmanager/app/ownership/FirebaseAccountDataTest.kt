package com.carmanager.app.ownership

import com.carmanager.app.core.data.repository.FirebaseAccountData
import com.carmanager.app.core.data.repository.DisabledSyncRepository
import com.carmanager.app.core.domain.session.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirebaseAccountDataTest {
    private val auth = mockk<FirebaseAuth>()
    private val firestore = mockk<FirebaseFirestore>()
    private val user = mockk<FirebaseUser>()
    private val root = mockk<DocumentReference>()
    private val diagnostics = mutableListOf<RemoteCleanupDiagnostic>()
    private val adapter = FirebaseAccountData { diagnostics += it }

    @BeforeEach fun setup() {
        // Le code Firebase construit une SparseArray Android ; les tests JVM gardent le vrai enum SDK.
        mockkConstructor(android.util.SparseArray::class)
        val codes = mutableMapOf<Int, Any>()
        every { anyConstructed<android.util.SparseArray<Any>>().get(any()) } answers { codes[firstArg<Int>()] }
        every { anyConstructed<android.util.SparseArray<Any>>().put(any(), any()) } answers {
            codes[firstArg<Int>()] = secondArg(); Unit
        }
        mockkStatic(android.text.TextUtils::class, FirebaseAuth::class, FirebaseFirestore::class)
        every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        every { FirebaseAuth.getInstance() } returns auth
        every { FirebaseFirestore.getInstance() } returns firestore
        every { auth.currentUser } returns user
        every { user.uid } returns "A"
        val users = mockk<CollectionReference>()
        every { firestore.collection("users") } returns users
        every { users.document("A") } returns root
    }
    @AfterEach fun cleanup() {
        unmockkStatic(android.text.TextUtils::class, FirebaseAuth::class, FirebaseFirestore::class)
        unmockkConstructor(android.util.SparseArray::class)
    }

    @Test fun `failed legacy cleanup read is surfaced without deleting parent`() = runTest {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        every { root.collection("vehicles") } returns collection
        every { collection.limit(400) } returns query
        val failure = IllegalStateException("permission denied")
        every { query.get(Source.SERVER) } returns Tasks.forException(failure)
        val caught = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull()
        assertInstanceOf(RemoteCleanupFailure::class.java, caught)
        assertEquals(failure, caught?.cause)
        assertEquals(RemoteCleanupFamily.LEGACY_VEHICLES, diagnostics.single().family)
        assertEquals(RemoteCleanupOperation.QUERY, diagnostics.single().operation)
        verify(exactly = 0) { root.delete() }
    }

    @Test fun `known collection deletion handles more than 500 documents and awaits batches`() = runTest {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val batch = mockk<WriteBatch>()
        every { root.collection(any()) } returns collection
        every { collection.limit(400) } returns query
        fun page(count: Int): QuerySnapshot {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.isEmpty } returns (count == 0)
            val document = mockk<DocumentSnapshot>()
            every { document.reference } returns mockk<DocumentReference>()
            every { snapshot.documents } returns List(count) { document }
            return snapshot
        }
        every { query.get(Source.SERVER) } returnsMany listOf(page(400), page(101), page(0), page(0), page(0)).map { Tasks.forResult(it) }
        every { firestore.batch() } returns batch
        every { batch.delete(any()) } returns batch
        every { batch.commit() } returns Tasks.forResult<Void>(null)
        every { root.delete() } returns Tasks.forResult<Void>(null)
        adapter.deleteKnownData("A")
        verify(exactly = 501) { batch.delete(any()) }
        verify(exactly = 2) { batch.commit() }
        verify(exactly = 3) { root.collection("vehicles") }
        verify(exactly = 1) { root.collection("fuel_records"); root.collection("maintenance_records"); root.delete() }
    }

    @Test fun `failed deletion batch does not delete parent or claim completion`() = runTest {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val page = mockk<QuerySnapshot>()
        val document = mockk<DocumentSnapshot>()
        val batch = mockk<WriteBatch>()
        every { root.collection("vehicles") } returns collection
        every { collection.limit(400) } returns query
        every { query.get(Source.SERVER) } returns Tasks.forResult(page)
        every { page.isEmpty } returns false
        every { page.documents } returns listOf(document)
        every { document.reference } returns mockk<DocumentReference>()
        every { firestore.batch() } returns batch
        every { batch.delete(any()) } returns batch
        val failure = IllegalStateException("offline")
        every { batch.commit() } returns Tasks.forException(failure)
        val caught = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull()
        assertInstanceOf(RemoteCleanupFailure::class.java, caught)
        assertEquals(failure, caught?.cause)
        assertEquals(RemoteCleanupOperation.BATCH_DELETE, diagnostics.single().operation)
        verify(exactly = 0) { root.delete() }
    }

    private fun empty(): QuerySnapshot = mockk { every { isEmpty } returns true }
    private fun queries(vararg tasks: com.google.android.gms.tasks.Task<QuerySnapshot>): Query {
        val collection = mockk<CollectionReference>(); val query = mockk<Query>()
        every { root.collection(any()) } returns collection
        every { collection.limit(400) } returns query
        every { query.get(Source.SERVER) } returnsMany tasks.toList()
        every { root.delete() } returns Tasks.forResult<Void>(null)
        return query
    }
    private fun denied() = FirebaseFirestoreException("UID-sensitive@example.test secret-token document contents",
        FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test fun `empty historical collections delete only the own parent and produce no failure`() = runTest {
        queries(Tasks.forResult(empty()), Tasks.forResult(empty()), Tasks.forResult(empty()))
        adapter.deleteKnownData("A")
        verifyOrder { root.collection("vehicles"); root.collection("fuel_records"); root.collection("maintenance_records"); root.delete() }
        verify(exactly = 0) { firestore.batch() }
        assertTrue(diagnostics.isEmpty())
    }

    @Test fun `PERMISSION_DENIED on vehicles SERVER query identifies exact family without treating it as empty`() = runTest {
        queries(Tasks.forException(denied()))
        val caught = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals(RemoteCleanupDiagnostic(RemoteCleanupFamily.LEGACY_VEHICLES, RemoteCleanupOperation.QUERY, "PERMISSION_DENIED"), caught.diagnostic)
        assertEquals("users/{uid}/vehicles", caught.diagnostic.family.pathFamily)
        assertFalse((caught.message + diagnostics.single().safeDescription).contains("secret-token"))
        assertFalse((caught.message + diagnostics.single().safeDescription).contains("sensitive@example"))
        verify(exactly = 0) { root.delete(); firestore.batch() }
    }

    @Test fun `fuel query denied after vehicles identifies later family and retry accepts previously deleted data`() = runTest {
        val document = mockk<DocumentSnapshot> { every { reference } returns mockk() }
        val page = mockk<QuerySnapshot> { every { isEmpty } returns false; every { documents } returns listOf(document) }
        queries(Tasks.forResult(page), Tasks.forResult(empty()), Tasks.forException(denied()))
        val batch = mockk<WriteBatch>()
        every { firestore.batch() } returns batch; every { batch.delete(any()) } returns batch
        every { batch.commit() } returns Tasks.forResult<Void>(null)
        val failure = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals(RemoteCleanupFamily.LEGACY_FUEL, failure.diagnostic.family)
        assertEquals(RemoteCleanupOperation.QUERY, failure.diagnostic.operation)
        verify(exactly = 1) { batch.commit() }; verify(exactly = 0) { root.delete() }
        queries(Tasks.forResult(empty()), Tasks.forResult(empty()), Tasks.forResult(empty()))
        adapter.deleteKnownData("A"); verify(exactly = 1) { root.delete() }
        verify(exactly = 1) { batch.commit() }
    }

    @Test fun `maintenance query denied cannot be misreported as vehicles or a missing collection`() = runTest {
        queries(Tasks.forResult(empty()), Tasks.forResult(empty()), Tasks.forException(denied()))
        val failure = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals(RemoteCleanupFamily.LEGACY_MAINTENANCE, failure.diagnostic.family)
        assertEquals(RemoteCleanupOperation.QUERY, failure.diagnostic.operation)
        verify(exactly = 0) { root.delete() }
    }

    @Test fun `user parent delete denied is distinct from collection query and preserves Firebase code`() = runTest {
        queries(Tasks.forResult(empty()), Tasks.forResult(empty()), Tasks.forResult(empty()))
        every { root.delete() } returns Tasks.forException(denied())
        val failure = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals(RemoteCleanupDiagnostic(RemoteCleanupFamily.USER_DOCUMENT, RemoteCleanupOperation.DELETE_DOCUMENT, "PERMISSION_DENIED"), failure.diagnostic)
        assertFalse(failure.message!!.contains("users/"))
    }

    @Test fun `pending writes denial has its own operation and no invented collection path`() = runTest {
        every { firestore.waitForPendingWrites() } returns Tasks.forException(denied())
        val failure = runCatching { adapter.drainWrites("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals(RemoteCleanupFamily.PENDING_WRITES, failure.diagnostic.family)
        assertEquals(RemoteCleanupOperation.DRAIN_WRITES, failure.diagnostic.operation)
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test fun `UID mismatch stops before querying a foreign user namespace`() = runTest {
        val users = mockk<CollectionReference>()
        val foreignRoot = mockk<DocumentReference>()
        every { firestore.collection("users") } returns users
        every { users.document("B") } returns foreignRoot
        assertInstanceOf(IllegalStateException::class.java, runCatching { adapter.deleteKnownData("B") }.exceptionOrNull())
        verify(exactly = 0) { foreignRoot.collection(any()); foreignRoot.delete(); firestore.batch() }
        assertTrue(diagnostics.isEmpty())
    }

    @Test fun `auth change after query prevents batch commit and never deletes another namespace`() = runTest {
        val page = mockk<QuerySnapshot> { every { isEmpty } returns false }
        val query = queries(Tasks.forResult(page))
        every { query.get(Source.SERVER) } answers { every { user.uid } returns "B"; Tasks.forResult(page) }
        assertInstanceOf(IllegalStateException::class.java, runCatching { adapter.deleteKnownData("A") }.exceptionOrNull())
        verify(exactly = 0) { firestore.batch(); root.delete() }
    }

    @Test fun `logger failure cannot swallow PERMISSION_DENIED or advance cleanup`() = runTest {
        queries(Tasks.forException(denied()))
        val failingLogger = FirebaseAccountData { error("logger unavailable") }
        val failure = runCatching { failingLogger.deleteKnownData("A") }.exceptionOrNull() as RemoteCleanupFailure
        assertEquals("PERMISSION_DENIED", failure.diagnostic.code)
        assertInstanceOf(FirebaseFirestoreException::class.java, failure.cause)
        verify(exactly = 0) { root.delete() }
    }

    @Test fun `cancellation remains cancellation without failure diagnostics`() = runTest {
        val collection = mockk<CollectionReference>(); val query = mockk<Query>()
        every { root.collection("vehicles") } returns collection; every { collection.limit(400) } returns query
        every { query.get(Source.SERVER) } throws CancellationException("cancelled")
        assertInstanceOf(CancellationException::class.java, runCatching { adapter.deleteKnownData("A") }.exceptionOrNull())
        assertTrue(diagnostics.isEmpty()); verify(exactly = 0) { root.delete() }
    }

    @Test fun `real cleanup adapter denies cloud step then same UID retry deletes Auth last with no local purge`() = runTest {
        val identity = AuthSession().apply { setUid("A") }; val registry = TestDeletionRegistry()
        val garage = WorkspaceSession(registry).apply { completeBootstrap() }
        val local = mockk<LocalAccountData>()
        val deletion = AccountDeletion(identity, registry, DisabledSyncRepository(), local, adapter)
        every { firestore.waitForPendingWrites() } returns Tasks.forResult<Void>(null)
        queries(Tasks.forException(denied()))
        assertTrue(deletion.delete("firebase:A").isFailure)
        assertEquals(DeletionStage.REMOTE, deletion.state.value.stage)
        assertTrue(deletion.state.value.error!!.contains("PERMISSION_DENIED"))
        assertTrue(deletion.state.value.error!!.contains("garage local reste conservé"))
        verify(exactly = 0) { user.delete(); root.delete() }; coVerify(exactly = 0) { local.purge(any()) }
        identity.setUid("B"); assertTrue(deletion.delete("firebase:A").isFailure)
        identity.setUid("A")
        queries(Tasks.forResult(empty()), Tasks.forResult(empty()), Tasks.forResult(empty()))
        every { user.delete() } answers { identity.setUid(null); every { auth.currentUser } returns null; Tasks.forResult<Void>(null) }
        assertTrue(deletion.delete("firebase:A").isSuccess)
        verifyOrder { root.delete(); user.delete() }
        assertNull(identity.uid.value); assertEquals("local:device", garage.owner.value)
        garage.requireWritable("local:device"); coVerify(exactly = 0) { local.purge(any()) }
    }
}
