package com.carmanager.app.features.fuel

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.repository.FuelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFuelRecordsUseCase @Inject constructor(
    private val repository: FuelRepository
) {
    operator fun invoke(vehicleId: Long): Flow<List<FuelRecord>> = repository.observeByVehicle(vehicleId)
}
