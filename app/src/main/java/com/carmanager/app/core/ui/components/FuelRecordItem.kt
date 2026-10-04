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
import com.carmanager.app.core.domain.model.FuelRecord

@Composable
fun FuelRecordItem(record: FuelRecord, modifier: Modifier = Modifier) {
    val units = LocalAppUnits.current
    val energyUnit = if (record.isElectric) "kWh" else "L"
    RecordRow(if (record.isElectric) Icons.Default.EvStation else Icons.Default.LocalGasStation, modifier) {
        Text("${record.totalPrice} ${units.currency} · ${if (record.isElectric) "Recharge" else "Plein"}",
            style = CarManagerTypography.supporting, color = MaterialTheme.colorScheme.onSurface)
        Text(DateFormatter.formatMedium(record.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${record.mileage} ${units.distance} · ${record.liters} $energyUnit", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!record.note.isNullOrBlank()) {
            Text(record.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
