package com.carmanager.app.features.dashboard

import com.carmanager.app.core.ui.components.CarManagerTopLevelAppBar
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.carmanager.app.core.util.UiEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.DashboardStats
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.ui.components.BannerAdSlot
import com.carmanager.app.core.ui.components.LocalDataContent
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
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var fabHeightPixels by remember { mutableIntStateOf(0) }
    val fabClearance = with(LocalDensity.current) { fabHeightPixels.toDp() } + 32.dp
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            if (event is UiEvent.ShowSnackbar) snackbarHostState.showSnackbar(event.message)
        }
    }

    Scaffold(
        topBar = { CarManagerTopLevelAppBar(title = stringResource(R.string.dashboard_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (canShowAds) BannerAdSlot() },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.onSizeChanged { fabHeightPixels = it.height },
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
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { stats ->
            DashboardContent(
                stats = stats,
                bottomContentPadding = fabClearance,
                modifier = Modifier,
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
                mileageSaveEnabled = !viewModel.isUpdatingMileage,
                onMileageUpdate = { vehicle, newMileage -> viewModel.updateMileage(vehicle, newMileage) }
            )
        }
        
    }
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    bottomContentPadding: Dp,
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
    mileageSaveEnabled: Boolean,
    onMileageUpdate: (Vehicle, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val units = LocalAppUnits.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = bottomContentPadding)
    ) {
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
                    mileageSaveEnabled = mileageSaveEnabled,
                    onMileageUpdate = { newMileage -> onMileageUpdate(vehicleStats.vehicle, newMileage) }
                )
            }
        }
        
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
