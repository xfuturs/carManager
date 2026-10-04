package com.carmanager.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.unit.dp

@Composable
fun VehicleItem(
    vehicle: Vehicle,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onFuelClick: () -> Unit,
    onMaintenanceClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val units = LocalAppUnits.current
    var showDeleteDialog by remember(vehicle.id) { mutableStateOf(false) }
    var showActions by remember(vehicle.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(), shape = CarManagerShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(CarManagerSpacing.extraSmall), verticalAlignment = Alignment.Top) {
            // La zone d'ouverture et le menu sont frères : aucune cible interactive imbriquée.
            Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).heightIn(min = CarManagerDimensions.touchTarget),
                shape = CarManagerShapes.control) {
                Column(Modifier.padding(CarManagerSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall)) {
                    VehicleIdentity(vehicle, Modifier.fillMaxWidth())
                    Text("${NumberFormat.getIntegerInstance(Locale.FRANCE).format(vehicle.currentMileage)} ${units.distance}",
                        style = CarManagerTypography.cardTitle, color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ouvrir le suivi", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { showActions = true }, modifier = Modifier.sizeIn(
                    minWidth = CarManagerDimensions.touchTarget, minHeight = CarManagerDimensions.touchTarget)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions pour ${vehicle.brand} ${vehicle.model}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(text = { Text(if (vehicle.fuelType == FuelType.ELECTRIC) "Recharge" else "Carburant") },
                        leadingIcon = { Icon(Icons.Default.EvStation, contentDescription = null) },
                        modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                        onClick = { showActions = false; onFuelClick() })
                    DropdownMenuItem(text = { Text("Entretien") },
                        leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                        modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                        onClick = { showActions = false; onMaintenanceClick() })
                    DropdownMenuItem(text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                        onClick = { showActions = false; onEdit() })
                    DropdownMenuItem(text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                        onClick = { showActions = false; showDeleteDialog = true })
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer ce véhicule ?") },
            text = { Text("Toutes les données associées seront définitivement perdues.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
