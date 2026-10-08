package com.carmanager.app.core.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Identité pour le compte et son nettoyage distant, indépendante du garage. */
@Singleton
class AuthSession @Inject constructor() {
    private val currentUid = MutableStateFlow<String?>(null)
    val uid = currentUid.asStateFlow()
    fun setUid(value: String?) { require(value == null || value.isNotBlank()); currentUid.value = value }
    fun requireAccount(owner: String) {
        check(uid.value != null && WorkspaceOwner.fromUid(uid.value) == owner) { "Le compte connecté a changé." }
    }
}
