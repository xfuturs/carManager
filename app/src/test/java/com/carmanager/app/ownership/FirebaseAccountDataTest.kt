package com.carmanager.app.ownership

import com.carmanager.app.core.data.repository.FirebaseAccountData
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirebaseAccountDataTest {
    private val auth = mockk<FirebaseAuth>()
    private val firestore = mockk<FirebaseFirestore>()
    private val user = mockk<FirebaseUser>()
    private val root = mockk<DocumentReference>()
    private val adapter = FirebaseAccountData()

    @BeforeEach fun setup() {
        mockkStatic(FirebaseAuth::class, FirebaseFirestore::class)
        every { FirebaseAuth.getInstance() } returns auth
        every { FirebaseFirestore.getInstance() } returns firestore
        every { auth.currentUser } returns user
        every { user.uid } returns "A"
        val users = mockk<CollectionReference>()
        every { firestore.collection("users") } returns users
        every { users.document("A") } returns root
    }
    @AfterEach fun cleanup() { unmockkStatic(FirebaseAuth::class, FirebaseFirestore::class) }

    @Test fun `failed legacy cleanup read is surfaced without deleting parent`() = runTest {
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        every { root.collection("vehicles") } returns collection
        every { collection.limit(400) } returns query
        val failure = IllegalStateException("permission denied")
        every { query.get(Source.SERVER) } returns Tasks.forException(failure)
        val caught = runCatching { adapter.deleteKnownData("A") }.exceptionOrNull()
        assertInstanceOf(IllegalStateException::class.java, caught)
        assertEquals(failure.message, caught?.message)
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
        assertInstanceOf(IllegalStateException::class.java, caught)
        assertEquals(failure.message, caught?.message)
        verify(exactly = 0) { root.delete() }
    }
}
