package com.carmanager.app.core.domain.model

data class VehicleStats(
    val vehicle: Vehicle,
    val averageConsumption: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalLiters: Double = 0.0,
    val fuelRecordsCount: Int = 0,
    val distanceTracked: Int = 0,
    val monthlyFuelCost: Double = 0.0,
    val yearlyMaintenanceCost: Double = 0.0,
    val alerts: List<String> = emptyList(),
    val nextCTDate: Long? = null,
    val nextInsuranceDate: Long? = null,
    val consumptionHistory: List<Double> = emptyList(),
    val isCTDocMissing: Boolean = false,
    val isInsuranceDocMissing: Boolean = false
)
