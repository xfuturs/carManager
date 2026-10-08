package com.carmanager.app.core.data.local

import androidx.room.withTransaction
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.domain.session.WorkspaceSession
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** La DB décide à chaque démarrage ; aucun flag ne peut masquer des lignes legacy restantes. */
@Singleton
class LocalGarageBootstrap internal constructor(
    private val session: WorkspaceSession,
    private val consolidate: suspend () -> Unit
) {
    @Inject constructor(session: WorkspaceSession, database: CarManagerDatabase, reminders: ReminderSettingsStore) :
        this(session, {
            GarageOwnershipConsolidation(database.vehicleDao(), database.maintenanceDao(),
                { keys -> reminders.saveKeys(reminders.knownKeys() + keys) },
                { action -> database.withTransaction { action() } }).run()
        })

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Synchronized fun start() { scope.launch { initialize() } }
    suspend fun initialize() = mutex.withLock {
        if (session.isResolved.value) return@withLock
        session.beginBootstrap()
        try {
            consolidate()
            session.completeBootstrap()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            session.failBootstrap()
        }
    }
}
