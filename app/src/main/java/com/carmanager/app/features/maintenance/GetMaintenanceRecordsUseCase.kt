package com.carmanager.app.features.maintenance

import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMaintenanceRecordsUseCase @Inject constructor(
    private val repository: MaintenanceRepository
) {
    operator fun invoke(vehicleId: Long): Flow<List<MaintenanceRecord>> = 
        repository.observeByVehicle(vehicleId)
}
