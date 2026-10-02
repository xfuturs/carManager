package com.carmanager.app.features.mileage

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.MileageRepository
import com.carmanager.app.core.domain.validation.GarageValidation
import javax.inject.Inject

/** Le repository relit le compteur et journalise dans une même transaction propriétaire. */
class UpdateMileageUseCase @Inject constructor(private val mileageRepository: MileageRepository) {
    suspend operator fun invoke(vehicle: Vehicle, newMileage: Int, source: MileageSource, date: Long = System.currentTimeMillis()) {
        GarageValidation.mileage(newMileage)
        GarageValidation.performedDate(date)
        mileageRepository.saveRecord(MileageRecord(vehicleId = vehicle.id, ownerKey = vehicle.ownerKey,
            date = date, mileage = newMileage, source = source))
    }
}
