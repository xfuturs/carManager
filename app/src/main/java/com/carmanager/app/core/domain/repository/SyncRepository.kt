package com.carmanager.app.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class GarageSyncStatus { DISABLED }

/** Point d'extension pour une future sauvegarde optionnelle. Room reste la source locale. */
interface SyncRepository {
    val status: StateFlow<GarageSyncStatus>
    suspend fun syncAll(): Result<Unit>
    fun startAutoSync()
    suspend fun stopSync()
    val syncError: StateFlow<String?>
    val isSyncing: StateFlow<Boolean>
}
