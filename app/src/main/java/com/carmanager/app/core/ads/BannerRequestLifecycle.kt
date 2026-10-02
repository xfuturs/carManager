package com.carmanager.app.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BannerRequestState { CREATED, REQUESTED, LOADED, FAILED, DISPOSED }

/** Un seul chargement applicatif par vue/configuration ; le refresh SDK reste gere par Google. */
class BannerRequestLifecycle {
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

    fun dispose(destroy: () -> Unit) {
        if (_state.value == BannerRequestState.DISPOSED) return
        _state.value = BannerRequestState.DISPOSED
        destroy()
    }
}
