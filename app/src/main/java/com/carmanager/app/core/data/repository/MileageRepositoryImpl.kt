package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.MileageDao
import com.carmanager.app.core.data.local.entity.MileageRecordEntity
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.repository.MileageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MileageRepositoryImpl @Inject constructor(
    private val mileageDao: MileageDao
) : MileageRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<MileageRecord>> {
        return mileageDao.observeByVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveRecord(record: MileageRecord): Long {
        return mileageDao.insert(record.toEntity())
    }

    override suspend fun getLatestMileage(vehicleId: Long): Int? {
        return mileageDao.getLatestByVehicle(vehicleId)?.mileage
    }

    private fun MileageRecordEntity.toDomain() = MileageRecord(
        id = id,
        vehicleId = vehicleId,
        date = date,
        mileage = mileage,
        source = MileageSource.valueOf(source)
    )

    private fun MileageRecord.toEntity() = MileageRecordEntity(
        id = id,
        vehicleId = vehicleId,
        date = date,
        mileage = mileage,
        source = source.name
    )
}
