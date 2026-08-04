package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.MaintenanceColor
import com.carmanager.app.core.util.DateFormatter

@Composable
fun MaintenanceRecordItem(
    record: MaintenanceRecord,
    modifier: Modifier = Modifier
) {
    val units = LocalAppUnits.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaintenanceColor,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateFormatter.formatMedium(record.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = getMaintenanceLabel(record.type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${record.mileage} ${units.distance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!record.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.cost} ${units.currency}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaintenanceColor
                )
                if (record.nextDueMileage != null) {
                    Text(
                        text = "Prochaine échéance : ${record.nextDueMileage} ${units.distance}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
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
