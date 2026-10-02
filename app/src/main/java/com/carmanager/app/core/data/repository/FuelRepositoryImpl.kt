package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.FuelRecordDao
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.repository.FuelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import javax.inject.Inject

class FuelRepositoryImpl @Inject constructor(
    private val fuelRecordDao: FuelRecordDao,
    private val session: WorkspaceSession,
    private val access: OwnedDatabaseAccess,
    private val writer: com.carmanager.app.core.data.local.LocalGarageWriter
) : FuelRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<FuelRecord>> {
        return session.observe { owner -> fuelRecordDao.observeByVehicle(vehicleId, owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override fun observeAll(): Flow<List<FuelRecord>> {
        return session.observe { owner -> fuelRecordDao.observeAll(owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override fun observeMonthlyTotal(timestamp: Long): Flow<Double> {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val startOfMonth = date.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return session.observe { owner -> fuelRecordDao.observeMonthlyTotal(startOfMonth, endOfMonth, owner) }
    }

    override suspend fun saveFuelRecord(record: FuelRecord): Long {
        if (record.id == 0L) return writer.saveFuel(record)
        com.carmanager.app.core.domain.validation.GarageValidation.fuel(record)

        return access.write(record.ownerKey, record.vehicleId) {
            if (record.id != 0L) check(fuelRecordDao.getById(record.id, record.ownerKey) != null) { "Enregistrement absent de cet espace." }
            fuelRecordDao.insert(record.toEntity())
        }
    }

    override suspend fun deleteFuelRecord(record: FuelRecord) {
        access.write(record.ownerKey, record.vehicleId) {
            check(fuelRecordDao.getById(record.id, record.ownerKey) != null) { "Enregistrement absent de cet espace." }
            fuelRecordDao.delete(record.toEntity())
        }
    }
}
