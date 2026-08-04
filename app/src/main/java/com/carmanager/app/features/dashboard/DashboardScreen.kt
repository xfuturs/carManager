package com.carmanager.app.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.DashboardStats
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.ui.components.BannerAd
import com.carmanager.app.core.ui.components.DashboardStatItem
import com.carmanager.app.core.ui.components.DashboardVehicleCard
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.VehicleColor
import com.carmanager.app.core.util.DateFormatter

@Composable
fun DashboardScreen(
    canShowAds: Boolean,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onNavigateToFuel: (Long) -> Unit,
    onNavigateToMaintenance: (Long) -> Unit,
    onAdminClick: (Long, MaintenanceType) -> Unit,
    onDocumentsClick: (Long) -> Unit,
    onNavigateToAdvice: (Long) -> Unit,
    onNavigateToMileageHistory: (Long) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDeadlines: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddVehicle,
                containerColor = VehicleColor,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.vehicle_add)
                )
            }
        }
    ) { padding ->
        DashboardContent(
            stats = stats,
            canShowAds = canShowAds,
            modifier = Modifier.padding(padding),
            onAddVehicle = onAddVehicle,
            onEditVehicle = onEditVehicle,
            onNavigateToFuel = onNavigateToFuel,
            onNavigateToMaintenance = onNavigateToMaintenance,
            onAdminClick = onAdminClick,
            onDocumentsClick = onDocumentsClick,
            onNavigateToAdvice = onNavigateToAdvice,
            onNavigateToMileageHistory = onNavigateToMileageHistory,
            onNavigateToStats = onNavigateToStats,
            onNavigateToDeadlines = onNavigateToDeadlines,
            onMileageUpdate = { vehicle, newMileage -> viewModel.updateMileage(vehicle, newMileage) }
        )
        
        // Pub flottante en bas de l'écran
        if (canShowAds) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    BannerAd()
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    canShowAds: Boolean,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onNavigateToFuel: (Long) -> Unit,
    onNavigateToMaintenance: (Long) -> Unit,
    onAdminClick: (Long, MaintenanceType) -> Unit,
    onDocumentsClick: (Long) -> Unit,
    onNavigateToAdvice: (Long) -> Unit,
    onNavigateToMileageHistory: (Long) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDeadlines: () -> Unit,
    onMileageUpdate: (Vehicle, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val units = LocalAppUnits.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardStatItem(
                        label = "Véhicules",
                        value = stats.vehicleCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val totalExpenses = stats.monthlyFuelCost + stats.monthlyMaintenanceCost
                    DashboardStatItem(
                        label = "Budget",
                        value = "%.0f".format(totalExpenses) + units.currency,
                        modifier = Modifier.weight(1.1f),
                        color = MaterialTheme.colorScheme.secondary,
                        onClick = onNavigateToStats
                    )

                    DashboardStatItem(
                        label = "Échéances",
                        value = if (stats.upcomingDeadlines.isNotEmpty()) "${stats.upcomingDeadlines.size}" else "0",
                        modifier = Modifier.weight(1.1f),
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = onNavigateToDeadlines
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp).width(64.dp))
                Text(
                    text = "Mes Véhicules",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (stats.vehicles.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Aucun véhicule enregistré",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onAddVehicle,
                        colors = ButtonDefaults.buttonColors(containerColor = VehicleColor)
                    ) {
                        Text(stringResource(R.string.vehicle_add))
                    }
                }
            }
        } else {
            items(stats.vehicles, key = { it.vehicle.id }) { vehicleStats ->
                DashboardVehicleCard(
                    stats = vehicleStats,
                    onEdit = { onEditVehicle(vehicleStats.vehicle.id) },
                    onFuelClick = { onNavigateToFuel(vehicleStats.vehicle.id) },
                    onMaintenanceClick = { onNavigateToMaintenance(vehicleStats.vehicle.id) },
                    onAdminClick = { type -> onAdminClick(vehicleStats.vehicle.id, type) },
                    onDocumentsClick = { onDocumentsClick(vehicleStats.vehicle.id) },
                    onAdviceClick = { onNavigateToAdvice(vehicleStats.vehicle.id) },
                    onMileageHistoryClick = { onNavigateToMileageHistory(vehicleStats.vehicle.id) },
                    onMileageUpdate = { newMileage -> onMileageUpdate(vehicleStats.vehicle, newMileage) }
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(48.dp)) }
    }
}

@Composable
fun getMaintenanceTypeName(type: MaintenanceType): String {
    return when (type) {
        MaintenanceType.OIL_CHANGE -> "Vidange"
        MaintenanceType.TIRES -> "Pneus"
        MaintenanceType.BRAKES -> "Freins"
        MaintenanceType.BELT -> "Courroie"
        MaintenanceType.BATTERY -> "Batterie"
        MaintenanceType.INSPECTION -> "Révision"
        MaintenanceType.REPAIR -> "Réparation"
        MaintenanceType.TECHNICAL_INSPECTION -> "Contrôle Technique"
        MaintenanceType.INSURANCE -> "Assurance"
        MaintenanceType.OTHER -> "Autre"
    }
}
