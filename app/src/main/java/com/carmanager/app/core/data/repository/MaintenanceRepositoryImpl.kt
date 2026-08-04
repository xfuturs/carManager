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
import javax.inject.Inject

class MaintenanceRepositoryImpl @Inject constructor(
    private val maintenanceDao: MaintenanceDao
) : MaintenanceRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<MaintenanceRecord>> {
        return maintenanceDao.observeByVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAll(): Flow<List<MaintenanceRecord>> {
        return maintenanceDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeNextUpcoming(): Flow<MaintenanceRecord?> {
        val now = System.currentTimeMillis()
        return maintenanceDao.observeNextUpcoming(now).map { it?.toDomain() }
    }

    override fun observeMonthlyTotal(timestamp: Long): Flow<Double> {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val startOfMonth = date.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return maintenanceDao.observeMonthlyTotal(startOfMonth, endOfMonth)
    }

    override suspend fun saveMaintenanceRecord(record: MaintenanceRecord): Long {
        return maintenanceDao.insert(record.toEntity())
    }

    override suspend fun deleteMaintenanceRecord(record: MaintenanceRecord) {
        maintenanceDao.delete(record.toEntity())
    }
}
