package com.carmanager.app.core.data.repository

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.carmanager.app.core.domain.model.AppearancePolicy
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl internal constructor(
    private val store: DataStore<Preferences>, private val systemDark: () -> Boolean
) : SettingsRepository {
    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore, {
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    })

    private val THEME_KEY = stringPreferencesKey("theme_preference")
    private val CURRENCY_KEY = stringPreferencesKey("currency_preference")
    private val DISTANCE_UNIT_KEY = stringPreferencesKey("distance_unit_preference")

    override val themePreference: Flow<AppTheme> = store.data
        .transform { preferences ->
            val stored = preferences[THEME_KEY]
            val explicit = AppearancePolicy.resolve(stored, systemDark())
            if (stored != explicit.name) {
                // Relire dans edit : ne jamais écraser un clic Jour/Nuit arrivé pendant la migration.
                val updated = store.edit { current ->
                    val currentName = current[THEME_KEY]
                    if (currentName != AppTheme.LIGHT.name && currentName != AppTheme.DARK.name)
                        current[THEME_KEY] = explicit.name
                }
                emit(AppearancePolicy.resolve(updated[THEME_KEY], systemDark()))
            } else emit(explicit)
        }

    override suspend fun setThemePreference(theme: AppTheme) {
        val name = AppearancePolicy.storedName(theme)
        store.edit { preferences ->
            preferences[THEME_KEY] = name
        }
    }

    override suspend fun toggleTheme() {
        store.edit { preferences ->
            preferences[THEME_KEY] = AppearancePolicy.toggle(AppearancePolicy.resolve(preferences[THEME_KEY], systemDark())).name
        }
    }

    override val currency: Flow<String> = store.data
        .map { preferences ->
            preferences[CURRENCY_KEY] ?: "€"
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit("€")
        }

    override suspend fun setCurrency(currency: String) {
        store.edit { preferences ->
            preferences[CURRENCY_KEY] = currency
        }
    }

    override val distanceUnit: Flow<String> = store.data
        .map { preferences ->
            preferences[DISTANCE_UNIT_KEY] ?: "km"
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit("km")
        }

    override suspend fun setDistanceUnit(unit: String) {
        store.edit { preferences ->
            preferences[DISTANCE_UNIT_KEY] = unit
        }
    }
}
