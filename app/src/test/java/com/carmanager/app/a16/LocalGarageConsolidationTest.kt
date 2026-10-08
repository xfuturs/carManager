package com.carmanager.app.a16

import com.carmanager.app.core.data.local.*
import com.carmanager.app.core.data.local.dao.*
import com.carmanager.app.core.data.local.entity.*
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.ReminderKey
import com.carmanager.app.ownership.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class LocalGarageConsolidationTest {
    @TempDir lateinit var dir: File
    private class Fixture(owners: List<String>) {
        val rows = owners.mapIndexed { i, owner -> testVehicle(owner, i + 1L).copy(
            remoteId = "remote-$i", syncStatus = "LEGACY", createdAt = 20, updatedAt = 30) }.toMutableList()
        val children = rows.map { MaintenanceRecordEntity(it.id, it.id, MaintenanceTypeEntity.OIL_CHANGE,
            date = 50, mileage = 123, cost = 42.5, nextDueDate = 1000) }
        val vehicles = mockk<VehicleDao>()
        val maintenance = mockk<MaintenanceDao>()
        var keys = emptySet<ReminderKey>()
        var failTransaction = false
        var failRegistry = false
        var leaveLegacy = false
        var updates = 0
        var inside = false
        val work = GarageOwnershipConsolidation(vehicles, maintenance, { next ->
            assertFalse(inside); if (failRegistry) error("registry unavailable"); keys += next
        }, { action ->
            val before = rows.toList(); inside = true
            try { action(); if (failTransaction) error("commit refused") }
            catch (error: Throwable) { rows.clear(); rows.addAll(before); throw error }
            finally { inside = false }
        })
        init {
            coEvery { vehicles.legacyOwners(LocalGarageOwner.KEY) } coAnswers {
                rows.filter { it.ownerKey != LocalGarageOwner.KEY }.map { it.ownerKey }.distinct()
            }
            coEvery { maintenance.getAll(any()) } coAnswers {
                val owner = firstArg<String>(); children.filter { child -> rows.any { it.id == child.vehicleId && it.ownerKey == owner } }
            }
            coEvery { vehicles.consolidateOwners(LocalGarageOwner.KEY) } coAnswers {
                assertTrue(inside); updates++
                val count = rows.count { it.ownerKey != LocalGarageOwner.KEY }
                if (!leaveLegacy) rows.replaceAll { it.copy(ownerKey = LocalGarageOwner.KEY) }
                count
            }
        }
    }
    private suspend fun preserves(owners: List<String>) {
        val f = Fixture(owners); val before = f.rows.toList(); val children = f.children.toList()
        f.work.run()
        assertEquals(before.map { it.copy(ownerKey = LocalGarageOwner.KEY) }, f.rows)
        assertEquals(children, f.children); assertEquals(before.map { it.id }, f.rows.map { it.id })
        coVerify(exactly = 0) { f.vehicles.insert(any()); f.vehicles.delete(any()); f.maintenance.insert(any()) }
    }
    @Test fun `guest only preserves every vehicle value`() = runTest { preserves(listOf("guest:local")) }
    @Test fun `firebase only preserves every vehicle value`() = runTest { preserves(listOf("firebase:A")) }
    @Test fun `guest and firebase form a union`() = runTest { preserves(listOf("guest:local", "firebase:A")) }
    @Test fun `multiple historical identities form a union`() = runTest { preserves(listOf("guest:local", "firebase:A", "firebase:B")) }
    @Test fun `duplicate looking cars with distinct IDs both survive`() = runTest { preserves(listOf("guest:local", "firebase:A", "firebase:B")) }
    @Test fun `unknown owner is preserved without guessed deletion`() = runTest { preserves(listOf("old-local-profile", "", "local:device")) }
    @Test fun `canonical and legacy rows both survive`() = runTest { preserves(listOf("local:device", "guest:local")) }
    @Test fun `idempotence changes no IDs counts values or alarm keys`() = runTest {
        val f=Fixture(listOf("guest:local", "firebase:A")); f.work.run()
        val rows=f.rows.toList(); val keys=f.keys; f.work.run(); f.work.run()
        assertEquals(rows,f.rows); assertEquals(keys,f.keys); assertEquals(2,f.rows.size)
    }
    @Test fun `commit failure rolls back all owners and durable alarm identities permit retry`() = runTest {
        val f=Fixture(listOf("guest:local", "firebase:A")); val rows=f.rows.toList()
        f.failTransaction=true; assertTrue(runCatching { f.work.run() }.isFailure)
        assertEquals(rows,f.rows); assertTrue(f.keys.isNotEmpty())
        f.failTransaction=false; f.work.run(); assertEquals(rows.map { it.copy(ownerKey="local:device") },f.rows)
    }
    @Test fun `alarm registry failure prevents DB ownership commit`() = runTest {
        val f=Fixture(listOf("guest:local")); val rows=f.rows.toList(); f.failRegistry=true
        assertTrue(runCatching { f.work.run() }.isFailure); assertEquals(rows,f.rows); assertEquals(0,f.updates)
        f.failRegistry=false; f.work.run(); assertEquals("local:device",f.rows.single().ownerKey)
    }
    @Test fun `remaining legacy rows fail validation and transaction rolls back`() = runTest {
        val f=Fixture(listOf("firebase:A")); val rows=f.rows.toList(); f.leaveLegacy=true
        assertTrue(runCatching { f.work.run() }.isFailure); assertEquals(rows,f.rows)
    }
    @Test fun `new process checks DB again despite prior completion`() = runTest {
        val f=Fixture(listOf("local:device")); f.work.run()
        f.rows += testVehicle("firebase:restored", 9)
        f.work.run(); assertEquals(setOf("local:device"),f.rows.map { it.ownerKey }.toSet()); assertEquals(2,f.rows.size)
    }
    @Test fun `guest migration and restored Google identity keep exactly the same garage after process recreation`() = runTest {
        val f = Fixture(listOf("guest:local")); val first = WorkspaceSession(TestDeletionRegistry())
        LocalGarageBootstrap(first) { f.work.run() }.initialize()
        val identity = AuthSession().apply { setUid("A") }
        val before = f.rows.toList(); val alarmKeys = f.keys
        val restarted = WorkspaceSession(TestDeletionRegistry())
        val restoredIdentity = AuthSession().apply { setUid(identity.uid.value) }
        assertFalse(restarted.isResolved.value)
        LocalGarageBootstrap(restarted) { f.work.run() }.initialize()
        val state = observeLocalState(restarted, MutableStateFlow(0)) { flowOf(f.rows.toList()) }
            .first { it is LocalDataState.Ready }
        assertEquals(LocalDataState.Ready("local:device", before), state)
        assertEquals("A", restoredIdentity.uid.value); assertEquals("local:device", restarted.owner.value)
        assertEquals(before, f.rows); assertEquals(alarmKeys, f.keys); assertEquals(1, f.rows.size)
    }
    @Test fun `document and report bytes paths timestamps and associations stay exact`() = runTest {
        val f=Fixture(listOf("guest:local", "firebase:A"))
        val photo=File(dir,"photo.jpg").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        val pdf=File(dir,"report.pdf").apply { writeText("%PDF-existing-private-report") }
        val documents=listOf(DocumentEntity(7,1,"Photo","PHOTOS",photo.path,77),DocumentEntity(8,2,"Rapport","REPORTS",pdf.path,88))
        fun hashes()=listOf(photo,pdf).map { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes(); f.work.run()
        assertEquals(before,hashes()); assertEquals(listOf(7L,8L),documents.map { it.id })
        assertTrue(documents.all { doc -> f.rows.any { it.id==doc.vehicleId && it.ownerKey=="local:device" } })
    }
    @Test fun `legacy alarm identities include every supported lead and old no lead URI`() = runTest {
        val f=Fixture(listOf("guest:local","firebase:A")); val previous=ReminderKey("firebase:B",77,7); f.keys=setOf(previous)
        f.work.run()
        assertTrue(previous in f.keys)
        for ((index,owner) in listOf("guest:local","firebase:A").withIndex()) {
            assertTrue(ReminderKey(owner,index+1L,null) in f.keys)
            ReminderPreferences.LEAD_DAYS.forEach { assertTrue(ReminderKey(owner,index+1L,it) in f.keys) }
        }
    }
    @Test fun `canonical DB requires no reminder registry churn`() = runTest {
        val f=Fixture(listOf("local:device")); f.work.run(); assertTrue(f.keys.isEmpty())
        coVerify(exactly=0) { f.maintenance.getAll(any()) }
    }

    private suspend fun TestScope.bootstrap(owners: List<String>) {
        val f=Fixture(owners); val session=WorkspaceSession(TestDeletionRegistry()); val gate=CompletableDeferred<Unit>()
        val states=mutableListOf<LocalDataState<List<VehicleEntity>>>()
        coroutineScope {
            val observer=launch(start=CoroutineStart.UNDISPATCHED) {
                observeLocalState(session,MutableStateFlow(0)) { flowOf(f.rows.toList()) }.toList(states)
            }
            val work=LocalGarageBootstrap(session) { gate.await(); f.work.run() }
            val init=async { work.initialize() }; runCurrent()
            assertEquals(listOf(LocalDataState.Loading),states); assertFalse(session.isResolved.value)
            gate.complete(Unit); init.await(); runCurrent()
            assertEquals(LocalDataState.Ready("local:device",f.rows.toList()),states.last())
            assertFalse(states.filterIsInstance<LocalDataState.Ready<List<VehicleEntity>>>().any { it.data.isEmpty() && owners.isNotEmpty() })
            observer.cancelAndJoin()
        }
    }
    @Test fun `canonical bootstrap becomes Ready without legacy churn`() = runTest { bootstrap(listOf("local:device")) }
    @Test fun `guest bootstrap waits before Ready`() = runTest { bootstrap(listOf("guest:local")) }
    @Test fun `firebase bootstrap waits before Ready`() = runTest { bootstrap(listOf("firebase:A")) }
    @Test fun `multi workspace bootstrap exposes only the complete union`() = runTest { bootstrap(listOf("guest:local","firebase:A","firebase:B")) }
    @Test fun `bootstrap failure is Error and retry never emits empty Ready`() = runTest {
        val session=WorkspaceSession(TestDeletionRegistry()); var failed=true
        val work=LocalGarageBootstrap(session) { if(failed) error("DB") }
        work.initialize(); assertEquals(GarageReadiness.Error,session.readiness.value); assertFalse(session.isResolved.value)
        assertThrows(IllegalStateException::class.java) { session.requireWritable("local:device") }
        failed=false; work.initialize(); assertTrue(session.isResolved.value)
    }
    @Test fun `auth changes during bootstrap cannot change target or release Loading`() = runTest {
        val session=WorkspaceSession(TestDeletionRegistry()); val identity=AuthSession(); val gate=CompletableDeferred<Unit>()
        val work=LocalGarageBootstrap(session) { gate.await() }; val job=async { work.initialize() }; runCurrent()
        for(uid in listOf("A",null,"B")) { identity.setUid(uid); assertEquals("local:device",session.owner.value); assertFalse(session.isResolved.value) }
        gate.complete(Unit); job.await(); assertTrue(session.isResolved.value)
    }
    @Test fun `cancelled bootstrap never marks complete and retry is possible`() = runTest {
        val session=WorkspaceSession(TestDeletionRegistry()); var cancel=true
        val work=LocalGarageBootstrap(session) { if(cancel) throw CancellationException() }
        assertTrue(runCatching { work.initialize() }.exceptionOrNull() is CancellationException)
        assertFalse(session.isResolved.value); cancel=false; work.initialize(); assertTrue(session.isResolved.value)
    }
    @Test fun `concurrent bootstrap calls perform one consolidation`() = runTest {
        val session=WorkspaceSession(TestDeletionRegistry()); val gate=CompletableDeferred<Unit>(); var calls=0
        val work=LocalGarageBootstrap(session) { calls++; gate.await() }
        val first=async { work.initialize() }; val second=async { work.initialize() }; runCurrent()
        assertEquals(1,calls); gate.complete(Unit); first.await(); second.await(); assertEquals(1,calls)
    }
}
