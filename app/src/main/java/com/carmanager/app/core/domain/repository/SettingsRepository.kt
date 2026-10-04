package com.carmanager.app.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.carmanager.app.core.domain.model.AppearancePolicy

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

interface SettingsRepository {
    val themePreference: Flow<AppTheme>
    suspend fun setThemePreference(theme: AppTheme)
    suspend fun toggleTheme() = setThemePreference(AppearancePolicy.toggle(themePreference.first()))
    
    val currency: Flow<String>
    suspend fun setCurrency(currency: String)
    
    val distanceUnit: Flow<String>
    suspend fun setDistanceUnit(unit: String)
}
