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
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
    private val identity = AuthSession()
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
    private fun repository(sync: com.carmanager.app.core.domain.repository.SyncRepository = DisabledSyncRepository()) =
        AuthRepositoryImpl(identity, sync, deletion, mockk<Context>())
    private fun nextGoogleUser(uid: String, email: String) {
        every { auth.signInWithCredential(any()) } answers {
            activeUser = user(uid, email)
            listener.captured.onAuthStateChanged(auth)
            Tasks.forResult(mockk<AuthResult>())
        }
    }

    @Test fun `Google non Gmail A B guest transitions preserve canonical rows and separate auth UID`() = runTest {
        val rows = listOf(testVehicle("local:device", 1), testVehicle("local:device", 2), testVehicle("local:device", 3))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val repository = repository()
        assertEquals("local:device", session.owner.value)
        assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        for (uid in listOf("A", "B", "A")) {
            nextGoogleUser(uid, "driver@company.example")
            assertTrue(repository.signInWithGoogle("google-token").isSuccess)
            assertEquals("local:device", session.owner.value)
            assertEquals(uid, repository.currentUser.value?.id)
            assertEquals("driver@company.example", repository.currentUser.value?.email)
            assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
            repository.signOut()
            assertNull(repository.currentUser.value)
            assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        }
        coVerify(exactly = 0) { deletion.delete(any()) }
        verify { dao.observeAll("local:device") }
        confirmVerified(dao)
    }

    @Test fun `persisted password session keeps UID until explicit signout without deletion or reassignment`() = runTest {
        activeUser = user("legacy", "legacy@company.example")
        val passwordProvider = mockk<UserInfo> { every { providerId } returns "password" }
        every { activeUser!!.providerData } returns listOf(passwordProvider)
        val rows = listOf(testVehicle("local:device", 1), testVehicle("local:device", 2))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll(any()) } answers { flowOf(rows.filter { it.ownerKey == firstArg<String>() }) }
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val repository = repository()
        assertEquals("local:device", session.owner.value)
        assertEquals("legacy", repository.currentUser.value?.id)
        assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        repository.signOut()
        assertEquals("local:device", session.owner.value)
        assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        verify { dao.observeAll("local:device") }
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

    @Test fun `failed Google login preserves the ready local garage and guest identity`() = runTest {
        val rows = listOf(testVehicle("local:device", 1), testVehicle("local:device", 2))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll("local:device") } returns flowOf(rows)
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val repository = repository()
        val failure = IllegalStateException("Google indisponible")
        every { auth.signInWithCredential(any()) } returns Tasks.forException(failure)
        assertEquals(failure, repository.signInWithGoogle("google-token").exceptionOrNull())
        assertNull(repository.currentUser.value); assertNull(identity.uid.value)
        assertEquals(GarageReadiness.Ready, session.readiness.value)
        assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        coVerify(exactly = 0) { deletion.delete(any()) }
    }

    @Test fun `failed logout preserves Google identity and the ready local garage`() = runTest {
        activeUser = user("A", "driver@company.example")
        val rows = listOf(testVehicle("local:device", 1), testVehicle("local:device", 2))
        val dao = mockk<VehicleDao>()
        every { dao.observeAll("local:device") } returns flowOf(rows)
        val vehicles = VehicleRepositoryImpl(dao, session, mockk<OwnedDatabaseAccess>(), mockk())
        val sync = mockk<com.carmanager.app.core.domain.repository.SyncRepository>()
        val failure = IllegalStateException("Arrêt indisponible")
        coEvery { sync.stopSync() } throws failure
        val repository = repository(sync)
        val actual = runCatching { repository.signOut() }.exceptionOrNull()
        assertEquals(failure.javaClass, actual?.javaClass); assertEquals(failure.message, actual?.message)
        assertEquals("A", repository.currentUser.value?.id); assertEquals("A", identity.uid.value)
        assertEquals(GarageReadiness.Ready, session.readiness.value)
        assertEquals(rows.map { it.id }, vehicles.observeAll().first().map { it.id })
        verify(exactly = 0) { auth.signOut() }
        coVerify(exactly = 0) { deletion.delete(any()) }
    }
}
