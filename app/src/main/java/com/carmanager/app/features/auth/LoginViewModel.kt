package com.carmanager.app.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onGoogleSignInError(message: String) {
        viewModelScope.launch { _uiEvent.send(UiEvent.ShowSnackbar(message)) }
    }

    fun onGoogleSignIn(idToken: String) {
        if (isLoading) return
        if (idToken.isBlank()) {
            onGoogleSignInError("Connexion Google impossible. Réessayez.")
            return
        }
        isLoading = true
        viewModelScope.launch {
            try {
                val result = authRepository.signInWithGoogle(idToken)
                _uiEvent.send(if (result.isSuccess) UiEvent.Success else UiEvent.ShowSnackbar(
                    result.exceptionOrNull()?.localizedMessage ?: "Connexion Google impossible. Réessayez."
                ))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiEvent.send(UiEvent.ShowSnackbar(e.localizedMessage ?: "Connexion Google impossible. Réessayez."))
            } finally {
                isLoading = false
            }
        }
    }
}
