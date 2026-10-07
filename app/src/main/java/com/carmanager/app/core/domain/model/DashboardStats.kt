package com.carmanager.app.core.domain.model

data class DashboardStats(
    val vehicleCount: Int = 0,
    val vehicles: List<VehicleStats> = emptyList(),
    val monthlyFuelCost: Double = 0.0,
    val monthlyMaintenanceCost: Double = 0.0,
    val nextMaintenance: MaintenanceRecord? = null,
    val upcomingDeadlines: List<Pair<Vehicle, MaintenanceRecord>> = emptyList(),
    val temporalContext: TemporalContext? = null
)
