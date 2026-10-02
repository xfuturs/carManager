package com.carmanager.app.ownership

import android.content.Context
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.data.repository.*
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.core.util.NotificationHelper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class AuthRepositoryImplTest {
    private val auth = mockk<FirebaseAuth>()
    private val listener = slot<FirebaseAuth.AuthStateListener>()
    private var activeUser: FirebaseUser? = null
    private val session = WorkspaceSession(TestDeletionRegistry())
    private val deletion = mockk<AccountDeletion>()

    @BeforeEach fun setup() {
        mockkStatic(FirebaseAuth::class, GoogleAuthProvider::class, FirebaseFirestore::class)
        mockkObject(NotificationHelper)
        every { FirebaseAuth.getInstance() } returns auth
        every { FirebaseFirestore.getInstance() } throws AssertionError("Normal auth initialized Firestore")
        every { auth.currentUser } answers { activeUser }
        every { auth.addAuthStateListener(capture(listener)) } answers { listener.captured.onAuthStateChanged(auth) }
        every { NotificationHelper.clearInactiveNotifications(any(), any()) } just Runs
        every { auth.signOut() } answers { activeUser = null; listener.captured.onAuthStateChanged(auth) }
        every { GoogleAuthProvider.getCredential(any(), null) } returns mockk<AuthCredential>()
    }

    @AfterEach fun cleanup() {
        try { verify(exactly = 0) { FirebaseFirestore.getInstance() } }
        finally {
            unmockkStatic(FirebaseAuth::class, GoogleAuthProvider::class, FirebaseFirestore::class)
            unmockkObject(NotificationHelper)
        }
    }

    private fun user(uid: String, email: String) = mockk<FirebaseUser> {
        every { this@mockk.uid } returns uid
        every { this@mockk.email } returns email
    }
    private fun repository() = AuthRepositoryImpl(session, DisabledSyncRepository(), deletion, mockk<Context>())
    private fun nextGoogleUser(uid: String, email: String) {
        every { auth.signInWithCredential(any()) } answers {
            activeUser = user(uid, email)
            listener.captured.onAuthStateChanged(auth)
            Tasks.forResult(mockk<AuthResult>())
        }
    }

    @Test fun `Google non Gmail A B guest transitions preserve local rows and same UID access`() = runTest {
        val rows = listOf(testVehicle("guest:local", 1), testVehicle("firebase:A", 2), testVehicle("firebase:B", 3))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val repository = repository()
        assertEquals("guest:local", session.owner.value)
        assertEquals(1L, vehicles.observeAll().first().single().id)
        for ((uid, id) in listOf("A" to 2L, "B" to 3L, "A" to 2L)) {
            nextGoogleUser(uid, "driver@company.example")
            assertTrue(repository.signInWithGoogle("google-token").isSuccess)
            assertEquals("firebase:$uid", session.owner.value)
            assertEquals(uid, repository.currentUser.value?.id)
            assertEquals("driver@company.example", repository.currentUser.value?.email)
            assertEquals(id, vehicles.observeAll().first().single().id)
            repository.signOut()
            assertNull(repository.currentUser.value)
            assertEquals(1L, vehicles.observeAll().first().single().id)
        }
        coVerify(exactly = 0) { deletion.delete(any()) }
        verify { dao.observeAll("guest:local"); dao.observeAll("firebase:A"); dao.observeAll("firebase:B") }
        confirmVerified(dao)
    }

    @Test fun `persisted password session keeps UID until explicit signout without deletion or reassignment`() = runTest {
        activeUser = user("legacy", "legacy@company.example")
        val passwordProvider = mockk<UserInfo> { every { providerId } returns "password" }
        every { activeUser!!.providerData } returns listOf(passwordProvider)
        val rows = listOf(testVehicle("guest:local", 1), testVehicle("firebase:legacy", 2))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val repository = repository()
        assertEquals("firebase:legacy", session.owner.value)
        assertEquals("legacy", repository.currentUser.value?.id)
        assertEquals(2L, vehicles.observeAll().first().single().id)
        repository.signOut()
        assertEquals("guest:local", session.owner.value)
        assertEquals(1L, vehicles.observeAll().first().single().id)
        verify { dao.observeAll("firebase:legacy"); dao.observeAll("guest:local") }
        confirmVerified(dao)
        coVerify(exactly = 0) { deletion.delete(any()) }
        verify(exactly = 0) { auth.signInWithCredential(any()) }
    }

    @Test fun `only explicit account deletion delegates to staged cleanup and propagates failure`() = runTest {
        activeUser = user("A", "driver@company.example")
        val repository = repository()
        val failure = IllegalStateException("legacy cleanup refused")
        coEvery { deletion.delete("firebase:A") } returns Result.failure(failure)
        coVerify(exactly = 0) { deletion.delete(any()) }
        assertEquals(failure, repository.deleteAccount().exceptionOrNull())
        coVerify(exactly = 1) { deletion.delete("firebase:A") }
    }

    @Test fun `public production auth contract no longer contains manual authentication`() {
        for (type in listOf(AuthRepository::class.java, AuthRepositoryImpl::class.java)) {
            // Result est une value class : le compilateur suffixe les noms JVM.
            val names = type.methods.map { it.name.substringBefore('-') }
            assertFalse(names.any { it == "signIn" || it == "signUp" })
            assertTrue("signInWithGoogle" in names)
        }
    }
}
