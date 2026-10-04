package com.carmanager.app.a14

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.EnumSource
import java.time.*

class ReminderPlanningTest {
    private val owner = "firebase:A"
    private val zone = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 3, 1, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val due = ZonedDateTime.of(2026, 4, 1, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val vehicle = Vehicle(7, "Renault", "Clio", 2020, 1000, FuelType.GASOLINE, powerHp = 90, licensePlate = null, createdAt = 0, updatedAt = 0, ownerKey = owner)
    private fun record(type: MaintenanceType = MaintenanceType.OIL_CHANGE, id: Long = 1) = MaintenanceRecord(id, 7, type, date = 0, mileage = 1000, cost = 12.5, nextDueDate = due, ownerKey = owner)
    private fun plan(p: ReminderPreferences = ReminderPreferences(), rows: List<MaintenanceRecord> = listOf(record())) = ReminderPlanning.plan(owner, p, rows, listOf(vehicle), now, zone)
    @ParameterizedTest @ValueSource(ints = [0, 1, 3, 7, 14, 30]) fun `allowed lead times subtract calendar days including daylight saving`(days: Int) {
        val reminder = plan(ReminderPreferences(leadDaysSet = setOf(days))).single()
        assertEquals(Instant.ofEpochMilli(due).atZone(zone).minusDays(days.toLong()).toInstant().toEpochMilli(), reminder.triggerAt)
        assertEquals(due, reminder.dueAt); assertEquals(days, reminder.leadDays)
    }
    @ParameterizedTest @EnumSource(MaintenanceType::class) fun `all existing types map to the three actual deadline categories`(type: MaintenanceType) {
        val category = ReminderCategory.of(type)
        assertTrue(plan(rows = listOf(record(type))).single().title.contains(category.label))
        assertTrue(plan(ReminderPreferences().withCategory(category, false), listOf(record(type))).isEmpty())
    }
    @Test fun `global disabled and unavailable date produce no alarms`() {
        assertTrue(plan(ReminderPreferences(enabled = false)).isEmpty())
        assertTrue(plan(rows = listOf(record().copy(nextDueDate = null, nextDueMileage = 5000), record().copy(id = 2, nextDueDate = now - 1))).isEmpty())
    }
    @Test fun `advance already passed is skipped without fallback`() {
        val soon = record().copy(nextDueDate = now + 3600_000)
        assertTrue(plan(ReminderPreferences(leadDaysSet = setOf(30)), listOf(soon)).isEmpty())
    }
    @Test fun `foreign owner foreign vehicle missing parent and unsaved row are excluded`() {
        assertTrue(plan(rows = listOf(record().copy(ownerKey = "firebase:B"), record().copy(vehicleId = 99), record().copy(id = 0))).isEmpty())
        assertTrue(ReminderPlanning.plan(owner, ReminderPreferences(), listOf(record()), listOf(vehicle.copy(ownerKey = "firebase:B")), now, zone).isEmpty())
    }
    @Test fun `identity is stable across timing and distinguishes same date records and owners`() {
        val a = plan().single(); val b = plan(ReminderPreferences(leadDaysSet = setOf(7))).single()
        assertNotEquals(a.key, b.key); assertNotEquals(a.triggerAt, b.triggerAt)
        assertEquals(2, plan(rows = listOf(record(), record(id = 2))).map { it.key }.distinct().size)
        val key = ReminderKey("firebase:é/:", 99); assertEquals(key, ReminderKey.decode(key.encode()))
        assertNotEquals(key, ReminderKey("firebase:B", 99)); assertNull(ReminderKey.decode("invalid"))
    }
    @Test fun `delivery rejects removed changed blocked foreign disabled and obsolete lead reminders`() {
        fun deliver(p: ReminderPreferences = ReminderPreferences(), row: MaintenanceRecord? = record(), active: String = owner, blocked: Boolean = false, lead: Int? = 0) =
            ReminderPlanning.canDeliver(owner, active, blocked, p, row, due, lead)
        assertTrue(deliver()); assertFalse(deliver(row = null)); assertFalse(deliver(active = "firebase:B")); assertFalse(deliver(blocked = true))
        assertFalse(deliver(p = ReminderPreferences(enabled = false))); assertFalse(deliver(p = ReminderPreferences(maintenance = false)))
        assertFalse(deliver(row = record().copy(nextDueDate = due + 1))); assertFalse(deliver(row = record().copy(ownerKey = "firebase:B")))
        assertFalse(deliver(p = ReminderPreferences(leadDaysSet = setOf(7))))
        assertFalse(deliver(lead = null)) // Les anciennes alarmes sont annulées, sans livraison.
    }
    private class Port : ReminderAlarmPort {
        val scheduled = mutableListOf<ScheduledReminder>(); val cancelled = mutableListOf<ReminderKey>(); var fail = false
        override fun schedule(reminder: ScheduledReminder) { if (fail) error("alarm"); scheduled += reminder }
        override fun cancel(key: ReminderKey) { cancelled += key }
    }
    @Test fun `reconciliation changes time cancels disabled and reenables without duplicate identity`() = runTest {
        val port = Port(); var keys = emptySet<ReminderKey>(); val reconciler = ReminderReconciler(port, { keys }, { keys = it })
        reconciler.reconcile(plan()); reconciler.reconcile(plan()); assertEquals(1, port.scheduled.size)
        reconciler.reconcile(plan(ReminderPreferences(leadDaysSet = setOf(7)))); assertEquals(2, port.scheduled.size); assertEquals(1, keys.size)
        reconciler.reconcile(emptyList()); assertEquals(listOf(plan().single().key, ReminderKey(owner, 1, 7)), port.cancelled); assertTrue(keys.isEmpty())
        reconciler.reconcile(plan()); assertEquals(3, port.scheduled.size)
    }
    @Test fun `new process reschedules durable identities and cancels inactive owner`() = runTest {
        val old = ReminderKey("firebase:B", 99); var keys = setOf(old, ReminderKey(owner, 1)); val port = Port()
        ReminderReconciler(port, { keys }, { keys = it }).reconcile(plan())
        assertEquals(listOf(old), port.cancelled); assertEquals(1, port.scheduled.size); assertEquals(setOf(ReminderKey(owner, 1)), keys)
    }
    @Test fun `legacy alarm without registry is cancelled by disabled current record`() = runTest {
        val port = Port(); var keys = emptySet<ReminderKey>()
        ReminderReconciler(port, { keys }, { keys = it }).reconcile(emptyList(), setOf(ReminderKey(owner, 1)))
        assertEquals(listOf(ReminderKey(owner, 1)), port.cancelled); assertTrue(keys.isEmpty())
    }
    @Test fun `partial scheduling failure remains cancellable and retryable`() = runTest {
        val port = Port(); var keys = emptySet<ReminderKey>(); val reconciler = ReminderReconciler(port, { keys }, { keys = it })
        port.fail = true
        try { reconciler.reconcile(plan()); fail<Unit>("failure") } catch (_: IllegalStateException) {}
        assertEquals(setOf(ReminderKey(owner, 1)), keys)
        port.fail = false; reconciler.reconcile(plan()); assertEquals(1, port.scheduled.size)
        reconciler.reconcile(emptyList()); assertEquals(listOf(ReminderKey(owner, 1)), port.cancelled)
    }
    @Test fun `failed final registry write after cancellation does not skip reenabling the same alarm`() = runTest {
        val port = Port(); var keys = emptySet<ReminderKey>(); var failEmptyWrite = false
        val reconciler = ReminderReconciler(port, { keys }, { value ->
            if (failEmptyWrite && value.isEmpty()) error("disk write after cancel")
            keys = value
        })
        reconciler.reconcile(plan()); failEmptyWrite = true
        try { reconciler.reconcile(emptyList()); fail<Unit>("write failure") } catch (_: IllegalStateException) {}
        assertEquals(listOf(ReminderKey(owner, 1)), port.cancelled)
        failEmptyWrite = false; reconciler.reconcile(plan())
        assertEquals(2, port.scheduled.size); assertEquals(setOf(ReminderKey(owner, 1)), keys)
    }
}
