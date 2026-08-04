package com.carmanager.app.features.mileage

import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.MileageRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Point d'entrée central pour la mise à jour du kilométrage d'un véhicule.
 * 
 * Cette classe assure deux fonctions critiques :
 * 1. La mise à jour de l'état actuel du véhicule si le nouveau kilométrage est cohérent (supérieur).
 * 2. L'archivage du nouveau point de mesure dans l'historique (MileageRecord).
 * 
 * @property vehicleRepository Accès aux données structurelles des véhicules.
 * @property mileageRepository Accès à l'historique des points kilométriques.
 */
class UpdateMileageUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val mileageRepository: MileageRepository
) {
    suspend operator fun invoke(vehicle: Vehicle, newMileage: Int, source: MileageSource, date: Long = System.currentTimeMillis()) {
        // 1. Mettre à jour le véhicule si le nouveau kilométrage est plus récent ou supérieur
        if (newMileage > vehicle.currentMileage) {
            vehicleRepository.saveVehicle(vehicle.copy(currentMileage = newMileage, updatedAt = date))
        }

        // 2. Enregistrer le point de kilométrage dans l'historique
        mileageRepository.saveRecord(
            MileageRecord(
                vehicleId = vehicle.id,
                date = date,
                mileage = newMileage,
                source = source
            )
        )
    }
}
