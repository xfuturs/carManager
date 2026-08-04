package com.carmanager.app.core.domain.repository

import kotlinx.coroutines.flow.Flow

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

interface SettingsRepository {
    val themePreference: Flow<AppTheme>
    suspend fun setThemePreference(theme: AppTheme)
    
    val currency: Flow<String>
    suspend fun setCurrency(currency: String)
    
    val distanceUnit: Flow<String>
    suspend fun setDistanceUnit(unit: String)
}
