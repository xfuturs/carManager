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

class GenerateVehicleReportUseCase @Inject constructor(private val reader: OwnedReportSnapshotReader) {
    suspend operator fun invoke(vehicleId: Long): ReportData? = reader.read(vehicleId)

    data class ReportData(
        val vehicle: Vehicle,
        val fuelRecords: List<FuelRecord>,
        val maintenanceRecords: List<MaintenanceRecord>,
        val mileageRecords: List<MileageRecord> = emptyList(),
        val sections: Set<ReportSection> = ReportSection.entries.toSet(),
        val presentation: com.carmanager.app.core.util.ReportPresentationSettings = com.carmanager.app.core.util.ReportPresentationSettings()
    )
}
