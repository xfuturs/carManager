package com.carmanager.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.domain.repository.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val themePreference: StateFlow<AppTheme> = settingsRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val currency: StateFlow<String> = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    val distanceUnit: StateFlow<String> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "km")

    val currentUser: StateFlow<User?> = authRepository.currentUser

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.deleteAccount()
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setThemePreference(theme)
        }
    }

    fun setCurrency(newCurrency: String) {
        viewModelScope.launch {
            settingsRepository.setCurrency(newCurrency)
        }
    }

    fun setDistanceUnit(newUnit: String) {
        viewModelScope.launch {
            settingsRepository.setDistanceUnit(newUnit)
        }
    }
}
