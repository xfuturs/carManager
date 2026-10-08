package com.carmanager.app.ownership

import com.carmanager.app.core.domain.repository.SyncRepository
import com.carmanager.app.core.domain.session.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountDeletionTest {
    private val registry = TestDeletionRegistry()
    private val session = AuthSession().apply { setUid("A") }
    private val sync = mockk<SyncRepository>(relaxed = true)
    private val remote = mockk<AccountRemoteData>(relaxed = true)
    private val local = mockk<LocalAccountData>(relaxed = true)
    private val deletion = AccountDeletion(session, registry, sync, local, remote)

    @Test fun `A deletion stops sync then deletes remote auth while preserving every local row`() = runTest {
        val rows = mutableSetOf("guest:local", "firebase:A", "firebase:B")
        coEvery { local.purge(any()) } coAnswers { rows.remove(firstArg()); Unit }
        assertTrue(deletion.delete("firebase:A").isSuccess)
        coVerifyOrder { sync.stopSync(); remote.drainWrites("A"); remote.deleteKnownData("A"); remote.deleteAuth("A") }
        coVerify(exactly = 0) { local.purge(any()) }
        assertEquals(setOf("guest:local", "firebase:A", "firebase:B"), rows)
        assertEquals(setOf("firebase:A"), registry.blockedOwners.value)
        assertEquals(DeletionStage.COMPLETE, deletion.state.value.stage)
        assertFalse(deletion.state.value.running)
    }

    @Test fun `remote failure never purges local data or claims success`() = runTest {
        coEvery { remote.deleteKnownData("A") } throws IllegalStateException("network")
        assertTrue(deletion.delete("firebase:A").isFailure)
        coVerify(exactly = 0) { local.purge(any()); remote.deleteAuth(any()) }
        assertEquals(DeletionStage.REMOTE, deletion.state.value.stage)
        assertNotNull(deletion.state.value.error)
        assertTrue("firebase:A" in registry.blockedOwners.value)
    }

    @Test fun `pending writes failure prevents all destructive phases`() = runTest {
        coEvery { remote.drainWrites("A") } throws IllegalStateException("offline")
        assertTrue(deletion.delete("firebase:A").isFailure)
        coVerify(exactly = 0) { remote.deleteKnownData(any()); local.purge(any()); remote.deleteAuth(any()) }
    }

    @Test fun `legacy local purge is never resumed even if its port would fail`() = runTest {
        coEvery { local.purge("firebase:A") } throws IllegalStateException("file deletion refused")
        assertTrue(deletion.delete("firebase:A").isSuccess)
        assertEquals(DeletionStage.COMPLETE, deletion.state.value.stage)
        coVerify(exactly = 0) { local.purge(any()) }
        coVerify(exactly = 1) { remote.deleteAuth("A") }
    }

    @Test fun `auth recent login failure remains explicit partial failure and permits manual retry`() = runTest {
        coEvery { remote.deleteAuth("A") } throws IllegalStateException("Reconnectez-vous au même compte")
        assertTrue(deletion.delete("firebase:A").isFailure)
        assertEquals(DeletionStage.AUTH, deletion.state.value.stage)
        assertTrue(deletion.state.value.error!!.contains("Reconnectez-vous"))
        coEvery { remote.deleteAuth("A") } returns Unit
        assertTrue(deletion.delete("firebase:A").isSuccess)
        assertTrue("firebase:A" in registry.blockedOwners.value)
    }

    @Test fun `guest and another owner cannot be deletion targets`() = runTest {
        for (owner in listOf("guest:local", "firebase:B")) assertTrue(deletion.delete(owner).isFailure)
        coVerify(exactly = 0) { sync.stopSync(); remote.deleteKnownData(any()); local.purge(any()); remote.deleteAuth(any()) }
        assertTrue(registry.blockedOwners.value.isEmpty())
    }
}
