package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.ReportSection
import com.carmanager.app.core.domain.repository.MileageRepository
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class GenerateVehicleReportUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val session: com.carmanager.app.core.domain.session.WorkspaceSession,
    private val mileageRepository: MileageRepository
) {
    suspend operator fun invoke(vehicleId: Long): ReportData? {
        val owner = session.owner.value
        val vehicle = vehicleRepository.observeById(vehicleId).firstOrNull() ?: return null
        val fuelRecords = fuelRepository.observeByVehicle(vehicleId).firstOrNull() ?: emptyList()
        val maintenanceRecords = maintenanceRepository.observeByVehicle(vehicleId).firstOrNull() ?: emptyList()
        val mileageRecords = mileageRepository.observeByVehicle(vehicleId).firstOrNull() ?: emptyList()
        session.requireCurrent(owner)
        check(vehicle.id == vehicleId && vehicle.ownerKey == owner && fuelRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            maintenanceRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            mileageRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId })

        return ReportData(vehicle, fuelRecords, maintenanceRecords, mileageRecords)
    }

    data class ReportData(
        val vehicle: Vehicle,
        val fuelRecords: List<FuelRecord>,
        val maintenanceRecords: List<MaintenanceRecord>,
        val mileageRecords: List<MileageRecord> = emptyList(),
        val sections: Set<ReportSection> = ReportSection.entries.toSet()
    )
}
