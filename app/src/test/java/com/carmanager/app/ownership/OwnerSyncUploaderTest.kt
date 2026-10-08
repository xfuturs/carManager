package com.carmanager.app.ownership

import com.carmanager.app.core.data.local.entity.FuelRecordEntity
import com.carmanager.app.core.data.repository.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OwnerSyncUploaderTest {
    private val registry = TestDeletionRegistry()
    private var capturedOwner = "guest:local"
    private val session = mockk<WorkspaceSession>().also { target ->
        every { target.requireWritable(any()) } answers {
            check(firstArg<String>() == capturedOwner && capturedOwner !in registry.blockedOwners.value)
        }
    }
    private val writer = mockk<SyncRemoteWriter>()
    private val uploader = OwnerSyncUploader(session, writer)
    private fun snapshot(owner: String) = OwnerSyncSnapshot(listOf(testVehicle(owner)), emptyList(), emptyList())

    @Test fun `guest never writes remotely`() = runTest {
        uploader.upload("guest:local", snapshot("guest:local"))
        coVerify(exactly = 0) { writer.write(any(), any(), any(), any()) }
    }

    @Test fun `foreign vehicles and children reject entire snapshot before upload`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        for (foreign in listOf("guest:local", "firebase:B")) {
            assertTrue(runCatching { uploader.upload("firebase:A", snapshot(foreign)) }.isFailure)
        }
        val child = FuelRecordEntity(vehicleId = 99, date = 0, mileage = 100, liters = 10.0, totalPrice = 20.0)
        assertTrue(runCatching { uploader.upload("firebase:A", snapshot("firebase:A").copy(fuel = listOf(child))) }.isFailure)
        coVerify(exactly = 0) { writer.write(any(), any(), any(), any()) }
    }

    @Test fun `owned data awaits writer and uses captured UID`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        val gate = CompletableDeferred<Unit>()
        coEvery { writer.write("A", "vehicles", "1", any()) } coAnswers { gate.await() }
        val upload = async { uploader.upload("firebase:A", snapshot("firebase:A")) }
        yield()
        assertFalse(upload.isCompleted)
        gate.complete(Unit)
        upload.await()
        coVerify(exactly = 1) { writer.write("A", "vehicles", "1", any()) }
    }

    @Test fun `remote failure propagates instead of successful upload`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        val failure = IllegalStateException("Firestore refused")
        coEvery { writer.write(any(), any(), any(), any()) } throws failure
        assertSame(failure, runCatching { uploader.upload("firebase:A", snapshot("firebase:A")) }.exceptionOrNull())
    }

    @Test fun `account change between awaited writes stops subsequent uploads`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        coEvery { writer.write(any(), any(), any(), any()) } coAnswers { run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("B") } }
        val input = snapshot("firebase:A").copy(vehicles = listOf(testVehicle("firebase:A", 1), testVehicle("firebase:A", 2)))
        assertTrue(runCatching { uploader.upload("firebase:A", input) }.isFailure)
        coVerify(exactly = 1) { writer.write("A", "vehicles", "1", any()) }
        coVerify(exactly = 0) { writer.write("B", any(), any(), any()) }
        coVerify(exactly = 0) { writer.write(any(), any(), "2", any()) }
    }

    @Test fun `interrupted deletion blocks future uploads after reauthentication`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        registry.block("firebase:A")
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid(null) }
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        assertTrue(runCatching { uploader.upload("firebase:A", snapshot("firebase:A")) }.isFailure)
        coVerify(exactly = 0) { writer.write(any(), any(), any(), any()) }
    }

    @Test fun `cancelled job cannot initiate next write even when writer returns immediately`() = runTest {
        run { capturedOwner = com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid("A") }
        coEvery { writer.write(any(), any(), any(), any()) } coAnswers {
            currentCoroutineContext().cancel()
        }
        val input = snapshot("firebase:A").copy(vehicles = listOf(testVehicle("firebase:A", 1), testVehicle("firebase:A", 2)))
        val upload = async { uploader.upload("firebase:A", input) }
        assertTrue(runCatching { upload.await() }.exceptionOrNull() is CancellationException)
        coVerify(exactly = 1) { writer.write("A", "vehicles", "1", any()) }
        coVerify(exactly = 0) { writer.write(any(), any(), "2", any()) }
    }
}
