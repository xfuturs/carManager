package com.carmanager.app.a16

import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.core.domain.repository.SyncRepository
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.*
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LocalGarageContinuityTest {
    private suspend fun create(uid: String?) {
        val auth=AuthSession().apply { setUid(uid) }; val f=GarageFixture()
        val before=f.vehicles.toMap()
        val id=f.writer.saveVehicle(f.vehicle().copy(id=0,brand="Nouvelle",currentMileage=0))
        assertEquals(LocalGarageOwner.KEY,f.vehicles[id]?.ownerKey)
        auth.setUid(null); assertEquals(before.keys+id,f.vehicles.keys)
        auth.setUid("other"); assertEquals(LocalGarageOwner.KEY,f.session.owner.value)
        assertEquals(1,f.history.size)
    }
    @Test fun `guest creates under canonical owner`() = runTest { create(null) }
    @Test fun `Google creates under canonical owner`() = runTest { create("A") }
    @Test fun `another Google identity creates under same owner`() = runTest { create("B") }
    @Test fun `auth transitions preserve fuel maintenance mileage and all vehicle fields`() = runTest {
        val f=GarageFixture(); val identity=AuthSession()
        f.writer.saveFuel(f.fuel()); f.writer.saveMaintenance(f.maintenance(1600))
        val before=listOf(f.vehicles.toMap(),f.fuelRows.toList(),f.maintenanceRows.toList(),f.history.toList())
        for(uid in listOf(null,"A",null,"B",null)) {
            identity.setUid(uid); f.session.requireWritable(LocalGarageOwner.KEY)
            assertEquals(before,listOf(f.vehicles.toMap(),f.fuelRows.toList(),f.maintenanceRows.toList(),f.history.toList()))
        }
    }
    private suspend fun deletion(remoteFails: Boolean=false, restarting: Boolean=false) {
        val f=GarageFixture(); f.writer.saveFuel(f.fuel()); val before=f.vehicles.toMap(); val rows=f.fuelRows.toList()
        val identity=AuthSession().apply { setUid("A") }
        val registry=TestDeletionRegistry(); if(restarting) registry.block("firebase:A")
        val remote=mockk<AccountRemoteData>(relaxed=true); val local=mockk<LocalAccountData>()
        if(remoteFails) coEvery { remote.deleteKnownData("A") } throws IllegalStateException("offline")
        val result=AccountDeletion(identity,registry,mockk<SyncRepository>(relaxed=true),local,remote).delete("firebase:A")
        assertEquals(!remoteFails,result.isSuccess)
        assertEquals(before,f.vehicles); assertEquals(rows,f.fuelRows)
        coVerify(exactly=0) { local.purge(any()) }
        f.registry.block("firebase:A"); f.session.requireWritable(LocalGarageOwner.KEY)
        identity.setUid(null); assertEquals("local:device",f.session.owner.value)
    }
    @Test fun `account deletion preserves local garage and signout leaves it accessible`() = runTest { deletion() }
    @Test fun `remote deletion failure preserves local garage`() = runTest { deletion(remoteFails=true) }
    @Test fun `pending deletion restored in new process never resumes local purge`() = runTest { deletion(restarting=true) }
    @Test fun `pending deletion with remote failure still cannot purge garage`() = runTest { deletion(remoteFails=true,restarting=true) }

    @Test fun `canonical owner is not a deletable Google account`() = runTest {
        val auth=AuthSession().apply { setUid("A") }; val remote=mockk<AccountRemoteData>(relaxed=true)
        val local=mockk<LocalAccountData>(); val registry=TestDeletionRegistry()
        assertTrue(AccountDeletion(auth,registry,mockk(relaxed=true),local,remote).delete("local:device").isFailure)
        coVerify(exactly=0) { local.purge(any()); remote.deleteKnownData(any()); remote.deleteAuth(any()) }
        assertTrue(registry.blockedOwners.value.isEmpty())
    }
    private class Port : ReminderAlarmPort {
        val cancelled=mutableListOf<ReminderKey>(); val scheduled=mutableListOf<ScheduledReminder>(); var fail=false
        override fun cancel(key: ReminderKey) { if(fail) error("AlarmManager unavailable"); cancelled+=key }
        override fun schedule(reminder: ScheduledReminder) { scheduled+=reminder }
    }
    private fun reminders(): List<ScheduledReminder> = listOf(0,7).map { lead ->
        ScheduledReminder(ReminderKey("local:device",1,lead),1000L+lead,9000,"Test","Échéance",lead)
    }
    @Test fun `legacy owner and no lead identities cancel before canonical reminders schedule`() = runTest {
        val old=setOf(ReminderKey("guest:local",1,null),ReminderKey("firebase:A",1,7),ReminderKey("firebase:B",1,0))
        var keys=old; val port=Port(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        reconciler.reconcile(reminders()); assertEquals(old,port.cancelled.toSet())
        assertEquals(reminders().map { it.key }.toSet(),keys); assertEquals(2,port.scheduled.size)
    }
    @Test fun `auth switches cannot duplicate canonical reminders`() = runTest {
        var keys=emptySet<ReminderKey>(); val port=Port(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        val identity=AuthSession()
        for(uid in listOf(null,"A",null,"B",null)) { identity.setUid(uid); reconciler.reconcile(reminders()) }
        assertEquals(2,port.scheduled.size); assertTrue(port.cancelled.isEmpty()); assertEquals(2,keys.size)
    }
    @Test fun `partial legacy alarm cancellation remains durable for restart retry`() = runTest {
        val old=ReminderKey("firebase:A",1,7); var keys=setOf(old); val port=Port().apply { fail=true }
        assertTrue(runCatching { ReminderReconciler(port,{keys},{keys=it}).reconcile(reminders()) }.isFailure)
        assertTrue(old in keys); assertTrue(reminders().all { it.key in keys })
        port.fail=false; val restarted=ReminderReconciler(port,{keys},{keys=it})
        restarted.reconcile(reminders()); restarted.reconcile(reminders())
        assertEquals(2,port.scheduled.size); assertEquals(setOf(old),port.cancelled.toSet())
    }
}
