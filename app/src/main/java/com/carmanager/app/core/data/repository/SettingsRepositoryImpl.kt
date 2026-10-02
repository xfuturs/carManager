package com.carmanager.app.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val THEME_KEY = stringPreferencesKey("theme_preference")
    private val CURRENCY_KEY = stringPreferencesKey("currency_preference")
    private val DISTANCE_UNIT_KEY = stringPreferencesKey("distance_unit_preference")

    override val themePreference: Flow<AppTheme> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
            try {
                AppTheme.valueOf(themeName)
            } catch (e: Exception) {
                AppTheme.SYSTEM
            }
        }

    override suspend fun setThemePreference(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    override val currency: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENCY_KEY] ?: "€"
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit("€")
        }

    override suspend fun setCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENCY_KEY] = currency
        }
    }

    override val distanceUnit: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DISTANCE_UNIT_KEY] ?: "km"
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit("km")
        }

    override suspend fun setDistanceUnit(unit: String) {
        context.dataStore.edit { preferences ->
            preferences[DISTANCE_UNIT_KEY] = unit
        }
    }
}
