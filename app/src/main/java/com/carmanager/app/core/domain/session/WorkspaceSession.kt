package com.carmanager.app.core.domain.session

import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

object WorkspaceOwner {
    const val GUEST = "guest:local"
    fun fromUid(uid: String?): String = if (uid == null) GUEST else "firebase:${uid.also { require(it.isNotBlank()) }}"
    fun uid(owner: String): String? = owner.takeIf { it.startsWith("firebase:") }?.removePrefix("firebase:")?.takeIf { it.isNotBlank() }
}

interface DeletionRegistry {
    val blockedOwners: StateFlow<Set<String>>
    suspend fun block(owner: String)
}

/** Alimenté exclusivement par le listener Firebase d'AuthRepositoryImpl. */
@Singleton
class WorkspaceSession @Inject constructor(private val deletionRegistry: DeletionRegistry) {
    private val _owner = MutableStateFlow(WorkspaceOwner.GUEST)
    val owner: StateFlow<String> = _owner.asStateFlow()
    private val _isResolved = MutableStateFlow(false)
    val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()
    fun setAuthenticatedUid(uid: String?) {
        _owner.value = WorkspaceOwner.fromUid(uid)
        _isResolved.value = true
    }
    fun requireCurrent(expected: String) {
        check(owner.value == expected) { "L'espace actif a changé. Rouvrez cet écran." }
    }
    fun requireWritable(expected: String) {
        requireCurrent(expected)
        check(expected !in deletionRegistry.blockedOwners.value) {
            "Suppression incomplète : modifications suspendues. Réessayez la suppression du compte."
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <T> observe(query: (String) -> Flow<T>): Flow<T> = owner.flatMapLatest { key ->
        query(key).filter { owner.value == key }
    }
}
