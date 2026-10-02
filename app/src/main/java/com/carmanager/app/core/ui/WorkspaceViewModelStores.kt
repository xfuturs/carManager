package com.carmanager.app.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Conservé par l'activité pendant une recréation, vidé uniquement au changement d'espace. */
class WorkspaceViewModelStores : ViewModel() {
    private var key: String? = null
    private var current = createOwner()

    fun forOwner(owner: String): ViewModelStoreOwner {
        if (key != owner) {
            current.viewModelStore.clear()
            current = createOwner()
            key = owner
        }
        return current
    }

    private fun createOwner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    override fun onCleared() { current.viewModelStore.clear() }
}
