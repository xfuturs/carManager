package com.carmanager.app.core.util

import com.carmanager.app.core.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ReminderKey(val owner: String, val id: Long, val leadDays: Int? = 0) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(owner.toByteArray(Charsets.UTF_8)) + ":$id" +
        (leadDays?.let { ":$it" } ?: "")
    companion object { fun decode(value: String): ReminderKey? = runCatching {
        val parts = value.split(':'); require(parts.size in 2..3)
        val days = if (parts.size == 3) parts[2].toInt().also { require(it in ReminderPreferences.LEAD_DAYS) } else null
        ReminderKey(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8), parts[1].toLong(), days)
            .also { require(it.id > 0 && it.owner.isNotBlank()) }
    }.getOrNull() }
}
data class ScheduledReminder(val key: ReminderKey, val triggerAt: Long, val dueAt: Long, val title: String, val message: String,
    val leadDays: Int = 0)

object ReminderPlanning {
    fun plan(owner: String, preferences: ReminderPreferences, records: List<MaintenanceRecord>, vehicles: List<Vehicle>,
        now: Long, zone: ZoneId = ZoneId.systemDefault()): List<ScheduledReminder> {
        if (!preferences.enabled) return emptyList()
        val available = vehicles.filter { it.ownerKey == owner }.associateBy { it.id }
        return records.filter { it.ownerKey == owner && it.id > 0 && preferences.allows(it.type) }.flatMap { record ->
            val vehicle = available[record.vehicleId] ?: return@flatMap emptyList()
            val due = record.nextDueDate?.takeIf { it > now } ?: return@flatMap emptyList()
            val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE).format(Instant.ofEpochMilli(due).atZone(zone))
            preferences.leadDaysSet.sorted().mapNotNull { days ->
                val trigger = triggerAt(due, days, zone).takeIf { it > now } ?: return@mapNotNull null
                ScheduledReminder(ReminderKey(owner, record.id, days), trigger, due, "Rappel : ${ReminderCategory.of(record.type).label}",
                    "${vehicle.brand} ${vehicle.model} : échéance le $date.", days)
            }
        }.distinctBy { it.key }
    }
    fun triggerAt(due: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(due).atZone(zone).minusDays(days.toLong()).toInstant().toEpochMilli()
    fun canDeliver(expected: String, active: String, blocked: Boolean, prefs: ReminderPreferences,
        record: MaintenanceRecord?, dueAt: Long?, leadDays: Int? = null): Boolean = expected == active && !blocked && record != null &&
        record.ownerKey == expected && prefs.allows(record.type) && record.nextDueDate != null &&
        dueAt != null && record.nextDueDate == dueAt && leadDays != null && leadDays in prefs.leadDaysSet
}

internal interface ReminderAlarmPort {
    fun schedule(reminder: ScheduledReminder)
    fun cancel(key: ReminderKey)
}
/** Réconciliation sans modification de données métier. Les URI stables remplacent les alarmes. */
internal class ReminderReconciler(private val alarms: ReminderAlarmPort,
    private val readKeys: suspend () -> Set<ReminderKey>, private val saveKeys: suspend (Set<ReminderKey>) -> Unit) {
    private var applied = emptyMap<ReminderKey, ScheduledReminder>()
    suspend fun reconcile(reminders: List<ScheduledReminder>, legacyKeys: Set<ReminderKey> = emptySet()) {
        val desired = reminders.associateBy { it.key }
        val previous = applied
        val known = readKeys() + previous.keys + legacyKeys
        // Après toute opération partielle, ne plus supposer les anciennes alarmes présentes.
        // Une reprise remplacera les mêmes URI, sans multiplier les identités.
        applied = emptyMap()
        saveKeys(known + desired.keys) // Enregistrer aussi les opérations partielles avant de les exécuter.
        (known - desired.keys).forEach { currentCoroutineContext().ensureActive(); alarms.cancel(it) }
        desired.values.filter { previous[it.key] != it }.forEach { currentCoroutineContext().ensureActive(); alarms.schedule(it) }
        saveKeys(desired.keys)
        applied = desired
    }
}
