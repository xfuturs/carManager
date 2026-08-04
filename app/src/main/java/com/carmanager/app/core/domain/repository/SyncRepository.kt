package com.carmanager.app.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface gérant la synchronisation entre la base de données locale (Room) 
 * et le stockage Cloud (Firestore).
 */
interface SyncRepository {
    /**
     * Lance le processus de synchronisation.
     */
    suspend fun syncAll()

    /**
     * Active la synchronisation automatique en temps réel.
     */
    fun startAutoSync()

    /**
     * Flux indiquant si une synchronisation est en cours.
     */
    val isSyncing: StateFlow<Boolean>
}
