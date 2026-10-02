package com.carmanager.app.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.VehicleStats
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.ui.theme.*
import com.carmanager.app.core.util.DateFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

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
    var showMileageDialog by remember { mutableStateOf(false) }
    var mileageInput by remember { mutableStateOf(stats.vehicle.currentMileage.toString()) }
    val vehicle = stats.vehicle

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VehicleHeader(
                vehicle = vehicle,
                hasAlerts = stats.alerts.isNotEmpty(),
                onEdit = onEdit,
                onDocumentsClick = onDocumentsClick
            )

            if (stats.alerts.isNotEmpty()) {
                AlertsList(stats.alerts)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BLOC ODOMÈTRE CENTRAL (Kilométrage proéminent)
            OdometerBlock(
                mileage = vehicle.currentMileage,
                onUpdateClick = { if (mileageSaveEnabled) { mileageInput = vehicle.currentMileage.toString(); showMileageDialog = true } },
                onHistoryClick = onMileageHistoryClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            AdminSection(
                nextCTDate = stats.nextCTDate,
                nextInsuranceDate = stats.nextInsuranceDate,
                isCTDocMissing = stats.isCTDocMissing,
                isInsuranceDocMissing = stats.isInsuranceDocMissing,
                onAdminClick = onAdminClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            MainCostsRow(
                monthlyFuelCost = stats.monthlyFuelCost,
                yearlyMaintenanceCost = stats.yearlyMaintenanceCost
            )

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                onFuelClick = onFuelClick,
                onMaintenanceClick = onMaintenanceClick,
                onPhotosClick = onDocumentsClick,
                onAdviceClick = onAdviceClick
            )
        }
    }

    if (showMileageDialog) {
        MileageUpdateDialog(
            vehicleName = "${vehicle.brand} ${vehicle.model}",
            minimumMileage = vehicle.currentMileage,
            enabled = mileageSaveEnabled,
            initialValue = mileageInput,
            onDismiss = { showMileageDialog = false },
            onConfirm = { 
                mileageInput = it
                it.toIntOrNull()?.let { km -> onMileageUpdate(km) }
                showMileageDialog = false
            }
        )
    }
}

