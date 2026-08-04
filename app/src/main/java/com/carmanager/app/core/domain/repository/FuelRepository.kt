package com.carmanager.app.core.domain.repository

import com.carmanager.app.core.domain.model.FuelRecord
import kotlinx.coroutines.flow.Flow

interface FuelRepository {
    fun observeByVehicle(vehicleId: Long): Flow<List<FuelRecord>>
    fun observeAll(): Flow<List<FuelRecord>>
    fun observeMonthlyTotal(timestamp: Long): Flow<Double>
    suspend fun saveFuelRecord(record: FuelRecord): Long
    suspend fun deleteFuelRecord(record: FuelRecord)
}
