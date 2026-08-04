package com.carmanager.app.features.vehicles

import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import javax.inject.Inject

class DeleteVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle) {
        repository.deleteVehicle(vehicle)
    }
}
