package com.carmanager.app.a15_1

import com.carmanager.app.core.data.local.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.ownership.TestDeletionRegistry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VehicleFileDeletionTest {
    @TempDir lateinit var root: File
    private val registry = TestDeletionRegistry()
    private val session = WorkspaceSession(registry).apply { completeBootstrap() }
    private val vehicle = GarageFixture().vehicle()
    private val directory get() = File(root,"vehicle_documents").apply { mkdirs() }
    private val journalFile get() = File(root,"cleanup-journal")
    private val files = mutableListOf<IndexedVehicleFile>()
    private val protected = mutableListOf<String>()
    private var vehiclePresent = true
    private var failDB = false
    private var failFile: String? = null
    private var beforeCommit: suspend () -> Unit = {}
    private var beforeSave: () -> Unit = {}
    private val journal get() = object : VehicleCleanupJournal {
        override fun entries() = if (!journalFile.exists()) emptyList() else journalFile.readLines().filter { it.isNotEmpty() }.map(VehicleCleanupCodec::decodeEntry)
        override fun save(entries: List<VehicleCleanupEntry>) { beforeSave(); journalFile.writeText(entries.joinToString("\n", postfix="\n", transform=VehicleCleanupCodec::encodeEntry)) }
    }
    private fun file(name: String): File = File(directory,name).apply {
        writeText("private $name"); files += IndexedVehicleFile(files.size.toLong()+1,path)
    }
    private fun service() = VehicleFileDeletion(session,directory,journal,
        { session.requireWritable(it.ownerKey); check(vehiclePresent); files.toList() },
        { id -> protected.toList() + if (id == null && vehiclePresent) files.map { it.path } else emptyList() },
        { _, _ -> vehiclePresent },
        { expected, captured ->
            session.requireWritable(expected.ownerKey); assertEquals(files.toSet(),captured.toSet())
            beforeCommit(); if (failDB) throw IOException("database")
            session.requireWritable(expected.ownerKey); files.clear(); vehiclePresent = false
        }, { file -> if (file.name == failFile) false else !file.exists() || file.delete() })
    private suspend fun failure(type: Class<out Throwable> = IllegalStateException::class.java) {
        try { service().delete(vehicle); fail<Unit>("failure expected") }
        catch (error: Throwable) { assertTrue(type.isInstance(error),error.toString()) }
    }
    @Test fun `owned vehicle deletes multiple indexed files including reports and cascaded metadata`() = runTest {
        val a=file("photo.jpg");val b=file("Rapport.pdf");service().delete(vehicle)
        assertFalse(a.exists());assertFalse(b.exists());assertFalse(vehiclePresent);assertTrue(files.isEmpty());assertTrue(journal.entries().isEmpty())
    }
    @Test fun `external exported copy and unknown historical files remain intact`() = runTest {
        val owned=file("report.pdf");val external=File(root,"export.pdf").apply { writeText("external") }
        val unknown=File(directory,"historical.pdf").apply { writeText("unknown") };service().delete(vehicle)
        assertFalse(owned.exists());assertEquals("external",external.readText());assertEquals("unknown",unknown.readText())
    }
    @Test fun `other vehicle file remains intact`() = runTest {
        file("owned");val other=File(directory,"other").apply { writeText("other vehicle") };protected += other.path
        service().delete(vehicle);assertEquals("other vehicle",other.readText())
    }
    @Test fun `shared foreign owner file blocks deletion before cascade`() = runTest {
        val owned=file("shared");protected += owned.path;failure()
        assertTrue(owned.exists());assertTrue(vehiclePresent);assertFalse(journalFile.exists())
    }
    @Test fun `shared same owner unrelated vehicle file also blocks deletion`() = runTest {
        val owned=file("shared-with-other-vehicle");protected += owned.canonicalPath;failure()
        assertTrue(owned.exists());assertTrue(vehiclePresent)
    }
    @Test fun `all paths validated before any file is deleted`() = runTest {
        val valid=file("valid");val outside=File(root,"outside").apply { writeText("keep") }
        files += IndexedVehicleFile(2,outside.path);failure()
        assertTrue(valid.exists());assertTrue(outside.exists());assertTrue(vehiclePresent)
    }
    @Test fun `canonical parent escape is rejected`() = runTest {
        val outside=File(root,"outside").apply { writeText("keep") }
        files += IndexedVehicleFile(1,File(directory,"../outside").path);failure()
        assertEquals("keep",outside.readText());assertTrue(vehiclePresent)
    }
    @Test fun `DB failure preserves vehicle metadata and bytes`() = runTest {
        val owned=file("owned");failDB=true;failure(IOException::class.java)
        assertTrue(owned.exists());assertTrue(vehiclePresent);assertEquals(1,files.size);assertTrue(journal.entries().isEmpty())
    }
    @Test fun `cleanup failure is a committed warning and survives restart for retry`() = runTest {
        val owned=file("retry");failFile="retry";failure(VehicleCleanupPendingException::class.java)
        assertFalse(vehiclePresent);assertTrue(files.isEmpty());assertTrue(owned.exists());assertEquals(1,journal.entries().size)
        failFile=null;service().retryPending();assertFalse(owned.exists());assertTrue(journal.entries().isEmpty())
    }
    @Test fun `one failed file does not prevent other successful cleanup and retry is idempotent`() = runTest {
        val good=file("good");val bad=file("bad");failFile="bad";failure(VehicleCleanupPendingException::class.java)
        assertFalse(good.exists());assertTrue(bad.exists());failFile=null
        service().retryPending();service().retryPending();assertFalse(bad.exists());assertTrue(journal.entries().isEmpty())
    }
    @Test fun `restart before SQL commit discards intent without touching existing vehicle files`() = runTest {
        val owned=file("still-owned")
        journal.save(listOf(VehicleCleanupEntry("token",vehicle.ownerKey,vehicle.id,listOf(owned.path))))
        service().retryPending();assertTrue(owned.exists());assertTrue(vehiclePresent);assertTrue(journal.entries().isEmpty())
    }
    @Test fun `legacy journal owner cannot make a consolidated living vehicle appear deleted`() = runTest {
        val owned=file("consolidated")
        journal.save(listOf(VehicleCleanupEntry("old-token","firebase:old",vehicle.id,listOf(owned.path))))
        service().retryPending(); assertTrue(owned.exists()); assertTrue(vehiclePresent); assertTrue(journal.entries().isEmpty())
    }
    @Test fun `legacy journal after committed deletion retains reference protection and idempotence`() = runTest {
        val owned=file("legacy-pending"); vehiclePresent=false
        journal.save(listOf(VehicleCleanupEntry("old-token","guest:local",vehicle.id,listOf(owned.path))))
        protected+=owned.path; service().retryPending(); assertTrue(owned.exists()); assertEquals(1,journal.entries().size)
        protected.clear(); service().retryPending(); service().retryPending(); assertFalse(owned.exists()); assertTrue(journal.entries().isEmpty())
    }
    @Test fun `new reference after cascade is protected by retry check`() = runTest {
        val owned=file("new-reference");failFile=owned.name;failure(VehicleCleanupPendingException::class.java)
        failFile=null;protected += owned.path;service().retryPending()
        assertTrue(owned.exists());assertEquals(1,journal.entries().size)
    }
    @Test fun `garage unavailable after intent blocks DB delete and preserves files`() = runTest {
        val owned=file("owned");beforeSave={ session.beginBootstrap() };failure()
        assertTrue(vehiclePresent);assertTrue(owned.exists())
    }
    @Test fun `blocked owner cannot begin deletion`() = runTest {
        val owned=file("owned");registry.block(vehicle.ownerKey);failure()
        assertTrue(vehiclePresent);assertTrue(owned.exists());assertFalse(journalFile.exists())
    }
    @Test fun `cancellation before SQL commit propagates without deleting owned bytes`() = runTest {
        val owned=file("owned");beforeCommit={ throw CancellationException() };failure(CancellationException::class.java)
        assertTrue(vehiclePresent);assertTrue(owned.exists());assertTrue(journal.entries().isEmpty())
    }
}
