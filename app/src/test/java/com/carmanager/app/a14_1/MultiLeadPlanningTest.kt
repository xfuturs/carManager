package com.carmanager.app.a14_1

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.*

class MultiLeadPlanningTest {
    private val owner = "firebase:A"
    private val zone = ZoneId.of("Europe/Paris")
    private val due = ZonedDateTime.of(2026,4,10,10,0,0,0,zone).toInstant().toEpochMilli()
    private val now = ZonedDateTime.of(2026,3,1,10,0,0,0,zone).toInstant().toEpochMilli()
    private val row = MaintenanceRecord(1,7,MaintenanceType.OIL_CHANGE,date=0,mileage=1000,cost=12.0,nextDueDate=due,ownerKey=owner)
    private val vehicle = Vehicle(7,"Renault","Clio",2020,1000,FuelType.GASOLINE,powerHp=90,licensePlate=null,createdAt=0,updatedAt=0,ownerKey=owner)
    private val prefs = ReminderPreferences(leadDaysSet=setOf(30,7,1,0))
    private fun plan(p: ReminderPreferences = prefs, time: Long = now) = ReminderPlanning.plan(owner,p,listOf(row),listOf(vehicle),time,zone)
    private class Port : ReminderAlarmPort {
        val active = mutableMapOf<ReminderKey,ScheduledReminder>()
        val scheduled = mutableListOf<ReminderKey>(); val cancelled = mutableListOf<ReminderKey>()
        override fun schedule(reminder: ScheduledReminder) { active[reminder.key]=reminder; scheduled+=reminder.key }
        override fun cancel(key: ReminderKey) { active.remove(key); cancelled+=key }
    }
    @Test fun `four future leads produce four distinct stable identities`() {
        val alarms=plan(); assertEquals(listOf(0,1,7,30), alarms.map { it.leadDays })
        assertEquals(4,alarms.map { it.key }.distinct().size)
        alarms.forEach { assertEquals(it.leadDays,it.key.leadDays); assertEquals(it.key,ReminderKey.decode(it.key.encode())) }
        assertNotEquals(ReminderKey(owner,1,7),ReminderKey("firebase:B",1,7))
    }
    @Test fun `second reconciliation adds no duplicate and removing one cancels only that lead`() = runTest {
        val port=Port(); var keys=emptySet<ReminderKey>(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        reconciler.reconcile(plan()); reconciler.reconcile(plan()); assertEquals(4,port.scheduled.size)
        reconciler.reconcile(plan(prefs.toggleLead(30)))
        assertEquals(listOf(ReminderKey(owner,1,30)),port.cancelled); assertEquals(4,port.scheduled.size)
        assertEquals(setOf(0,1,7),keys.map { it.leadDays }.toSet())
    }
    @Test fun `adding fourteen schedules only the new lead`() = runTest {
        val port=Port(); var keys=emptySet<ReminderKey>(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        reconciler.reconcile(plan()); reconciler.reconcile(plan(prefs.toggleLead(14)))
        assertEquals(5,port.scheduled.size); assertEquals(ReminderKey(owner,1,14),port.scheduled.last()); assertTrue(port.cancelled.isEmpty())
    }
    @Test fun `global off cancels all and on reconstructs all`() = runTest {
        val port=Port(); var keys=emptySet<ReminderKey>(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        reconciler.reconcile(plan()); reconciler.reconcile(plan(prefs.copy(enabled=false)))
        assertEquals(4,port.cancelled.size); assertTrue(port.active.isEmpty())
        reconciler.reconcile(plan()); assertEquals(4,port.active.size); assertEquals(8,port.scheduled.size)
    }
    @Test fun `category off cancels its four leads while another category remains`() = runTest {
        val ct=row.copy(id=2,type=MaintenanceType.TECHNICAL_INSPECTION)
        fun both(p: ReminderPreferences)=ReminderPlanning.plan(owner,p,listOf(row,ct),listOf(vehicle),now,zone)
        val port=Port(); var keys=emptySet<ReminderKey>(); val reconciler=ReminderReconciler(port,{keys},{keys=it})
        reconciler.reconcile(both(prefs)); reconciler.reconcile(both(prefs.copy(maintenance=false)))
        assertEquals(4,port.cancelled.size); assertTrue(port.cancelled.all { it.id==1L })
        assertEquals(4,port.active.size); assertTrue(port.active.keys.all { it.id==2L })
    }
    @Test fun `passed thirty is skipped independently while seven and one remain`() {
        val time=ReminderPlanning.triggerAt(due,14,zone)
        assertEquals(listOf(1,7),plan(prefs.copy(leadDaysSet=setOf(30,7,1)),time).map { it.leadDays })
    }
    @Test fun `no forced same day fallback and exact now is already passed`() {
        assertTrue(plan(prefs.copy(leadDaysSet=setOf(30)),ReminderPlanning.triggerAt(due,30,zone)).isEmpty())
        assertTrue(plan(time=due).isEmpty())
    }
    @ParameterizedTest @ValueSource(ints=[0,1,3,7,14,30])
    fun `delivery accepts each selected lead and rejects it after removal`(days:Int) {
        val p=prefs.copy(leadDaysSet=setOf(days, if(days==0) 7 else 0))
        assertTrue(ReminderPlanning.canDeliver(owner,owner,false,p,row,due,days))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,false,p.toggleLead(days),row,due,days))
    }
    @Test fun `delivery rejects inactive owner deleted record obsolete deadline and legacy lead`() {
        assertFalse(ReminderPlanning.canDeliver(owner,"firebase:B",false,prefs,row,due,7))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,false,prefs,null,due,7))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,true,prefs,row,due,7))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,false,prefs,row,due+1,7))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,false,prefs,row,due,null))
        assertFalse(ReminderPlanning.canDeliver(owner,owner,false,prefs,row,null,7))
    }
    @Test fun `boot style fresh reconciler restores every selected lead and cleans legacy first`() = runTest {
        val legacy=ReminderKey(owner,1,null); val port=Port(); var keys=setOf(legacy)
        ReminderReconciler(port,{keys},{keys=it}).reconcile(plan(),setOf(legacy))
        assertEquals(listOf(legacy),port.cancelled); assertEquals(4,port.active.size); assertEquals(4,keys.size)
        val reboot=Port(); ReminderReconciler(reboot,{keys},{keys=it}).reconcile(plan())
        assertEquals(4,reboot.scheduled.size); assertEquals(keys,reboot.active.keys)
    }
    @Test fun `duplicate records do not duplicate lead identities`() {
        assertEquals(4,ReminderPlanning.plan(owner,prefs,listOf(row,row),listOf(vehicle),now,zone).size)
    }
    @ParameterizedTest @ValueSource(ints=[3,7,14,30])
    fun `spring DST subtracts calendar days instead of twenty four hour durations`(days:Int) {
        val end=ZonedDateTime.of(2026,3,30,10,0,0,0,zone)
        val actual=ReminderPlanning.triggerAt(end.toInstant().toEpochMilli(),days,zone)
        assertEquals(end.minusDays(days.toLong()).toInstant().toEpochMilli(),actual)
        assertNotEquals(end.toInstant().toEpochMilli()-days*86400_000L,actual)
    }
    @Test fun `autumn DST preserves local clock`() {
        val end=ZonedDateTime.of(2026,10,26,10,0,0,0,zone)
        val actual=Instant.ofEpochMilli(ReminderPlanning.triggerAt(end.toInstant().toEpochMilli(),7,zone)).atZone(zone)
        assertEquals(10,actual.hour); assertEquals(end.minusDays(7),actual)
    }
}
