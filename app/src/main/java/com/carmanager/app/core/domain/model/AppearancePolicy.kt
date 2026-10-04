package com.carmanager.app.core.domain.model

import com.carmanager.app.core.domain.repository.AppTheme

object AppearancePolicy {
    fun resolve(stored: String?, systemDark: Boolean): AppTheme = when (stored) {
        AppTheme.LIGHT.name -> AppTheme.LIGHT
        AppTheme.DARK.name -> AppTheme.DARK
        else -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
    }
    fun storedName(theme: AppTheme): String {
        require(theme != AppTheme.SYSTEM) { "Choisissez Jour ou Nuit." }
        return theme.name
    }
    fun toggle(theme: AppTheme): AppTheme = when (theme) {
        AppTheme.LIGHT -> AppTheme.DARK
        AppTheme.DARK -> AppTheme.LIGHT
        AppTheme.SYSTEM -> error("L’apparence doit être résolue avant le changement.")
    }
}
