package com.carmanager.app.features.mileage

import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.repository.MileageRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UpdateMileageUseCaseTest {

    private val vehicleRepository = mockk<VehicleRepository>()
    private val mileageRepository = mockk<MileageRepository>()
    
    private val useCase = UpdateMileageUseCase(
        vehicleRepository,
        mileageRepository
    )

    @Test
    fun `when new mileage is higher, vehicle is updated`() = runTest {
        // GIVEN
        val vehicle = Vehicle(id = 1, brand = "Test", model = "Car", year = 2020, currentMileage = 1000, fuelType = FuelType.GASOLINE, type = VehicleType.CAR, createdAt = 0, updatedAt = 0, powerHp = 100, licensePlate = "ABC")
        coEvery { vehicleRepository.saveVehicle(any()) } returns 1L
        coEvery { mileageRepository.saveRecord(any()) } returns 1L

        // WHEN
        useCase(vehicle, 1500, MileageSource.MANUAL)

        // THEN
        coVerify { vehicleRepository.saveVehicle(match { it.currentMileage == 1500 }) }
        coVerify { mileageRepository.saveRecord(match { it.mileage == 1500 }) }
    }

    @Test
    fun `when new mileage is lower, vehicle is not updated`() = runTest {
        // GIVEN
        val vehicle = Vehicle(id = 1, brand = "Test", model = "Car", year = 2020, currentMileage = 2000, fuelType = FuelType.GASOLINE, type = VehicleType.CAR, createdAt = 0, updatedAt = 0, powerHp = 100, licensePlate = "ABC")
        coEvery { mileageRepository.saveRecord(any()) } returns 1L

        // WHEN
        useCase(vehicle, 1500, MileageSource.MANUAL)

        // THEN
        coVerify(exactly = 0) { vehicleRepository.saveVehicle(any()) }
        // On enregistre quand même le point (historique) ou pas ? 
        // Selon la logique actuelle : oui, mais ici on teste juste le comportement du repository véhicule
        coVerify { mileageRepository.saveRecord(any()) }
    }
}
