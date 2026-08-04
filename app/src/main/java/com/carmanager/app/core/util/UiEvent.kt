package com.carmanager.app.core.util

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data object Success : UiEvent()
}
