package com.carmanager.app.a14_1

import androidx.datastore.preferences.core.*
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.ReminderKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class MultiLeadPreferencesTest {
    @TempDir lateinit var dir: File
    private val old = intPreferencesKey("reminders_lead_days")
    private val new = stringSetPreferencesKey("reminders_lead_days_set")
    private fun scenario(body: suspend (androidx.datastore.core.DataStore<Preferences>, ReminderSettingsStore) -> Unit) = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(dir, "settings.preferences_pb") })
        try { body(store, ReminderSettingsStore(store)) } finally { job.cancelAndJoin() }
    }
    @Test fun `new installation durably defaults to same day only`() = scenario { store, reminders ->
        assertEquals(setOf(0), reminders.preferences.first().leadDaysSet)
        assertEquals(setOf("0"), store.data.first()[new]); assertNull(store.data.first()[old])
    }
    @ParameterizedTest @ValueSource(ints = [0,1,3,7,14,30])
    fun `each old valid choice migrates exactly and does not change unrelated keys`(days: Int) = scenario { store, reminders ->
        store.edit { it[old] = days; it[stringPreferencesKey("theme_preference")] = "DARK"
            it[stringPreferencesKey("currency_preference")] = "CHF"; it[stringPreferencesKey("distance_unit_preference")] = "mi" }
        assertEquals(setOf(days), reminders.preferences.first().leadDaysSet)
        assertEquals(setOf(days.toString()), store.data.first()[new]); assertEquals(days, store.data.first()[old])
        assertEquals("DARK", store.data.first()[stringPreferencesKey("theme_preference")])
        assertEquals("CHF", store.data.first()[stringPreferencesKey("currency_preference")])
        assertEquals("mi", store.data.first()[stringPreferencesKey("distance_unit_preference")])
    }
    @ParameterizedTest @ValueSource(ints = [-1,2,19,31,2147483647])
    fun `invalid old choice migrates to safe default`(days: Int) = scenario { store, reminders ->
        store.edit { it[old] = days }; assertEquals(setOf(0), reminders.preferences.first().leadDaysSet)
    }
    @Test fun `new set is authoritative and migration is idempotent`() = scenario { store, reminders ->
        store.edit { it[old] = 7; it[new] = setOf("30","1","0") }
        repeat(3) { assertEquals(setOf(0,1,30), reminders.preferences.first().leadDaysSet) }
        reminders.update { it.toggleLead(14) }
        assertEquals(setOf(0,1,14,30), reminders.preferences.first().leadDaysSet)
        assertEquals(7, store.data.first()[old])
    }
    @Test fun `malformed set ignores invalid values and normalizes order independent representation`() = scenario { store, reminders ->
        store.edit { it[new] = setOf("x","7","-3","01","30","999") }
        assertEquals(setOf(1,7,30), reminders.preferences.first().leadDaysSet)
        assertEquals(setOf("1","7","30"), store.data.first()[new])
    }
    @Test fun `empty persisted new set does not resurrect old value`() = scenario { store, reminders ->
        store.edit { it[old] = 30; it[new] = emptySet() }
        assertEquals(setOf(0), reminders.preferences.first().leadDaysSet)
    }
    @Test fun `multiple values survive a real DataStore file restart`() {
        scenario { _, reminders -> reminders.update { it.copy(leadDaysSet = setOf(30,7,1,0)) } }
        scenario { _, reminders -> assertEquals(setOf(30,7,1,0), reminders.preferences.first().leadDaysSet) }
    }
    @Test fun `empty active and inactive writes are rejected without replacing durable choice`() = scenario { _, reminders ->
        reminders.update { it.copy(leadDaysSet = setOf(7,30)) }
        for (enabled in listOf(true,false)) {
            assertThrows(IllegalArgumentException::class.java) { ReminderPreferences(enabled = enabled, leadDaysSet = emptySet()) }
        }
        assertEquals(setOf(7,30), reminders.preferences.first().leadDaysSet)
    }
    @Test fun `global off and on retain set and category flags`() = scenario { _, reminders ->
        reminders.update { it.copy(leadDaysSet = setOf(30,7,1,0), insurance = false) }
        reminders.update { it.copy(enabled = false) }; assertEquals(setOf(30,7,1,0), reminders.preferences.first().leadDaysSet)
        reminders.update { it.copy(enabled = true) }; assertFalse(reminders.preferences.first().insurance)
        assertEquals(setOf(30,7,1,0), reminders.preferences.first().leadDaysSet)
    }
    @ParameterizedTest @ValueSource(ints = [0,1,3,7,14,30])
    fun `last selected chip is retained but additional choices toggle independently`(days: Int) {
        val one = ReminderPreferences(leadDaysSet = setOf(days))
        assertEquals(one, one.toggleLead(days))
        val other = if (days == 0) 7 else 0
        assertEquals(setOf(days, other), one.toggleLead(other).leadDaysSet)
        assertEquals(setOf(other), one.toggleLead(other).toggleLead(days).leadDaysSet)
    }
    @Test fun `registry retains multiple leads and decodes legacy owner id without guessing a lead`() = scenario { store, reminders ->
        val legacy = ReminderKey("firebase:é/:", 9, null)
        store.edit { it[stringSetPreferencesKey("reminders_scheduled_keys")] = setOf(legacy.encode(),"bad") }
        assertEquals(setOf(legacy), reminders.knownKeys())
        val keys = setOf(legacy, ReminderKey(legacy.owner,9,0),ReminderKey(legacy.owner,9,7),ReminderKey(legacy.owner,9,30))
        reminders.saveKeys(keys); assertEquals(keys, reminders.knownKeys())
    }
}
