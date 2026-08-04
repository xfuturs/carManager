package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Orchestre le calcul et la fusion de toutes les statistiques affichées sur l'écran d'accueil.
 */
class GetDashboardStatsUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val documentRepository: DocumentRepository
) {
    operator fun invoke(): Flow<DashboardStats> {
        val now = System.currentTimeMillis()
        
        val basicDataFlow = combine(
            vehicleRepository.observeCount(),
            vehicleRepository.observeAll(),
            fuelRepository.observeAll(),
            maintenanceRepository.observeAll(),
            documentRepository.observeAll()
        ) { count, vehicles, allFuel, allMaint, allDocs ->
            BasicData(count, vehicles, allFuel, allMaint, allDocs)
        }

        val totalsFlow = combine(
            fuelRepository.observeMonthlyTotal(now),
            maintenanceRepository.observeMonthlyTotal(now),
            maintenanceRepository.observeNextUpcoming()
        ) { fuelTotal, maintTotal, next ->
            TotalsData(fuelTotal, maintTotal, next)
        }

        return combine(basicDataFlow, totalsFlow) { basic, totals ->
            val currentZone = ZoneId.systemDefault()
            val nowDateTime = Instant.ofEpochMilli(now).atZone(currentZone)
            val currentMonth = nowDateTime.month
            val currentYear = nowDateTime.year
            
            val vehicleStatsList = basic.vehicles.map { vehicle ->
                val vehicleFuel = basic.allFuel.filter { it.vehicleId == vehicle.id }.sortedBy { it.date }
                val vehicleMaintenance = basic.allMaint.filter { it.vehicleId == vehicle.id }
                val vehicleDocs = basic.allDocs.filter { it.vehicleId == vehicle.id }
                
                // Séparation thermique / électrique
                val thermalRecords = vehicleFuel.filter { !it.isElectric }
                val electricRecords = vehicleFuel.filter { it.isElectric }

                var avgConso = 0.0
                var distance = 0
                val history = mutableListOf<Double>()
                
                if (vehicle.fuelType == FuelType.ELECTRIC) {
                    if (electricRecords.size > 1) {
                        for (i in 1 until electricRecords.size) {
                            val dist = electricRecords[i].mileage - electricRecords[i-1].mileage
                            if (dist > 0) history.add((electricRecords[i].liters / dist) * 100.0)
                        }
                        distance = electricRecords.last().mileage - electricRecords.first().mileage
                        if (distance > 0) avgConso = (electricRecords.sumOf { it.liters } - electricRecords.first().liters) / distance * 100.0
                    }
                } else {
                    if (thermalRecords.size > 1) {
                        for (i in 1 until thermalRecords.size) {
                            val dist = thermalRecords[i].mileage - thermalRecords[i-1].mileage
                            if (dist > 0) history.add((thermalRecords[i].liters / dist) * 100.0)
                        }
                        distance = thermalRecords.last().mileage - thermalRecords.first().mileage
                        if (distance > 0) avgConso = (thermalRecords.sumOf { it.liters } - thermalRecords.first().liters) / distance * 100.0
                    }
                }

                val vehicleMonthlyFuel = vehicleFuel.filter {
                    val date = Instant.ofEpochMilli(it.date).atZone(currentZone)
                    date.month == currentMonth && date.year == currentYear
                }.sumOf { it.totalPrice }

                val vehicleYearlyMaintenance = vehicleMaintenance.filter {
                    Instant.ofEpochMilli(it.date).atZone(currentZone).year == currentYear
                }.sumOf { it.cost }

                val nextCTDate = vehicleMaintenance
                    .filter { it.type == MaintenanceType.TECHNICAL_INSPECTION }
                    .mapNotNull { it.nextDueDate }.maxOrNull()

                val nextInsuranceDate = vehicleMaintenance
                    .filter { it.type == MaintenanceType.INSURANCE }
                    .mapNotNull { it.nextDueDate }.maxOrNull()

                // Vérification présence documents (plus précise avec les nouvelles catégories)
                val hasCTDoc = vehicleDocs.any { 
                    it.category == DocumentCategory.TECHNICAL_INSPECTION || 
                    (it.category == DocumentCategory.ADMINISTRATIVE && it.title.contains("CT", true)) 
                }
                val hasInsuranceDoc = vehicleDocs.any { 
                    it.category == DocumentCategory.INSURANCE || 
                    (it.category == DocumentCategory.ADMINISTRATIVE && it.title.contains("Assurance", true)) 
                }

                val alerts = mutableListOf<String>()
                vehicleMaintenance.forEach { maint ->
                    maint.nextDueMileage?.let { if (vehicle.currentMileage >= it) alerts.add("KILOMÉTRAGE DÉPASSÉ : ${getMaintenanceLabel(maint.type)}") }
                    maint.nextDueDate?.let { if (now >= it) alerts.add("DATE DÉPASSÉE : ${getMaintenanceLabel(maint.type)}") }
                }

                VehicleStats(
                    vehicle = vehicle,
                    averageConsumption = avgConso,
                    totalFuelCost = vehicleFuel.sumOf { it.totalPrice },
                    totalLiters = vehicleFuel.sumOf { it.liters },
                    fuelRecordsCount = vehicleFuel.size,
                    distanceTracked = distance,
                    monthlyFuelCost = vehicleMonthlyFuel,
                    yearlyMaintenanceCost = vehicleYearlyMaintenance,
                    alerts = alerts.distinct(),
                    nextCTDate = nextCTDate,
                    nextInsuranceDate = nextInsuranceDate,
                    consumptionHistory = history.takeLast(10),
                    isCTDocMissing = !hasCTDoc,
                    isInsuranceDocMissing = !hasInsuranceDoc
                )
            }

            // Échéances à venir pour tout le garage
            val deadlines = mutableListOf<Pair<Vehicle, MaintenanceRecord>>()
            basic.vehicles.forEach { vehicle ->
                val vehicleMaint = basic.allMaint.filter { it.vehicleId == vehicle.id }
                vehicleMaint.filter { it.nextDueDate != null || it.nextDueMileage != null }.forEach { record ->
                    val isFutureDate = (record.nextDueDate ?: Long.MAX_VALUE) > (now - 86400000)
                    val isFutureMileage = (record.nextDueMileage ?: Int.MAX_VALUE) >= vehicle.currentMileage
                    
                    if (isFutureDate || isFutureMileage) {
                        deadlines.add(vehicle to record)
                    }
                }
            }

            DashboardStats(
                vehicleCount = basic.count,
                vehicles = vehicleStatsList,
                monthlyFuelCost = totals.monthlyFuelTotal,
                monthlyMaintenanceCost = totals.monthlyMaintenanceTotal,
                nextMaintenance = totals.next,
                upcomingDeadlines = deadlines.sortedWith(compareBy({ it.second.nextDueDate ?: Long.MAX_VALUE }, { it.second.nextDueMileage ?: Int.MAX_VALUE }))
            )
        }
    }

    private fun getMaintenanceLabel(type: MaintenanceType) = when (type) {
        MaintenanceType.TECHNICAL_INSPECTION -> "Contrôle Technique"
        MaintenanceType.INSURANCE -> "Assurance"
        else -> "Entretien"
    }

    private data class BasicData(
        val count: Int, 
        val vehicles: List<Vehicle>, 
        val allFuel: List<FuelRecord>, 
        val allMaint: List<MaintenanceRecord>,
        val allDocs: List<Document>
    )
    
    private data class TotalsData(val monthlyFuelTotal: Double, val monthlyMaintenanceTotal: Double, val next: MaintenanceRecord?)
}
