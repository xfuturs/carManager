package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.*

/** Calcul pur : chaque historique est groupé une fois, sans cache ni accès aux repositories. */
object DashboardCalculator {
    fun calculate(vehicles: List<Vehicle>, fuel: List<FuelRecord>, maintenance: List<MaintenanceRecord>,
                  documents: List<Document>, time: TemporalContext): DashboardStats {
        val fuelByVehicle = fuel.groupBy { it.ownerKey to it.vehicleId }
        val maintenanceByVehicle = maintenance.groupBy { it.ownerKey to it.vehicleId }
        val documentsByVehicle = documents.groupBy { it.ownerKey to it.vehicleId }
        val deadlines = mutableListOf<Pair<Vehicle, MaintenanceRecord>>()
        var monthlyMaintenance = 0.0
        var next: MaintenanceRecord? = null
        val previousDayStart = time.instant.atZone(time.zone).minusDays(1).toInstant().toEpochMilli()
        val summaries = vehicles.map { vehicle ->
            val key = vehicle.ownerKey to vehicle.id
            val fills = fuelByVehicle[key].orEmpty().sortedBy { it.date }
            val interventions = maintenanceByVehicle[key].orEmpty()
            val docs = documentsByVehicle[key].orEmpty()
            // L'hybride conserve la série thermique ; aucune grandeur L+kWh combinée.
            val series = fills.filter { it.isElectric == (vehicle.fuelType == FuelType.ELECTRIC) }
            val history = mutableListOf<Double>()
            val validIndices = mutableSetOf<Int>()
            var consumed = 0.0
            var validDistance = 0.0
            for (index in 1 until series.size) {
                val before = series[index - 1]
                val after = series[index]
                val distance = after.mileage.toLong() - before.mileage
                if (distance > 0 && before.liters.isFinite() && before.liters > 0 &&
                    after.liters.isFinite() && after.liters > 0) {
                    consumed += after.liters
                    validDistance += distance
                    history += after.liters / distance * 100
                    validIndices += index - 1; validIndices += index
                }
            }
            val alerts = mutableListOf<String>()
            var yearlyMaintenance = 0.0
            var totalMaintenance = 0.0
            var nextCT: Long? = null
            var nextInsurance: Long? = null
            interventions.forEach { record ->
                totalMaintenance += record.cost
                if (record.date >= time.yearStart && record.date < time.nextYearStart) yearlyMaintenance += record.cost
                if (record.date >= time.monthStart && record.date < time.nextMonthStart) monthlyMaintenance += record.cost
                record.nextDueDate?.let { date ->
                    if (record.type == MaintenanceType.TECHNICAL_INSPECTION) nextCT = maxOf(nextCT ?: date, date)
                    if (record.type == MaintenanceType.INSURANCE) nextInsurance = maxOf(nextInsurance ?: date, date)
                    if (date >= time.instant.toEpochMilli() && (next?.nextDueDate?.let { date < it } != false)) next = record
                    if (time.instant.toEpochMilli() >= date) alerts += "DATE DÉPASSÉE : ${label(record.type)}"
                }
                record.nextDueMileage?.let { if (vehicle.currentMileage >= it) alerts += "KILOMÉTRAGE DÉPASSÉ : ${label(record.type)}" }
                if ((record.nextDueDate != null || record.nextDueMileage != null) &&
                    ((record.nextDueDate ?: Long.MAX_VALUE) > previousDayStart ||
                        (record.nextDueMileage ?: Int.MAX_VALUE) >= vehicle.currentMileage)) deadlines += vehicle to record
            }
            VehicleStats(vehicle = vehicle,
                averageConsumption = if (validDistance > 0) consumed / validDistance * 100 else null,
                relevantConsumptionSampleCount = validIndices.size,
                totalFuelCost = fills.sumOf { it.totalPrice }, totalLiters = fills.sumOf { it.liters },
                fuelRecordsCount = fills.size,
                distanceTracked = if (series.size > 1) series.last().mileage - series.first().mileage else 0,
                monthlyFuelCost = fills.filter { it.date >= time.monthStart && it.date < time.nextMonthStart }.sumOf { it.totalPrice },
                yearlyMaintenanceCost = yearlyMaintenance, totalMaintenanceCost = totalMaintenance,
                alerts = alerts.distinct(), nextCTDate = nextCT, nextInsuranceDate = nextInsurance,
                consumptionHistory = history.takeLast(10),
                isCTDocMissing = docs.none { it.category == DocumentCategory.TECHNICAL_INSPECTION ||
                    (it.category == DocumentCategory.ADMINISTRATIVE && it.title.contains("CT", true)) },
                isInsuranceDocMissing = docs.none { it.category == DocumentCategory.INSURANCE ||
                    (it.category == DocumentCategory.ADMINISTRATIVE && it.title.contains("Assurance", true)) })
        }
        return DashboardStats(vehicleCount = vehicles.size, vehicles = summaries,
            monthlyFuelCost = summaries.sumOf { it.monthlyFuelCost }, monthlyMaintenanceCost = monthlyMaintenance,
            nextMaintenance = next, temporalContext = time,
            upcomingDeadlines = deadlines.sortedWith(compareBy({ it.second.nextDueDate ?: Long.MAX_VALUE },
                { it.second.nextDueMileage ?: Int.MAX_VALUE })))
    }
    private fun label(type: MaintenanceType) = when (type) {
        MaintenanceType.TECHNICAL_INSPECTION -> "Contrôle Technique"
        MaintenanceType.INSURANCE -> "Assurance"
        else -> "Entretien"
    }
}
