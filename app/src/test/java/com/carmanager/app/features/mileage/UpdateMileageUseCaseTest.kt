package com.carmanager.app.features.mileage

import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.model.MileageSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpdateMileageUseCaseTest {
    @Test fun `when new mileage is higher, vehicle is updated`() = runTest {
        val fixture = GarageFixture()
        val useCase = UpdateMileageUseCase(fixture.mileageRepository())
        useCase(fixture.vehicle(), 1500, MileageSource.MANUAL)
        assertEquals(1500, fixture.vehicles[1]?.currentMileage)
        assertEquals(1500, fixture.history.single().mileage)
        assertEquals(1, fixture.transactions)
    }
    @Test fun `when new mileage is lower, vehicle is not updated`() = runTest {
        val fixture = GarageFixture()
        val useCase = UpdateMileageUseCase(fixture.mileageRepository())
        useCase(fixture.vehicle(), 900, MileageSource.FUEL, 0)
        assertEquals(1000, fixture.vehicles[1]?.currentMileage)
        assertEquals(900, fixture.history.single().mileage)
        assertEquals("FUEL", fixture.history.single().source)
    }
}
