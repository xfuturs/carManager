package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.entity.*
import com.carmanager.app.core.domain.session.WorkspaceOwner
import com.carmanager.app.core.domain.session.WorkspaceSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface SyncRemoteWriter { suspend fun write(uid: String, collection: String, id: String, data: Any) }
data class OwnerSyncSnapshot(val vehicles: List<VehicleEntity>, val fuel: List<FuelRecordEntity>, val maintenance: List<MaintenanceRecordEntity>)

/** Garde-fou conservé pour une future sauvegarde ; absent du graphe DI actif. */
class OwnerSyncUploader(private val session: WorkspaceSession, private val writer: SyncRemoteWriter) {
    suspend fun upload(owner: String, snapshot: OwnerSyncSnapshot) {
        val uid = WorkspaceOwner.uid(owner) ?: return
        session.requireWritable(owner)
        check(snapshot.vehicles.all { it.ownerKey == owner }) { "Snapshot d'un autre propriétaire refusé." }
        val ids = snapshot.vehicles.map { it.id }.toSet()
        check(snapshot.fuel.all { it.vehicleId in ids } && snapshot.maintenance.all { it.vehicleId in ids }) { "Enregistrement hors de l'espace refusé." }
        suspend fun send(collection: String, id: Long, value: Any) {
            currentCoroutineContext().ensureActive()
            session.requireWritable(owner)
            writer.write(uid, collection, id.toString(), value)
            session.requireWritable(owner)
        }
        snapshot.vehicles.forEach { send("vehicles", it.id, it) }
        snapshot.fuel.forEach { send("fuel_records", it.id, it) }
        snapshot.maintenance.forEach { send("maintenance_records", it.id, it) }
    }
}
