package com.carmanager.app.core.domain.session

import com.carmanager.app.core.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface AccountRemoteData {
    fun requireUid(uid: String)
    suspend fun drainWrites(uid: String)
    suspend fun deleteKnownData(uid: String)
    suspend fun deleteAuth(uid: String)
}

interface LocalAccountData { suspend fun purge(owner: String) }

enum class DeletionStage(val label: String) {
    STOP_SYNC("Préparation de la suppression"), REMOTE("Données cloud"), LOCAL("Données locales"), AUTH("Compte Firebase"), COMPLETE("Terminée")
}
data class DeletionState(val owner: String? = null, val stage: DeletionStage? = null, val running: Boolean = false, val error: String? = null)
class AccountDeletionFailure(val stage: DeletionStage, cause: Throwable, suspended: Boolean) : Exception(
    "Suppression incomplète à l'étape ${stage.label} : ${cause.localizedMessage ?: "erreur"}. Les étapes précédentes peuvent être définitives." +
        if (suspended) " Réessayez la suppression du même compte. Votre garage local reste conservé." else " Aucune suppression destructive n'a commencé.", cause
)

@Singleton
class AccountDeletion @Inject constructor(
    private val session: AuthSession,
    private val registry: DeletionRegistry,
    private val sync: SyncRepository,
    private val local: LocalAccountData,
    private val remote: AccountRemoteData
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(DeletionState())
    val state = _state.asStateFlow()

    fun acknowledgeResult() {
        if (!_state.value.running) _state.value = DeletionState()
    }

    suspend fun delete(owner: String): Result<Unit> = mutex.withLock {
        // Le travail sensible continue même si la navigation détruit le ViewModel.
        withContext(NonCancellable) {
            var stage = DeletionStage.STOP_SYNC
            try {
                val uid = WorkspaceOwner.uid(owner) ?: error("Aucun compte connecté")
                session.requireAccount(owner)
                remote.requireUid(uid)
                _state.value = DeletionState(owner, stage, running = true)
                registry.block(owner)
                sync.stopSync()
                stage = DeletionStage.REMOTE
                _state.value = DeletionState(owner, stage, running = true)
                remote.drainWrites(uid)
                remote.deleteKnownData(uid)
                // LOCAL est conservé pour compatibilité historique, mais aucune purge de garage ne reprend.
                session.requireAccount(owner)
                remote.requireUid(uid)
                stage = DeletionStage.AUTH
                _state.value = DeletionState(owner, stage, running = true)
                remote.deleteAuth(uid)
                _state.value = DeletionState(owner, DeletionStage.COMPLETE)
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                val failure = AccountDeletionFailure(stage, e, owner in registry.blockedOwners.value)
                _state.value = DeletionState(owner, stage, error = failure.message)
                Result.failure(failure)
            }
        }
    }
}
