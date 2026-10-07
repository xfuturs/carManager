package com.carmanager.app.features.vehicles

import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import javax.inject.Inject

class DeleteVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle): String? {
        return try { repository.deleteVehicle(vehicle); null }
        catch (warning: com.carmanager.app.core.data.local.VehicleCleanupPendingException) { warning.message }
    }
}
