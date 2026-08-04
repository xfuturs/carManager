package com.carmanager.app.features.maintenance

import android.content.Context
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

import com.carmanager.app.features.mileage.UpdateMileageUseCase

class SaveMaintenanceUseCase @Inject constructor(
    private val repository: MaintenanceRepository,
    private val vehicleRepository: VehicleRepository,
    private val updateMileageUseCase: UpdateMileageUseCase,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(record: MaintenanceRecord): Long {
        val id = repository.saveMaintenanceRecord(record)

        // Lier le kilométrage au véhicule
        vehicleRepository.observeById(record.vehicleId).firstOrNull()?.let { vehicle ->
            updateMileageUseCase(vehicle, record.mileage, MileageSource.MAINTENANCE, record.date)
            
            // Programmer un rappel si une date d'échéance est fixée
            record.nextDueDate?.let { dueDate ->
                if (dueDate > System.currentTimeMillis()) {
                    val label = when(record.type) {
                        MaintenanceType.TECHNICAL_INSPECTION -> "Contrôle Technique"
                        MaintenanceType.INSURANCE -> "Assurance"
                        else -> "Entretien"
                    }
                    NotificationHelper.scheduleReminder(
                        context,
                        dueDate,
                        "Rappel : $label",
                        "Votre ${vehicle.brand} a une échéance aujourd'hui."
                    )
                }
            }
        }

        return id
    }
}
