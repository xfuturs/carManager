package com.carmanager.app.features.maintenance

import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import javax.inject.Inject

class DeleteMaintenanceRecordUseCase @Inject constructor(
    private val repository: MaintenanceRepository
) {
    suspend operator fun invoke(record: MaintenanceRecord) {
        repository.deleteMaintenanceRecord(record)
    }
}
