package com.carmanager.app.features.fuel

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.repository.FuelRepository
import javax.inject.Inject

class DeleteFuelRecordUseCase @Inject constructor(
    private val repository: FuelRepository
) {
    suspend operator fun invoke(record: FuelRecord) {
        repository.deleteFuelRecord(record)
    }
}
