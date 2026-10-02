package com.carmanager.app.core.domain.model

data class FuelRecord(
    val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val mileage: Int,
    val liters: Double,
    val totalPrice: Double,
    val note: String? = null,
    val isElectric: Boolean = false, // true = Recharge (kWh), false = Plein (Litres),
    val ownerKey: String = "guest:local",
)
