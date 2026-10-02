package com.carmanager.app.core.ui.startup

import com.carmanager.app.core.domain.repository.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AppearanceBootstrapState {
    data object Loading : AppearanceBootstrapState
    data class Ready(val theme: AppTheme, val fallback: Boolean = false) : AppearanceBootstrapState
}

fun canComposeLocalApp(appearance: AppearanceBootstrapState, workspaceResolved: Boolean): Boolean =
    appearance is AppearanceBootstrapState.Ready && workspaceResolved

fun AppTheme.usesDarkColors(systemDark: Boolean): Boolean = when (this) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM -> systemDark
}

/** Un seul lecteur DataStore ; le delai est une borne d'echec, jamais une duree minimale. */
fun observeAppearanceBootstrap(
    preferences: Flow<AppTheme>,
    timeoutMillis: Long = 2_000,
    onFallback: () -> Unit = {}
): Flow<AppearanceBootstrapState> = channelFlow {
    send(AppearanceBootstrapState.Loading)
    val first = CompletableDeferred<Unit>()
    val watchdog = launch {
        if (withTimeoutOrNull(timeoutMillis) { first.await() } == null) {
            onFallback()
            send(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true))
        }
    }
    try {
        preferences.collect { theme ->
            first.complete(Unit)
            watchdog.cancel()
            send(AppearanceBootstrapState.Ready(theme))
        }
        if (!first.isCompleted) {
            onFallback()
            send(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true))
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        onFallback()
        send(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true))
    } finally {
        watchdog.cancel()
    }
}.distinctUntilChanged()
