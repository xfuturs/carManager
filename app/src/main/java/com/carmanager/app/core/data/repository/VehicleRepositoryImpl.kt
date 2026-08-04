package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao
) : VehicleRepository {

    override fun observeAll(): Flow<List<Vehicle>> {
        return vehicleDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeById(id: Long): Flow<Vehicle?> {
        return vehicleDao.observeById(id).map { it?.toDomain() }
    }

    override fun observeCount(): Flow<Int> {
        return vehicleDao.observeCount()
    }

    override suspend fun saveVehicle(vehicle: Vehicle): Long {
        return if (vehicle.id == 0L) {
            vehicleDao.insert(vehicle.toEntity())
        } else {
            vehicleDao.update(vehicle.toEntity())
            vehicle.id
        }
    }

    override suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle.toEntity())
    }
}
