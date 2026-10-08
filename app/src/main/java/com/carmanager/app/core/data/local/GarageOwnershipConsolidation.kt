package com.carmanager.app.core.data.local

import com.carmanager.app.core.data.local.dao.MaintenanceDao
import com.carmanager.app.core.data.local.dao.VehicleDao
import com.carmanager.app.core.domain.model.ReminderPreferences
import com.carmanager.app.core.domain.session.LocalGarageOwner
import com.carmanager.app.core.util.ReminderKey

/** Les clés d'alarmes restent durables jusqu'à réconciliation ; seul ownerKey change en SQL. */
internal class GarageOwnershipConsolidation(
    private val vehicles: VehicleDao,
    private val maintenance: MaintenanceDao,
    private val remember: suspend (Set<ReminderKey>) -> Unit,
    private val transaction: suspend (suspend () -> Unit) -> Unit
) {
    suspend fun run() {
        val legacyKeys = vehicles.legacyOwners(LocalGarageOwner.KEY).flatMap { owner ->
            maintenance.getAll(owner).flatMap { record ->
                listOf(ReminderKey(owner, record.id, null)) + ReminderPreferences.LEAD_DAYS.map { ReminderKey(owner, record.id, it) }
            }
        }.toSet()
        if (legacyKeys.isNotEmpty()) remember(legacyKeys)
        transaction {
            vehicles.consolidateOwners(LocalGarageOwner.KEY)
            check(vehicles.legacyOwners(LocalGarageOwner.KEY).isEmpty()) { "Consolidation locale incomplète." }
        }
    }
}
