package com.carmanager.app.core.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import com.carmanager.app.core.ads.InterstitialAdManager
import com.carmanager.app.core.ads.InterstitialModalState
import com.carmanager.app.core.ads.InterstitialPresentability
import com.carmanager.app.core.ads.InterstitialScheduler

/** Unique timer du shell ; les événements de sûreté/SDK interrompent et recalculent l'échéance. */
@OptIn(ExperimentalLayoutApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
internal fun ObserveTimedInterstitials(
    navController: NavHostController,
    activity: ComponentActivity,
    manager: InterstitialAdManager,
    modals: InterstitialModalState
) {
    val window = LocalWindowInfo.current
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val currentState by rememberUpdatedState({
        val entry = navController.currentBackStackEntry
        InterstitialPresentability(
            route = entry?.destination?.route,
            hostResumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            destinationResumed = entry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
            windowFocused = window.isWindowFocused,
            imeVisible = imeVisible,
            modalActive = modals.blocked.value
        )
    })
    LaunchedEffect(navController, activity, manager, modals) {
        try {
            navController.currentBackStackEntryFlow.flatMapLatest { entry ->
                combine(entry.lifecycle.currentStateFlow, activity.lifecycle.currentStateFlow,
                    snapshotFlow { window.isWindowFocused to imeVisible }, modals.blocked,
                    manager.changes.onStart { emit(Unit) }) { _, _, _, _, _ -> Unit }
            }.collectLatest {
                try {
                    InterstitialScheduler().run(
                        evaluate = {
                            // Laisser la composition fermer les modales et terminer la transition.
                            withFrameNanos { }
                            val state = currentState()
                            manager.updateForeground(state)
                            manager.onTimedOpportunity(activity, state)
                        },
                        nextDelay = manager::nextWakeDelay
                    )
                } finally { manager.pauseForeground() }
            }
        } finally {
            // Le job et ses captures Activity appartiennent seulement à cette composition.
            manager.pauseForeground()
        }
    }
}
