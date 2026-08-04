package com.carmanager.app.features.fuel

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

import com.carmanager.app.features.mileage.UpdateMileageUseCase

class SaveFuelRecordUseCase @Inject constructor(
    private val repository: FuelRepository,
    private val vehicleRepository: VehicleRepository,
    private val updateMileageUseCase: UpdateMileageUseCase
) {
    suspend operator fun invoke(record: FuelRecord): Long {
        if (record.liters <= 0 || record.totalPrice <= 0) {
            throw IllegalArgumentException("Les litres et le prix total doivent être supérieurs à zéro")
        }

        val id = repository.saveFuelRecord(record)

        // Lier le kilométrage au véhicule
        vehicleRepository.observeById(record.vehicleId).firstOrNull()?.let { vehicle ->
            updateMileageUseCase(vehicle, record.mileage, MileageSource.FUEL, record.date)
        }

        return id
    }
}
