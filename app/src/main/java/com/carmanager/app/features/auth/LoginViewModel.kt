package com.carmanager.app.features.auth

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isSignUp by mutableStateOf(false)
        private set
    var isPrivacyAccepted by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onEmailChange(value: String) { email = value }
    fun onPasswordChange(value: String) { password = value }
    fun onPrivacyChange(value: Boolean) { isPrivacyAccepted = value }
    fun toggleMode() { isSignUp = !isSignUp }

    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            isLoading = true
            val result = authRepository.signInWithGoogle(idToken)
            isLoading = false
            if (result.isSuccess) {
                _uiEvent.send(UiEvent.Success)
            } else {
                _uiEvent.send(UiEvent.ShowSnackbar(result.exceptionOrNull()?.localizedMessage ?: "Erreur Google"))
            }
        }
    }

    fun onSubmit() {
        if (email.isBlank() || password.isBlank()) return
        if (isSignUp && !isPrivacyAccepted) {
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar("Veuillez accepter la politique de confidentialité."))
            }
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            val result = if (isSignUp) {
                authRepository.signUp(email, password)
            } else {
                authRepository.signIn(email, password)
            }
            
            isLoading = false
            if (result.isSuccess) {
                _uiEvent.send(UiEvent.Success)
            } else {
                _uiEvent.send(UiEvent.ShowSnackbar(result.exceptionOrNull()?.localizedMessage ?: "Erreur d'authentification"))
            }
        }
    }
}
