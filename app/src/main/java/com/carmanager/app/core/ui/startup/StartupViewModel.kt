package com.carmanager.app.core.ui.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import com.carmanager.app.core.domain.model.AppearancePolicy
import com.carmanager.app.core.domain.repository.AppTheme

/** Conservé par l'Activity, hors du store du garage et de la composition. */
@HiltViewModel
class StartupViewModel internal constructor(settings: SettingsRepository, fallback: AppTheme = AppTheme.SYSTEM) : ViewModel() {
    @Inject constructor(settings: SettingsRepository, @ApplicationContext context: Context) : this(settings,
        AppearancePolicy.resolve(null, context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES))
    val appearance = observeAppearanceBootstrap(settings.themePreference, onFallback = {
        Log.w("Startup", "Préférence d'apparence indisponible : apparence explicite temporaire.")
    }, fallbackTheme = fallback).stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceBootstrapState.Loading)
}
