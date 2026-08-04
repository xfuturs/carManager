package com.carmanager.app.core.domain.repository

import com.carmanager.app.core.domain.model.MileageRecord
import kotlinx.coroutines.flow.Flow

interface MileageRepository {
    fun observeByVehicle(vehicleId: Long): Flow<List<MileageRecord>>
    suspend fun saveRecord(record: MileageRecord): Long
    suspend fun getLatestMileage(vehicleId: Long): Int?
}
