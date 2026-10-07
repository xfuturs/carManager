package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry

class GetDashboardStatsUseCaseTest {

    private val vehicleRepository = mockk<VehicleRepository>()
    private val fuelRepository = mockk<FuelRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val documentRepository = mockk<DocumentRepository>()
    
    private val useCase = GetDashboardStatsUseCase(
        vehicleRepository,
        fuelRepository,
        maintenanceRepository,
        documentRepository,
        WorkspaceSession(TestDeletionRegistry())
    )

    @Test
    fun `when two fuel records exist, average consumption is calculated correctly`() = runTest {
        // GIVEN
        val vehicle = Vehicle(id = 1, brand = "Test", model = "Car", year = 2020, currentMileage = 1000, fuelType = FuelType.GASOLINE, type = VehicleType.CAR, createdAt = 0, updatedAt = 0, powerHp = 100, licensePlate = "ABC")
        val fuel1 = FuelRecord(id = 1, vehicleId = 1, date = 1000, mileage = 0, liters = 50.0, totalPrice = 70.0)
        val fuel2 = FuelRecord(id = 2, vehicleId = 1, date = 2000, mileage = 1000, liters = 50.0, totalPrice = 70.0)

        every { vehicleRepository.observeCount() } returns flowOf(1)
        every { vehicleRepository.observeAll() } returns flowOf(listOf(vehicle))
        every { fuelRepository.observeAll() } returns flowOf(listOf(fuel1, fuel2))
        every { maintenanceRepository.observeAll() } returns flowOf(emptyList())
        every { documentRepository.observeAll() } returns flowOf(emptyList())
        every { fuelRepository.observeMonthlyTotal(any()) } returns flowOf(140.0)
        every { maintenanceRepository.observeMonthlyTotal(any()) } returns flowOf(0.0)
        every { maintenanceRepository.observeNextUpcoming() } returns flowOf(null)

        // WHEN
        val stats = GetDashboardStatsUseCase(vehicleRepository, fuelRepository, maintenanceRepository, documentRepository,
            WorkspaceSession(TestDeletionRegistry()), DashboardTimeSource(), kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))().first()

        // THEN
        val vehicleStats = stats.vehicles.first()
        // (100L total - 50L premier plein) / 1000km * 100 = 5.0 L/100
        assertEquals(5.0, vehicleStats.averageConsumption)
        assertEquals(1, stats.vehicleCount)
    }
}
