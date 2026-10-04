package com.carmanager.app.features.dashboard

import com.carmanager.app.core.ui.components.CarManagerTopLevelAppBar
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerTypography
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import java.util.concurrent.TimeUnit
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
    onNavigateToVehicles: () -> Unit,
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
        topBar = { CarManagerTopLevelAppBar(title = stringResource(R.string.dashboard_title), actions = {
            val appearance = com.carmanager.app.core.ui.theme.LocalAppAppearance.current
            IconButton(onClick = viewModel::toggleAppearance, modifier = Modifier.size(48.dp)) {
                Icon(if (appearance == com.carmanager.app.core.domain.repository.AppTheme.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = if (appearance == com.carmanager.app.core.domain.repository.AppTheme.LIGHT) "Passer en mode Nuit" else "Passer en mode Jour")
            }
        }) },
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
                onNavigateToVehicles = onNavigateToVehicles,
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
    onNavigateToVehicles: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDeadlines: () -> Unit,
    mileageSaveEnabled: Boolean,
    onMileageUpdate: (Vehicle, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.medium),
        contentPadding = PaddingValues(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = bottomContentPadding)
    ) {
        item {
            DashboardSummary(stats, onNavigateToVehicles, onNavigateToStats, onNavigateToDeadlines)
        }
        if (stats.vehicles.isNotEmpty()) {
            item {
                Text("Suivi du garage", style = CarManagerTypography.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface)
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
        if (stats.upcomingDeadlines.isNotEmpty()) {
            item {
                DashboardDeadlinesPreview(stats, onNavigateToDeadlines)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSummary(stats: DashboardStats, onVehicles: () -> Unit, onStats: () -> Unit, onDeadlines: () -> Unit) {
    val units = LocalAppUnits.current
    val fontScale = LocalDensity.current.fontScale
    val totalExpenses = stats.monthlyFuelCost + stats.monthlyMaintenanceCost
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 288.dp * fontScale) 3 else 1
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small), maxItemsInEachRow = columns) {
            DashboardStatItem(if (stats.vehicleCount == 1) "Véhicule" else stringResource(R.string.nav_vehicles),
                stats.vehicleCount.toString(), Modifier.weight(1f), onClick = onVehicles)
            DashboardStatItem("Ce mois-ci", "%.0f".format(totalExpenses) + units.currency,
                Modifier.weight(1f), MaterialTheme.colorScheme.secondary, onStats)
            DashboardStatItem("Échéances", stats.upcomingDeadlines.size.toString(),
                Modifier.weight(1f), MaterialTheme.colorScheme.primary, onDeadlines)
        }
    }
}

/** Aperçu du résultat existant, sans nouvelle requête ni modification du calcul des échéances. */
@Composable
private fun DashboardDeadlinesPreview(stats: DashboardStats, onOpen: () -> Unit) {
    val units = LocalAppUnits.current
    val now = System.currentTimeMillis()
    Card(shape = CarManagerShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(CarManagerSpacing.medium)) {
            Row(Modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget)
                .clickable(role = Role.Button, onClickLabel = "Ouvrir les échéances", onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Échéances à suivre", style = CarManagerTypography.cardTitle, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            stats.upcomingDeadlines.take(3).forEach { (vehicle, record) ->
                val daysRemaining = record.nextDueDate?.let { TimeUnit.MILLISECONDS.toDays(it - now) }
                val kmRemaining = record.nextDueMileage?.let { it - vehicle.currentMileage }
                // Seuils visuels identiques à DeadlineItem : calcul métier et tri restent dans le use case.
                val isUrgent = (daysRemaining != null && daysRemaining < 7) || (kmRemaining != null && kmRemaining < 500)
                val isOverdue = (daysRemaining != null && daysRemaining < 0) || (kmRemaining != null && kmRemaining < 0)
                val color = when {
                    isOverdue -> MaterialTheme.colorScheme.error
                    isUrgent -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget)
                    .clickable(role = Role.Button, onClickLabel = "Ouvrir les échéances", onClick = onOpen)
                    .padding(vertical = CarManagerSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(getMaintenanceTypeName(record.type), style = CarManagerTypography.supporting)
                        Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        record.nextDueDate?.let { Text(DateFormatter.formatShort(it), style = MaterialTheme.typography.labelSmall) }
                        record.nextDueMileage?.let { Text("À $it ${units.distance}", style = MaterialTheme.typography.labelSmall) }
                        Text(if (isOverdue) "Échéance dépassée" else if (isUrgent) "À prévoir" else "À venir",
                            style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
            if (stats.upcomingDeadlines.size > 3) {
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget)) {
                    Text("Toutes les échéances (${stats.upcomingDeadlines.size})")
                }
            }
        }
    }
}
