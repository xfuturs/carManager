package com.carmanager.app.ownership

import com.carmanager.app.core.data.local.entity.FuelTypeEntity
import com.carmanager.app.core.data.local.entity.VehicleEntity
import com.carmanager.app.core.domain.session.DeletionRegistry
import kotlinx.coroutines.flow.MutableStateFlow

class TestDeletionRegistry : DeletionRegistry {
    override val blockedOwners = MutableStateFlow<Set<String>>(emptySet())
    override suspend fun block(owner: String) { blockedOwners.value += owner }
}

fun testVehicle(owner: String, id: Long = 1) = VehicleEntity(
    id = id, brand = "Test", model = "Garage", year = 2020, currentMileage = 100,
    fuelType = FuelTypeEntity.GASOLINE, powerHp = 90, licensePlate = null,
    createdAt = 0, updatedAt = 0, ownerKey = owner
)
