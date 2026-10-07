package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.MileageDao
import com.carmanager.app.core.data.local.entity.MileageRecordEntity
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.repository.MileageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import javax.inject.Inject

class MileageRepositoryImpl @Inject constructor(
    private val mileageDao: MileageDao,
    private val session: WorkspaceSession,
    private val access: OwnedDatabaseAccess,
    private val writer: com.carmanager.app.core.data.local.LocalGarageWriter
) : MileageRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<MileageRecord>> {
        return session.observe { owner -> mileageDao.observeByVehicle(vehicleId, owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override suspend fun saveRecord(record: MileageRecord): Long {
        if (record.id == 0L) return writer.saveMileage(record)
        com.carmanager.app.core.domain.validation.GarageValidation.mileage(record.mileage)

        return access.write(record.ownerKey, record.vehicleId) {
            if (record.id != 0L) check(mileageDao.getById(record.id, record.ownerKey) != null) { "Enregistrement absent de cet espace." }
            mileageDao.insert(record.toEntity())
        }
    }

    override suspend fun getLatestMileage(vehicleId: Long): Int? {
        val owner = session.owner.value
        val result = mileageDao.getLatestByVehicle(vehicleId, owner)?.mileage
        session.requireCurrent(owner)
        return result
    }

    private fun MileageRecord.toEntity() = MileageRecordEntity(
        id = id,
        vehicleId = vehicleId,
        date = date,
        mileage = mileage,
        source = source.name
    )
}
