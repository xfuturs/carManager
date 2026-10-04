package com.carmanager.app.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.carmanager.app.core.domain.model.ReminderPreferences
import com.carmanager.app.core.util.ReminderKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderSettingsStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)
    private val enabled = booleanPreferencesKey("reminders_enabled")
    private val maintenance = booleanPreferencesKey("reminders_maintenance")
    private val inspection = booleanPreferencesKey("reminders_technical_inspection")
    private val insurance = booleanPreferencesKey("reminders_insurance")
    private val lead = intPreferencesKey("reminders_lead_days")
    private val leads = stringSetPreferencesKey("reminders_lead_days_set")
    private val scheduled = stringSetPreferencesKey("reminders_scheduled_keys")
    private fun decode(p: Preferences) = ReminderPreferences(p[enabled] ?: true, p[maintenance] ?: true,
        p[inspection] ?: true, p[insurance] ?: true, selected(p))
    private fun selected(p: Preferences): Set<Int> = (p[leads]?.mapNotNull { it.toIntOrNull() }
        ?.filter { it in ReminderPreferences.LEAD_DAYS }?.toSet()
        ?: setOf((p[lead] ?: 0).takeIf { it in ReminderPreferences.LEAD_DAYS } ?: 0)).ifEmpty { setOf(0) }
    // L'edit relit la valeur courante : une migration concurrente ne remplace jamais un clic.
    private suspend fun migrate() { store.edit { p ->
        val normalized = selected(p).map(Int::toString).toSet()
        if (p[leads] != normalized) p[leads] = normalized
    } }
    val preferences: Flow<ReminderPreferences> = flow { migrate(); emitAll(store.data.map(::decode)) }.distinctUntilChanged()
    suspend fun update(change: (ReminderPreferences) -> ReminderPreferences) {
        store.edit { p -> val value = change(decode(p))
            p[enabled] = value.enabled; p[maintenance] = value.maintenance; p[inspection] = value.technicalInspection
            p[insurance] = value.insurance; p[leads] = value.leadDaysSet.map(Int::toString).toSet()
        }
    }
    suspend fun knownKeys(): Set<ReminderKey> = store.data.first()[scheduled].orEmpty().mapNotNull(ReminderKey::decode).toSet()
    suspend fun saveKeys(keys: Set<ReminderKey>) { store.edit { it[scheduled] = keys.map(ReminderKey::encode).toSet() } }
}
