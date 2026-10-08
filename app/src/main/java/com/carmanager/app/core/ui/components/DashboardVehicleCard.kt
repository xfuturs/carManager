package com.carmanager.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.VehicleStats
import com.carmanager.app.core.ui.theme.*
import com.carmanager.app.core.util.DateFormatter
import java.text.NumberFormat
import java.util.Locale
import com.carmanager.app.core.ui.navigation.rememberTimedInterstitialBlocker

@Composable
fun DashboardVehicleCard(
    stats: VehicleStats,
    onEdit: () -> Unit,
    onFuelClick: () -> Unit,
    onMaintenanceClick: () -> Unit,
    onAdviceClick: () -> Unit,
    onMileageHistoryClick: () -> Unit,
    onAdminClick: (MaintenanceType) -> Unit,
    onDocumentsClick: () -> Unit,
    onMileageUpdate: (Int) -> Unit,
    mileageSaveEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val vehicle = stats.vehicle
    val units = LocalAppUnits.current
    var showMileageDialog by remember(vehicle.id) { mutableStateOf(false) }
    var mileageInput by remember(vehicle.id) { mutableStateOf(vehicle.currentMileage.toString()) }
    val blockInterstitials = rememberTimedInterstitialBlocker()
    Card(
        modifier = modifier.fillMaxWidth(), shape = CarManagerShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(CarManagerSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VehicleIdentity(vehicle, Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.sizeIn(
                    minWidth = CarManagerDimensions.touchTarget, minHeight = CarManagerDimensions.touchTarget)) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier ${vehicle.brand} ${vehicle.model}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { blockInterstitials(true); mileageInput = vehicle.currentMileage.toString(); showMileageDialog = true },
                    enabled = mileageSaveEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = CarManagerDimensions.touchTarget),
                    contentPadding = PaddingValues(horizontal = CarManagerSpacing.small)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${NumberFormat.getIntegerInstance(Locale.FRANCE).format(vehicle.currentMileage)} ${units.distance}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Mettre à jour le compteur", style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMileageHistoryClick, modifier = Modifier.sizeIn(
                    minWidth = CarManagerDimensions.touchTarget, minHeight = CarManagerDimensions.touchTarget)) {
                    Icon(Icons.Default.History, contentDescription = "Historique du kilométrage",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Les alertes fournies par le use case sont de vrais dépassements ; aucune réinterprétation métier.
            stats.alerts.forEach { alert ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CarManagerShapes.control) {
                    Row(Modifier.fillMaxWidth().padding(CarManagerSpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(alert, style = CarManagerTypography.supporting,
                            color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    }
                }
            }

            AdaptivePair(
                first = { modifier -> VehicleStatusItem("Contrôle technique", "Non renseigné", stats.nextCTDate,
                    stats.isCTDocMissing, Icons.AutoMirrored.Filled.FactCheck,
                    { onAdminClick(MaintenanceType.TECHNICAL_INSPECTION) }, modifier) },
                second = { modifier -> VehicleStatusItem("Assurance", "Non renseignée", stats.nextInsuranceDate,
                    stats.isInsuranceDocMissing, Icons.Default.Shield,
                    { onAdminClick(MaintenanceType.INSURANCE) }, modifier) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AdaptivePair(
                first = { modifier -> DashboardStatItem(
                    label = if (vehicle.fuelType == FuelType.ELECTRIC) "Recharge · mois" else "Carburant · mois",
                    value = "%.0f".format(stats.monthlyFuelCost) + units.currency,
                    color = MaterialTheme.colorScheme.primary, modifier = modifier) },
                second = { modifier -> DashboardStatItem(label = "Entretien · année",
                    value = "%.0f".format(stats.yearlyMaintenanceCost) + units.currency,
                    color = MaterialTheme.colorScheme.secondary, modifier = modifier) }
            )
            DashboardQuickActions(onFuelClick, onMaintenanceClick, onDocumentsClick, onAdviceClick,
                isElectric = vehicle.fuelType == FuelType.ELECTRIC)
        }
    }
    if (showMileageDialog) {
        MileageUpdateDialog(
            vehicleName = "${vehicle.brand} ${vehicle.model}", minimumMileage = vehicle.currentMileage,
            enabled = mileageSaveEnabled, initialValue = mileageInput,
            onDismiss = { showMileageDialog = false; blockInterstitials(false) },
            onConfirm = {
                mileageInput = it
                it.toIntOrNull()?.let { km -> onMileageUpdate(km) }
                showMileageDialog = false
                blockInterstitials(false)
            }
        )
    }
}

@Composable
private fun AdaptivePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 240.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                first(Modifier.weight(1f)); second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VehicleStatusItem(
    label: String, missingLabel: String, date: Long?, isDocMissing: Boolean,
    icon: ImageVector, onClick: () -> Unit, modifier: Modifier
) {
    // Conditions historiques de l'indicateur CT/assurance conservées.
    val now = System.currentTimeMillis()
    val isExpired = date != null && date < now
    val isNear = date != null && date - now < 30L * 24 * 60 * 60 * 1000
    val color = when {
        isExpired -> MaterialTheme.colorScheme.error
        isNear -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(onClick = onClick,
        modifier = modifier.heightIn(min = CarManagerDimensions.touchTarget),
        shape = CarManagerShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(CarManagerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (date == null) Icons.Default.Info else icon, contentDescription = null,
                    tint = color, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(date?.let { DateFormatter.formatShort(it) } ?: missingLabel,
                style = CarManagerTypography.supporting, color = color)
            if (isExpired || isNear) {
                Text(if (isExpired) "Échéance dépassée" else "À prévoir",
                    style = MaterialTheme.typography.labelSmall, color = color)
            }
            if (date != null && isDocMissing) {
                Text("Document à ajouter", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardQuickActions(
    onFuelClick: () -> Unit, onMaintenanceClick: () -> Unit,
    onDocumentsClick: () -> Unit, onAdviceClick: () -> Unit, isElectric: Boolean
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            fontScale > 1.3f || maxWidth < 240.dp -> 1
            else -> 4
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall), maxItemsInEachRow = columns) {
            QuickAction(Icons.Default.EvStation, if (isElectric) "Recharge" else "Carburant", onFuelClick, Modifier.weight(1f))
            QuickAction(Icons.Default.Build, "Entretien", onMaintenanceClick, Modifier.weight(1f))
            QuickAction(Icons.Default.Folder, "Documents", onDocumentsClick, Modifier.weight(1f))
            QuickAction(Icons.Default.Lightbulb, "Conseils", onAdviceClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick,
        modifier = modifier.heightIn(min = CarManagerDimensions.touchTarget),
        shape = CarManagerShapes.control,
        contentPadding = PaddingValues(CarManagerSpacing.extraSmall)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(CarManagerSpacing.extraSmall))
            Text(label, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MileageUpdateDialog(
    vehicleName: String,
    initialValue: String,
    minimumMileage: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val units = LocalAppUnits.current
    var input by remember { mutableStateOf(initialValue) }
    var inputError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre à jour le compteur", style = CarManagerTypography.toolbarTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                Text("Saisissez le nouveau kilométrage (${units.distance}) pour $vehicleName :",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; inputError = null },
                    label = { Text("Kilométrage actuel") },
                    isError = inputError != null,
                    supportingText = { inputError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(enabled = enabled, shape = CarManagerShapes.control,
            modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget), onClick = {
            try {
                val value = com.carmanager.app.core.domain.validation.NumericInput.integer(input, "Kilométrage")
                if (value < minimumMileage) inputError = "Le compteur ne peut pas diminuer."
                else onConfirm(value.toString())
            } catch (e: com.carmanager.app.core.domain.validation.FormValidationException) {
                inputError = e.message
            }
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
