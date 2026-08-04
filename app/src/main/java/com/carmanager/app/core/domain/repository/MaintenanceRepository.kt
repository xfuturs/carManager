package com.carmanager.app.core.domain.repository

import com.carmanager.app.core.domain.model.MaintenanceRecord
import kotlinx.coroutines.flow.Flow

interface MaintenanceRepository {
    fun observeByVehicle(vehicleId: Long): Flow<List<MaintenanceRecord>>
    fun observeAll(): Flow<List<MaintenanceRecord>>
    fun observeNextUpcoming(): Flow<MaintenanceRecord?>
    fun observeMonthlyTotal(timestamp: Long): Flow<Double>
    suspend fun saveMaintenanceRecord(record: MaintenanceRecord): Long
    suspend fun deleteMaintenanceRecord(record: MaintenanceRecord)
}
