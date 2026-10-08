package com.carmanager.app.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BannerRequestState { CREATED, REQUESTED, LOADED, FAILED, DISPOSED }

/** Un seul chargement applicatif par vue/configuration ; le refresh SDK reste gere par Google. */
class BannerRequestLifecycle {
    private var active: Boolean? = null
    private val _state = MutableStateFlow(BannerRequestState.CREATED)
    val state = _state.asStateFlow()

    fun requestOnce(load: () -> Unit) {
        if (_state.value != BannerRequestState.CREATED) return
        _state.value = BannerRequestState.REQUESTED
        load()
    }

    fun loaded() {
        if (_state.value != BannerRequestState.DISPOSED) _state.value = BannerRequestState.LOADED
    }

    fun failed() {
        if (_state.value != BannerRequestState.DISPOSED) _state.value = BannerRequestState.FAILED
    }

    fun updateActivity(visible: Boolean, resumed: Boolean, pause: () -> Unit, resume: () -> Unit) {
        if (_state.value == BannerRequestState.DISPOSED) return
        val next = visible && resumed
        if (active == next) return
        active = next
        if (next) resume() else pause()
    }

    fun dispose(destroy: () -> Unit) {
        if (_state.value == BannerRequestState.DISPOSED) return
        _state.value = BannerRequestState.DISPOSED
        destroy()
    }
}

/** Retenir l'unique host après la première route autorisée, jusqu'à révocation Ads. */
class BannerHostRetention {
    private val _retained = MutableStateFlow(false)
    val retained = _retained.asStateFlow()
    fun update(adsAllowed: Boolean, routeEligible: Boolean) {
        _retained.value = adsAllowed && (_retained.value || routeEligible)
    }
}
