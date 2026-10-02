package com.carmanager.app.core.ui.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Conservé par l'Activity, hors du store du garage et de la composition. */
@HiltViewModel
class StartupViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val appearance = observeAppearanceBootstrap(settings.themePreference, onFallback = {
        Log.w("Startup", "Préférence d'apparence indisponible : mode système temporaire.")
    }).stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceBootstrapState.Loading)
}
