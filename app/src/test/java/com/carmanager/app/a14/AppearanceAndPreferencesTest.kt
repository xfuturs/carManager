package com.carmanager.app.a14

import androidx.datastore.preferences.core.*
import com.carmanager.app.core.data.repository.SettingsRepositoryImpl
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.ui.startup.*
import com.carmanager.app.core.util.ReminderKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.util.stream.Stream

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceAndPreferencesTest {
    @TempDir lateinit var dir: File
    private val theme = stringPreferencesKey("theme_preference")
    private fun scenario(body: suspend (androidx.datastore.core.DataStore<Preferences>) -> Unit) = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { File(dir, "settings.preferences_pb") })
        try { body(store) } finally { job.cancelAndJoin() }
    }
    companion object {
        @JvmStatic fun appearances(): Stream<Arguments> = listOf<String?>(null, "SYSTEM", "invalid", "LIGHT", "DARK")
            .flatMap { name -> listOf(false, true).map { Arguments.of(name, it) } }.stream()
    }
    @ParameterizedTest @MethodSource("appearances") fun `explicit theme is durably resolved before first emission`(name: String?, dark: Boolean) = scenario { store ->
        store.edit { p -> name?.let { p[theme] = it }; p[stringPreferencesKey("currency_preference")] = "CHF"; p[stringPreferencesKey("distance_unit_preference")] = "mi" }
        var systemDark = dark
        val repository = SettingsRepositoryImpl(store) { systemDark }
        val expected = when(name) { "LIGHT" -> AppTheme.LIGHT; "DARK" -> AppTheme.DARK; else -> if(dark) AppTheme.DARK else AppTheme.LIGHT }
        assertEquals(expected, repository.themePreference.first())
        assertEquals(expected.name, store.data.first()[theme])
        systemDark = !dark
        assertEquals(expected, repository.themePreference.first())
        assertEquals("CHF", repository.currency.first()); assertEquals("mi", repository.distanceUnit.first())
    }
    @Test fun `SYSTEM write is rejected without changing an explicit choice`() = scenario { store ->
        val repository = SettingsRepositoryImpl(store) { false }; repository.setThemePreference(AppTheme.DARK)
        try { repository.setThemePreference(AppTheme.SYSTEM); fail<Unit>("SYSTEM must be rejected") } catch (_: IllegalArgumentException) {}
        assertEquals("DARK", store.data.first()[theme])
    }
    @Test fun `simultaneous Home toggles are atomic and share Settings state`() = scenario { store ->
        val repository = SettingsRepositoryImpl(store) { false }; repository.setThemePreference(AppTheme.LIGHT)
        coroutineScope { repeat(21) { launch { repository.toggleTheme() } } }
        assertEquals(AppTheme.DARK, repository.themePreference.first())
        repository.setThemePreference(AppTheme.LIGHT); assertEquals(AppTheme.LIGHT, repository.themePreference.first())
    }
    @Test fun `theme and reminder settings survive reopening the same file`() {
        scenario { store -> SettingsRepositoryImpl(store) { false }.setThemePreference(AppTheme.DARK)
            ReminderSettingsStore(store).update { it.copy(enabled = false, insurance = false, leadDaysSet = setOf(14)) } }
        scenario { store -> assertEquals(AppTheme.DARK, SettingsRepositoryImpl(store) { false }.themePreference.first())
            assertEquals(ReminderPreferences(enabled = false, insurance = false, leadDaysSet = setOf(14)), ReminderSettingsStore(store).preferences.first()) }
    }
    @Test fun `reminder defaults preserve historic enabled and same day behavior`() = scenario { store ->
        assertEquals(ReminderPreferences(), ReminderSettingsStore(store).preferences.first())
    }
    @Test fun `category edits and alarm registry preserve theme currency distance and each other`() = scenario { store ->
        val appearance = SettingsRepositoryImpl(store) { false }
        appearance.setThemePreference(AppTheme.LIGHT); appearance.setCurrency("CHF"); appearance.setDistanceUnit("mi")
        val reminders = ReminderSettingsStore(store)
        coroutineScope { launch { reminders.update { it.copy(maintenance = false) } }; launch { reminders.update { it.copy(insurance = false) } } }
        reminders.update { it.copy(leadDaysSet = setOf(7)) }; reminders.saveKeys(setOf(ReminderKey("firebase:A", 2)))
        assertEquals(ReminderPreferences(maintenance = false, insurance = false, leadDaysSet = setOf(7)), reminders.preferences.first())
        assertEquals(setOf(ReminderKey("firebase:A", 2)), reminders.knownKeys())
        assertEquals("LIGHT", store.data.first()[theme]); assertEquals("CHF", appearance.currency.first()); assertEquals("mi", appearance.distanceUnit.first())
    }
    @Test fun `invalid historical lead time uses same day without changing business or theme keys`() = scenario { store ->
        store.edit { it[intPreferencesKey("reminders_lead_days")] = 19; it[theme] = "DARK" }
        assertEquals(setOf(0), ReminderSettingsStore(store).preferences.first().leadDaysSet); assertEquals("DARK", store.data.first()[theme])
    }
    @Test fun `production fallback is explicit and late durable choice replaces it`() = runTest {
        val source = MutableSharedFlow<AppTheme>()
        val states = mutableListOf<AppearanceBootstrapState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeAppearanceBootstrap(source, fallbackTheme = AppTheme.DARK).toList(states) }
        advanceTimeBy(1999); runCurrent(); assertEquals(listOf(AppearanceBootstrapState.Loading), states)
        advanceTimeBy(1); runCurrent(); assertEquals(AppearanceBootstrapState.Ready(AppTheme.DARK, true), states.last())
        assertFalse(canComposeLocalApp(states.last(), false))
        source.emit(AppTheme.LIGHT); runCurrent(); assertEquals(AppearanceBootstrapState.Ready(AppTheme.LIGHT), states.last())
        assertTrue(canComposeLocalApp(states.last(), true))
    }
}
