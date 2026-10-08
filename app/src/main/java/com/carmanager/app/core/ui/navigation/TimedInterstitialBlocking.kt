package com.carmanager.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.carmanager.app.core.ads.InterstitialModalState

internal val LocalInterstitialModals = staticCompositionLocalOf<InterstitialModalState?> { null }

/** Bloque dès le clic d'ouverture ; la disparition de son propriétaire libère le jeton. */
@Composable
internal fun rememberTimedInterstitialBlocker(): (Boolean) -> Unit {
    val modals = LocalInterstitialModals.current
    val token = remember { Any() }
    DisposableEffect(modals, token) {
        onDispose { modals?.release(token) }
    }
    return remember(modals, token) { { active ->
        if (active) modals?.acquire(token) else modals?.release(token)
        Unit
    } }
}
