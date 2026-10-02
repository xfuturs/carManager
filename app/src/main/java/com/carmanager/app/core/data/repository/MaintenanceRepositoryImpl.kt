package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.MaintenanceDao
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import javax.inject.Inject

class MaintenanceRepositoryImpl @Inject constructor(
    private val maintenanceDao: MaintenanceDao,
    private val session: WorkspaceSession,
    private val access: OwnedDatabaseAccess,
    private val writer: com.carmanager.app.core.data.local.LocalGarageWriter
) : MaintenanceRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<MaintenanceRecord>> {
        return session.observe { owner -> maintenanceDao.observeByVehicle(vehicleId, owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override fun observeAll(): Flow<List<MaintenanceRecord>> {
        return session.observe { owner -> maintenanceDao.observeAll(owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override fun observeNextUpcoming(): Flow<MaintenanceRecord?> {
        val now = System.currentTimeMillis()
        return session.observe<MaintenanceRecord?> { owner -> maintenanceDao.observeNextUpcoming(now, owner).map { it?.toDomain(owner) } }
    }

    override fun observeMonthlyTotal(timestamp: Long): Flow<Double> {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val startOfMonth = date.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return session.observe { owner -> maintenanceDao.observeMonthlyTotal(startOfMonth, endOfMonth, owner) }
    }

    override suspend fun saveMaintenanceRecord(record: MaintenanceRecord): Long {
        if (record.id == 0L) return writer.saveMaintenance(record)
        com.carmanager.app.core.domain.validation.GarageValidation.maintenance(record)

        return access.write(record.ownerKey, record.vehicleId) {
            if (record.id != 0L) check(maintenanceDao.getById(record.id, record.ownerKey) != null) { "Enregistrement absent de cet espace." }
            maintenanceDao.insert(record.toEntity())
        }
    }

    override suspend fun deleteMaintenanceRecord(record: MaintenanceRecord) {
        access.write(record.ownerKey, record.vehicleId) {
            check(maintenanceDao.getById(record.id, record.ownerKey) != null) { "Enregistrement absent de cet espace." }
            maintenanceDao.delete(record.toEntity())
        }
    }
}
