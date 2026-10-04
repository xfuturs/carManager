package com.carmanager.app.core.domain.model

enum class ReportSection(val label: String) {
    VEHICLE_INFORMATION("Informations du véhicule"),
    MILEAGE_HISTORY("Historique du kilométrage"),
    FUEL_AND_CHARGING_HISTORY("Pleins et recharges"),
    MAINTENANCE_HISTORY("Historique des interventions")
}
