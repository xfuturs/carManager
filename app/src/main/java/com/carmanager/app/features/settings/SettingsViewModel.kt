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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val premiumRepository: PremiumRepository,
    private val deletion: com.carmanager.app.core.domain.session.AccountDeletion,
    session: com.carmanager.app.core.domain.session.WorkspaceSession,
    registry: com.carmanager.app.core.domain.session.DeletionRegistry,
    private val reminders: com.carmanager.app.core.data.repository.ReminderSettingsStore? = null
) : ViewModel() {
    @Inject constructor(reminders: com.carmanager.app.core.data.repository.ReminderSettingsStore,
        settings: SettingsRepository, auth: AuthRepository, premium: PremiumRepository,
        deletion: com.carmanager.app.core.domain.session.AccountDeletion,
        session: com.carmanager.app.core.domain.session.WorkspaceSession, registry: com.carmanager.app.core.domain.session.DeletionRegistry)
        : this(settings, auth, premium, deletion, session, registry, reminders)

    val themePreference: StateFlow<AppTheme> = settingsRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.LIGHT)

    private val _preferenceEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val preferenceEvents = _preferenceEvents.receiveAsFlow()
    val reminderPreferences = (reminders?.preferences ?: kotlinx.coroutines.flow.flowOf(com.carmanager.app.core.domain.model.ReminderPreferences()))
        .map<com.carmanager.app.core.domain.model.ReminderPreferences, com.carmanager.app.core.domain.model.ReminderPreferences?> { it }
        .catch { error ->
            if (error is CancellationException) throw error
            _preferenceEvents.send("Réglages des rappels indisponibles. Rouvrez les paramètres.")
            emit(null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setRemindersEnabled(enabled: Boolean) = updateReminders { it.copy(enabled = enabled) }
    fun setReminderCategory(category: com.carmanager.app.core.domain.model.ReminderCategory, enabled: Boolean) = updateReminders { it.withCategory(category, enabled) }
    fun setReminderLead(days: Int) = updateReminders { it.toggleLead(days) }
    private fun updateReminders(change: (com.carmanager.app.core.domain.model.ReminderPreferences) -> com.carmanager.app.core.domain.model.ReminderPreferences) {
        viewModelScope.launch {
            try { checkNotNull(reminders).update(change) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _preferenceEvents.send("Réglage du rappel non enregistré. Réessayez.")
            }
        }
    }

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
        if (theme == AppTheme.SYSTEM) return
        viewModelScope.launch {
            try { settingsRepository.setThemePreference(theme) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _preferenceEvents.send("Apparence non enregistrée. Réessayez.")
            }
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
