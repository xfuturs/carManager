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
import javax.inject.Inject

class FuelRepositoryImpl @Inject constructor(
    private val fuelRecordDao: FuelRecordDao
) : FuelRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<FuelRecord>> {
        return fuelRecordDao.observeByVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAll(): Flow<List<FuelRecord>> {
        return fuelRecordDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeMonthlyTotal(timestamp: Long): Flow<Double> {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val startOfMonth = date.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return fuelRecordDao.observeMonthlyTotal(startOfMonth, endOfMonth)
    }

    override suspend fun saveFuelRecord(record: FuelRecord): Long {
        return fuelRecordDao.insert(record.toEntity())
    }

    override suspend fun deleteFuelRecord(record: FuelRecord) {
        fuelRecordDao.delete(record.toEntity())
    }
}
