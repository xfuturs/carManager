package com.carmanager.app.core.domain.repository

import com.carmanager.app.core.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeAll(): Flow<List<Vehicle>>
    fun observeById(id: Long): Flow<Vehicle?>
    fun observeCount(): Flow<Int>
    suspend fun saveVehicle(vehicle: Vehicle): Long
    suspend fun deleteVehicle(vehicle: Vehicle)
}
