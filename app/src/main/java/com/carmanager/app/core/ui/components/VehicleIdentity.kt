package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography
import com.carmanager.app.features.vehicles.getFuelTypeName

/** Identité commune, sans coûts ni actions métier : accueil et garage gardent leurs rôles. */
@Composable
fun VehicleIdentity(vehicle: Vehicle, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
        Icon(
            imageVector = when (vehicle.type) {
                VehicleType.CAR -> Icons.Default.DirectionsCar
                VehicleType.MOTORCYCLE -> Icons.Default.TwoWheeler
                VehicleType.UTILITY -> Icons.Default.LocalShipping
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("${vehicle.brand} ${vehicle.model}", style = CarManagerTypography.cardTitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${vehicle.year} • ${getFuelTypeName(vehicle.fuelType)}",
                style = CarManagerTypography.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { plate ->
                Text(plate, style = CarManagerTypography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
