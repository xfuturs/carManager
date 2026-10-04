package com.carmanager.app.core.util

import android.content.Context
import android.util.Log
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.ReminderPreferences
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.domain.session.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReminderCoordinator @Inject constructor(private val settings: ReminderSettingsStore,
    private val session: WorkspaceSession, private val registry: DeletionRegistry,
    private val maintenance: MaintenanceRepository, private val vehicles: VehicleRepository,
    @ApplicationContext context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null
    private val mutex = Mutex()
    private val reconciler = ReminderReconciler(object : ReminderAlarmPort {
        override fun schedule(reminder: ScheduledReminder) = NotificationHelper.scheduleReminder(context, reminder.triggerAt,
            reminder.title, reminder.message, reminder.key.owner, reminder.key.id, reminder.dueAt, reminder.leadDays)
        override fun cancel(key: ReminderKey) = NotificationHelper.cancelOwnedReminder(context, key.owner, key.id, key.leadDays)
    }, settings::knownKeys, settings::saveKeys)
    private data class Gate(val owner: String, val ready: Boolean, val blocked: Boolean)
    private data class Snapshot(val gate: Gate, val prefs: ReminderPreferences, val records: List<MaintenanceRecord>, val vehicles: List<Vehicle>)
    private fun snapshots(): Flow<Snapshot> {
        val gate = combine(session.owner, session.isResolved, registry.blockedOwners) { owner, ready, blocked -> Gate(owner, ready, owner in blocked) }
        return combine(gate, settings.preferences, maintenance.observeAll(), vehicles.observeAll()) { g, p, m, v -> Snapshot(g, p, m, v) }
    }
    @Synchronized fun start() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            while (isActive) {
                try { snapshots().collect { apply(it) } }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.w("Reminders", "Réconciliation locale indisponible, nouvelle tentative.", error)
                    delay(5_000)
                }
            }
        }
    }
    private suspend fun apply(snapshot: Snapshot) = mutex.withLock {
        val gate = snapshot.gate
        val valid = gate.ready && !gate.blocked && gate.owner == session.owner.value &&
            snapshot.records.all { it.ownerKey == gate.owner } && snapshot.vehicles.all { it.ownerKey == gate.owner }
        reconciler.reconcile(if (valid) ReminderPlanning.plan(gate.owner, snapshot.prefs, snapshot.records, snapshot.vehicles,
            System.currentTimeMillis()) else emptyList(), snapshot.records.filter { it.ownerKey == gate.owner && it.id > 0 }
            .map { ReminderKey(it.ownerKey, it.id, null) }.toSet())
    }
    suspend fun reconcileNow() {
        withTimeout(8_000) {
            val snapshot = snapshots().first { it.gate.ready &&
                it.records.all { row -> row.ownerKey == it.gate.owner } && it.vehicles.all { row -> row.ownerKey == it.gate.owner } }
            apply(snapshot)
        }
    }
    suspend fun enabledFor(record: MaintenanceRecord): Boolean {
        val prefs = settings.preferences.first()
        val now = System.currentTimeMillis()
        return prefs.allows(record.type) && record.nextDueDate?.let { due ->
            prefs.leadDaysSet.any { ReminderPlanning.triggerAt(due, it) > now }
        } == true
    }
    fun afterBoot(onFinished: () -> Unit) { scope.launch {
        try { reconcileNow() } catch (error: Exception) { if (error is CancellationException) throw error; Log.w("Reminders", "Reprise locale indisponible.", error) }
        finally { onFinished() }
    } }
}
