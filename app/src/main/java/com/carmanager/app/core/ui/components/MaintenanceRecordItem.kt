package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.CarManagerTypography
import com.carmanager.app.core.util.DateFormatter
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType

@Composable
fun MaintenanceRecordItem(record: MaintenanceRecord, modifier: Modifier = Modifier) {
    val units = LocalAppUnits.current
    RecordRow(Icons.Default.Build, modifier) {
        Text("${getMaintenanceLabel(record.type)} · ${record.cost} ${units.currency}", style = CarManagerTypography.supporting)
        Text(DateFormatter.formatMedium(record.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${record.mileage} ${units.distance}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!record.note.isNullOrBlank()) {
            Text(record.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (record.nextDueMileage != null) {
            Text("Prochaine échéance : ${record.nextDueMileage} ${units.distance}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun getMaintenanceLabel(type: MaintenanceType): String {
    return when (type) {
        MaintenanceType.OIL_CHANGE -> stringResource(R.string.maintenance_oil_change)
        MaintenanceType.TIRES -> stringResource(R.string.maintenance_tires)
        MaintenanceType.BRAKES -> stringResource(R.string.maintenance_brakes)
        MaintenanceType.BELT -> stringResource(R.string.maintenance_belt)
        MaintenanceType.BATTERY -> stringResource(R.string.maintenance_battery)
        MaintenanceType.INSPECTION -> stringResource(R.string.maintenance_inspection)
        MaintenanceType.REPAIR -> stringResource(R.string.maintenance_repair)
        MaintenanceType.TECHNICAL_INSPECTION -> stringResource(R.string.maintenance_technical_inspection)
        MaintenanceType.INSURANCE -> stringResource(R.string.maintenance_insurance)
        MaintenanceType.OTHER -> stringResource(R.string.maintenance_other)
    }
}