@Composable
private fun VehicleHeader(
    vehicle: com.carmanager.app.core.domain.model.Vehicle,
    hasAlerts: Boolean,
    onEdit: () -> Unit,
    onDocumentsClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val colors = LocalCategoryColors.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val vehicleIcon = when (vehicle.type) {
            VehicleType.CAR -> Icons.Default.DirectionsCar
            VehicleType.MOTORCYCLE -> Icons.Default.TwoWheeler
            VehicleType.UTILITY -> Icons.Default.LocalShipping
        }
        
        // Icône à gauche (Start)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = vehicleIcon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (hasAlerts) MaterialTheme.colorScheme.error else colors.vehicle
            )
            if (hasAlerts) {
                Surface(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd)
                        .scale(scale),
                    color = MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        "!", 
                        color = Color.White, 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Marque et Modèle au CENTRE de la carte
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${vehicle.brand} ${vehicle.model}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${vehicle.licensePlate ?: "Sans plaque"} • ${vehicle.year}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Boutons d'action à droite (End)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDocumentsClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp), tint = colors.admin)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun OdometerBlock(
    mileage: Int,
    onUpdateClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val units = LocalAppUnits.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable { onUpdateClick() }
            ) {
                Text(
                    text = mileage.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "KILOMÉTRAGE (${units.distance})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            VerticalDivider(modifier = Modifier.height(32.dp).width(1.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            IconButton(onClick = onHistoryClick) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Historique",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AlertsList(alerts: List<String>) {
    Spacer(modifier = Modifier.height(12.dp))
    alerts.forEach { alert ->
        Text(
            text = alert,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AdminSection(
    nextCTDate: Long?,
    nextInsuranceDate: Long?,
    isCTDocMissing: Boolean,
    isInsuranceDocMissing: Boolean,
    onAdminClick: (MaintenanceType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AdminIndicator(
                label = "CT",
                date = nextCTDate,
                isDocMissing = isCTDocMissing,
                icon = Icons.Default.Search,
                onClick = { onAdminClick(MaintenanceType.TECHNICAL_INSPECTION) }
            )
        }
        
        VerticalDivider(modifier = Modifier.height(32.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AdminIndicator(
                label = "Assurance",
                date = nextInsuranceDate,
                isDocMissing = isInsuranceDocMissing,
                icon = Icons.Default.Shield,
                onClick = { onAdminClick(MaintenanceType.INSURANCE) }
            )
        }
    }
}

@Composable
private fun MainCostsRow(
    monthlyFuelCost: Double,
    yearlyMaintenanceCost: Double
) {
    val colors = LocalCategoryColors.current
    val units = LocalAppUnits.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StatColumn(label = "Carburant (Mois)", value = "%.0f".format(monthlyFuelCost) + units.currency, color = colors.onFuel)
        }

        VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StatColumn(label = "Entretien (An)", value = "%.0f".format(yearlyMaintenanceCost) + units.currency, color = colors.maintenance)
        }
    }
}

@Composable
private fun ActionButtons(
    onFuelClick: () -> Unit, 
    onMaintenanceClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onAdviceClick: () -> Unit
) {
    val colors = LocalCategoryColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LargeActionButton(Icons.Default.EvStation, "CARBURANT", onFuelClick, Modifier.weight(1f), colors.fuelContainer, colors.onFuel)
            LargeActionButton(Icons.Default.Build, "ENTRETIEN", onMaintenanceClick, Modifier.weight(1f), colors.maintenanceContainer, colors.maintenance)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LargeActionButton(Icons.Default.PhotoLibrary, "PHOTOS", onPhotosClick, Modifier.weight(1f), colors.adminContainer, colors.admin)
            LargeActionButton(Icons.Default.Lightbulb, "CONSEILS", onAdviceClick, Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
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
        title = { Text("Mise à jour du kilométrage", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Saisissez le nouveau kilométrage (${units.distance}) pour $vehicleName :", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; inputError = null },
                    label = { Text("Kilométrage actuel") },
                    isError = inputError != null,
                    supportingText = { inputError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(enabled = enabled, onClick = {
            try {
                val value = com.carmanager.app.core.domain.validation.NumericInput.integer(input, "Kilométrage")
                if (value < minimumMileage) inputError = "Le compteur ne peut pas diminuer."
                else onConfirm(value.toString())
            } catch (e: com.carmanager.app.core.domain.validation.FormValidationException) {
                inputError = e.message
            }
        }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminIndicator(
    label: String, 
    date: Long?, 
    isDocMissing: Boolean,
    icon: ImageVector, 
    onClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isExpired = date != null && date < now
    val isNear = date != null && date - now < 30L * 24 * 60 * 60 * 1000
    
    val showBadge = date == null || isDocMissing
    val colors = LocalCategoryColors.current

    val color = when {
        isExpired -> MaterialTheme.colorScheme.error
        isNear -> Color(0xFFFFB74D) // Orange plus clair pour le mode nuit
        date == null -> MaterialTheme.colorScheme.outline
        else -> colors.admin
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseBadge")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        BadgedBox(
            badge = {
                if (showBadge) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.scale(scale)
                    ) {
                        Text("!", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                }
            }
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp), tint = color)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        val dateText = when {
            date == null -> "non renseigné"
            isDocMissing -> "Doc. manquant"
            else -> DateFormatter.formatShort(date)
        }

        Text(
            text = dateText,
            style = if (date == null || isDocMissing) MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic) else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (date == null || isDocMissing) MaterialTheme.colorScheme.onSurfaceVariant else color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LargeActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, containerColor: Color, contentColor: Color) {
    Card(onClick = onClick, modifier = modifier.height(48.dp), colors = CardDefaults.cardColors(containerColor, contentColor), elevation = CardDefaults.cardElevation(0.dp), shape = MaterialTheme.shapes.large) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
