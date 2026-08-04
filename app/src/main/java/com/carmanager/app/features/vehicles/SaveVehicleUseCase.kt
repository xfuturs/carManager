package com.carmanager.app.features.vehicles

import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import javax.inject.Inject

class SaveVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle): Long {
        // Ici on pourrait ajouter de la validation métier
        if (vehicle.brand.isBlank() || vehicle.model.isBlank()) {
            throw IllegalArgumentException("La marque et le modèle sont obligatoires")
        }
        return repository.saveVehicle(vehicle)
    }
}
