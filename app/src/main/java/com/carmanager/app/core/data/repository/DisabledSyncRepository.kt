package com.carmanager.app.core.data.repository

import com.carmanager.app.core.domain.repository.GarageSyncStatus
import com.carmanager.app.core.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Sauvegarde cloud inactive : aucun client réseau, observateur Room ou tâche de fond. */
@Singleton
class DisabledSyncRepository @Inject constructor() : SyncRepository {
    override val status = MutableStateFlow(GarageSyncStatus.DISABLED).asStateFlow()
    override val syncError = MutableStateFlow<String?>(null).asStateFlow()
    override val isSyncing = MutableStateFlow(false).asStateFlow()
    override suspend fun syncAll(): Result<Unit> = Result.failure(
        IllegalStateException("La sauvegarde cloud du garage est inactive. Les données restent sur cet appareil.")
    )
    override fun startAutoSync() = Unit
    override suspend fun stopSync() = Unit
}
