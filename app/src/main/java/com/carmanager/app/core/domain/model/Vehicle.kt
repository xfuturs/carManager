package com.carmanager.app.core.domain.model

enum class FuelType {
    GASOLINE,
    DIESEL,
    ELECTRIC,
    HYBRID,
    LPG,
    OTHER,
}

enum class VehicleType {
    CAR,
    MOTORCYCLE,
    UTILITY,
}

data class Vehicle(
    val id: Long = 0,
    val brand: String,
    val model: String,
    val year: Int,
    val currentMileage: Int,
    val fuelType: FuelType,
    val type: VehicleType = VehicleType.CAR,
    val powerHp: Int?,
    val licensePlate: String?,
    val tankCapacity: Double? = null,
    val batteryCapacity: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerKey: String = com.carmanager.app.core.domain.session.LocalGarageOwner.KEY,
)
