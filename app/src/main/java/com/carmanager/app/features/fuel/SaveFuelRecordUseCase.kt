package com.carmanager.app.features.fuel

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.validation.GarageValidation
import javax.inject.Inject

class SaveFuelRecordUseCase @Inject constructor(private val repository: FuelRepository) {
    suspend operator fun invoke(record: FuelRecord): Long {
        GarageValidation.fuel(record)
        return repository.saveFuelRecord(record)
    }
}
