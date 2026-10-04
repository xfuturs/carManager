package com.carmanager.app.features.vehicles

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.components.BannerAdSlot
import com.carmanager.app.core.ui.components.LocalDataContent
import com.carmanager.app.core.ui.components.VehicleItem
import com.carmanager.app.core.ui.theme.VehicleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    canShowAds: Boolean,
    onNavigateBack: () -> Unit,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onNavigateToFuel: (Long) -> Unit,
    onNavigateToMaintenance: (Long) -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var fabHeightPixels by remember { mutableIntStateOf(0) }
    val fabClearance = with(LocalDensity.current) { fabHeightPixels.toDp() } + 32.dp

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            CarManagerBackAppBar(title = stringResource(R.string.vehicles_title), onNavigateBack = onNavigateBack)
        },
        bottomBar = { if (canShowAds) BannerAdSlot() },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.onSizeChanged { fabHeightPixels = it.height },
                onClick = onAddVehicle,
                containerColor = VehicleColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vehicle_add))
            }
        }
    ) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { vehicles ->
            if (vehicles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.vehicles_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = fabClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        VehicleItem(
                            vehicle = vehicle,
                            onClick = { onNavigateToFuel(vehicle.id) },
                            onEdit = { onEditVehicle(vehicle.id) },
                            onFuelClick = { onNavigateToFuel(vehicle.id) },
                            onMaintenanceClick = { onNavigateToMaintenance(vehicle.id) },
                            onDelete = { viewModel.deleteVehicle(vehicle) }
                        )
                    }
                }
            }
        }
    }

}
