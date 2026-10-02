package com.carmanager.app.features.maintenance

import android.content.Context
import android.util.Log
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.validation.GarageValidation
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MaintenanceSaveResult(val id: Long, val warning: String? = null)

class SaveMaintenanceUseCase @Inject constructor(
    private val repository: MaintenanceRepository,
    private val vehicleRepository: VehicleRepository,
    private val session: WorkspaceSession,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(record: MaintenanceRecord): MaintenanceSaveResult {
        GarageValidation.maintenance(record)
        // Échec ici : transaction annulée, le formulaire pourra réessayer.
        val id = repository.saveMaintenanceRecord(record)
        // Après commit, une alarme échouée ne transforme pas la sauvegarde en échec réessayable.
        return withContext(NonCancellable) {
            try {
                record.nextDueDate?.takeIf { it > System.currentTimeMillis() }?.let { dueDate ->
                    session.requireWritable(record.ownerKey)
                    val vehicle = checkNotNull(vehicleRepository.observeById(record.vehicleId).firstOrNull())
                    session.requireWritable(record.ownerKey)
                    val label = when (record.type) {
                        MaintenanceType.TECHNICAL_INSPECTION -> "Contrôle Technique"
                        MaintenanceType.INSURANCE -> "Assurance"
                        else -> "Entretien"
                    }
                    NotificationHelper.scheduleReminder(context, dueDate, "Rappel : $label",
                        "Votre ${vehicle.brand} a une échéance aujourd'hui.", record.ownerKey, id)
                }
                MaintenanceSaveResult(id)
            } catch (e: Exception) {
                Log.e("SaveMaintenance", "Intervention enregistrée, rappel non programmé", e)
                MaintenanceSaveResult(id, "Intervention enregistrée. Le rappel n'a pas pu être programmé ; ne réenregistrez pas l'intervention.")
            }
        }
    }
}
