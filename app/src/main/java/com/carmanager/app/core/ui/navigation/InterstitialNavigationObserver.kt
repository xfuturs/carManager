package com.carmanager.app.core.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Observe uniquement la navigation ; jamais un timer, resume ou recomposition pour presenter. */
@Composable
internal fun ObserveInterstitialOpportunities(
    navController: NavHostController,
    activity: ComponentActivity,
    onOpportunity: () -> Unit
) {
    val currentOpportunity by rememberUpdatedState(onOpportunity)
    LaunchedEffect(navController, activity) {
        // Laisser la restauration initiale du graphe se terminer, sans retenir sa premiere frame.
        withFrameNanos { }
        val policy = InterstitialNavigationPolicy()
        navController.currentBackStackEntryFlow.collectLatest { entry ->
            if (!policy.onDestination(entry.destination.route)) return@collectLatest
            // Attendre seulement la fin de transition NavHost, pas un chargement d'annonce.
            // Toute pause du host invalide cette opportunite, sans reprise au prochain resume.
            val stable = withTimeoutOrNull(1_000L) {
                combine(entry.lifecycle.currentStateFlow, activity.lifecycle.currentStateFlow) { destination, host ->
                    when {
                        !host.isAtLeast(Lifecycle.State.RESUMED) -> false
                        destination.isAtLeast(Lifecycle.State.RESUMED) -> true
                        else -> null
                    }
                }.first { it != null }
            }
            if (stable != true) return@collectLatest
            withFrameNanos { }
            if (navController.currentBackStackEntry === entry &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentOpportunity()
        }
    }
}
