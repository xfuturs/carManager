package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val session: WorkspaceSession,
    private val access: OwnedDatabaseAccess,
    private val writer: com.carmanager.app.core.data.local.LocalGarageWriter,
    private val deletion: com.carmanager.app.core.data.local.VehicleFileDeletion? = null
) : VehicleRepository {

    override fun observeAll(): Flow<List<Vehicle>> {
        return session.observe { owner -> vehicleDao.observeAll(owner).map { entities ->
            entities.map { it.toDomain() }
        } }
    }

    override fun observeById(id: Long): Flow<Vehicle?> {
        return session.observe<Vehicle?> { owner -> vehicleDao.observeById(id, owner).map { it?.toDomain() } }
    }

    override fun observeCount(): Flow<Int> {
        return session.observe { owner -> vehicleDao.observeCount(owner) }
    }

    override suspend fun saveVehicle(vehicle: Vehicle): Long = writer.saveVehicle(vehicle)

    override suspend fun deleteVehicle(vehicle: Vehicle) {
        checkNotNull(deletion).delete(vehicle)
    }
}
