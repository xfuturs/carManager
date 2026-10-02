package com.carmanager.app.features.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.domain.repository.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val premiumRepository: PremiumRepository,
    private val deletion: com.carmanager.app.core.domain.session.AccountDeletion,
    session: com.carmanager.app.core.domain.session.WorkspaceSession,
    registry: com.carmanager.app.core.domain.session.DeletionRegistry
) : ViewModel() {

    val themePreference: StateFlow<AppTheme> = settingsRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val currency: StateFlow<String> = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    val distanceUnit: StateFlow<String> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "km")

    val currentUser: StateFlow<User?> = authRepository.currentUser

    val isPremium: StateFlow<Boolean> = premiumRepository.isPremium
    val premiumState = premiumRepository.state

    fun purchasePremium(activity: Activity) = premiumRepository.launchPurchase(activity)
    fun refreshPremium() = premiumRepository.checkPremiumStatus()
    val deletionState = deletion.state
    private val _accountError = MutableStateFlow<String?>(null)
    val accountError = _accountError.asStateFlow()
    val deletionPending = combine(session.owner, registry.blockedOwners) { owner, blocked -> owner in blocked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), session.owner.value in registry.blockedOwners.value)

    fun signOut() {
        if (deletionState.value.running) return
        viewModelScope.launch {
            try {
                _accountError.value = null
                authRepository.signOut()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _accountError.value = e.message ?: "Déconnexion impossible."
            }
        }
    }

    fun deleteAccount() {
        if (deletionState.value.running) return
        viewModelScope.launch {
            _accountError.value = null
            authRepository.deleteAccount().onFailure { error ->
                // Les phases destructives publient déjà leur résultat dans deletionState.
                if (deletionState.value.error == null) _accountError.value = error.message ?: "Suppression impossible."
            }
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
