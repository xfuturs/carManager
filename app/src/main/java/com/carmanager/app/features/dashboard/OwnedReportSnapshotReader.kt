package com.carmanager.app.features.dashboard

import com.carmanager.app.core.data.local.CarManagerDatabase
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.domain.session.WorkspaceSession
import javax.inject.Inject

/** Les quatre sources sont lues sous la même transaction ; aucun IO de fichier/rendu ici. */
class OwnedReportSnapshotReader internal constructor(
    private val session: WorkspaceSession,
    private val snapshot: suspend (String, Long) -> GenerateVehicleReportUseCase.ReportData?
) {
    @Inject constructor(session: WorkspaceSession, database: CarManagerDatabase, access: OwnedDatabaseAccess) :
        this(session, { owner, id -> access.read(owner, id) {
            val vehicle = database.vehicleDao().getById(id, owner)?.toDomain()
            vehicle?.let { GenerateVehicleReportUseCase.ReportData(it,
                database.fuelRecordDao().getByVehicle(id, owner).map { row -> row.toDomain(owner) },
                database.maintenanceDao().getByVehicle(id, owner).map { row -> row.toDomain(owner) },
                database.mileageDao().getByVehicle(id, owner).map { row -> row.toDomain(owner) }) }
        } })
    suspend fun read(vehicleId: Long): GenerateVehicleReportUseCase.ReportData? {
        val owner = session.owner.value
        session.requireWritable(owner)
        val data = snapshot(owner, vehicleId)
        session.requireWritable(owner)
        if (data != null) check(data.vehicle.ownerKey == owner && data.vehicle.id == vehicleId &&
            data.fuelRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.maintenanceRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.mileageRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId }) { "Données de rapport hors espace actif." }
        return data
    }
}
