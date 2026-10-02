package com.carmanager.app.features.vehicles

import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import javax.inject.Inject

class SaveVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle): Long {
        com.carmanager.app.core.domain.validation.GarageValidation.vehicle(vehicle)
        return repository.saveVehicle(vehicle)
    }
}
