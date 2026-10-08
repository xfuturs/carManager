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

enum class GarageReadiness { Loading, Ready, Error }

/** Session du garage uniquement ; la résolution dépend de la consolidation locale, jamais d'Auth. */
@Singleton
class WorkspaceSession @Inject constructor(private val deletionRegistry: DeletionRegistry) {
    private val _owner = MutableStateFlow(LocalGarageOwner.KEY)
    val owner: StateFlow<String> = _owner.asStateFlow()
    private val _isResolved = MutableStateFlow(false)
    val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()
    private val _readiness = MutableStateFlow(GarageReadiness.Loading)
    val readiness = _readiness.asStateFlow()
    internal fun beginBootstrap() { _isResolved.value = false; _readiness.value = GarageReadiness.Loading }
    internal fun completeBootstrap() {
        _readiness.value = GarageReadiness.Ready
        _isResolved.value = true
    }
    internal fun failBootstrap() { _isResolved.value = false; _readiness.value = GarageReadiness.Error }
    fun requireCurrent(expected: String) {
        check(expected == LocalGarageOwner.KEY && isResolved.value) { "Le garage local est indisponible. Rouvrez cet écran." }
    }
    fun requireWritable(expected: String) {
        requireCurrent(expected)
        check(expected !in deletionRegistry.blockedOwners.value) {
            "Suppression incomplète : modifications suspendues. Réessayez la suppression du compte."
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <T> observe(query: (String) -> Flow<T>): Flow<T> = isResolved.flatMapLatest { ready ->
        if (ready) query(LocalGarageOwner.KEY).filter { isResolved.value } else emptyFlow()
    }
}
