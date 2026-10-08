package com.carmanager.app.core.ads

import com.carmanager.app.core.ui.navigation.InterstitialNavigationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État de présentation indépendant du SDK, fermé par défaut. */
internal data class InterstitialPresentability(
    val route: String? = null,
    val hostResumed: Boolean = false,
    val destinationResumed: Boolean = false,
    val windowFocused: Boolean = false,
    val imeVisible: Boolean = true,
    val modalActive: Boolean = false,
    val blockingFlow: Boolean = false
) {
    // Un dialogue de l'app est interactif mais ne constitue jamais une surface publicitaire.
    val interactiveForeground: Boolean get() = hostResumed && destinationResumed && (windowFocused || modalActive)
    fun blocker(): InterstitialDecision? = when {
        blockingFlow -> InterstitialDecision.DueButBlockingFlow
        modalActive -> InterstitialDecision.DueButModalActive
        !hostResumed || !windowFocused -> InterstitialDecision.DueButActivityUnavailable
        !destinationResumed -> InterstitialDecision.DueButTransitionActive
        !InterstitialNavigationPolicy.isSafeRoute(route) -> InterstitialDecision.DueButUnsafeRoute
        imeVisible -> InterstitialDecision.DueButImeVisible
        else -> null
    }
}

/** Jetons appartenant uniquement au shell Compose courant, sans contexte Android. */
internal class InterstitialModalState {
    private val tokens = mutableSetOf<Any>()
    private val _blocked = MutableStateFlow(false)
    val blocked = _blocked.asStateFlow()
    fun acquire(token: Any) { tokens += token; _blocked.value = tokens.isNotEmpty() }
    fun release(token: Any) { tokens -= token; _blocked.value = tokens.isNotEmpty() }
}
