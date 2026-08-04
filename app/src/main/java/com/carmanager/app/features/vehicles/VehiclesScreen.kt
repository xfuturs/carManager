package com.carmanager.app.features.vehicles

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.components.BannerAd
import com.carmanager.app.core.ui.components.VehicleItem
import com.carmanager.app.core.ui.theme.VehicleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    canShowAds: Boolean,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onNavigateToFuel: (Long) -> Unit,
    onNavigateToMaintenance: (Long) -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehicles.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.vehicles_title), fontWeight = FontWeight.Bold) }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddVehicle,
                    containerColor = VehicleColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vehicle_add))
                }
            }
        ) { padding ->
            if (vehicles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vehicles) { vehicle ->
                        VehicleItem(
                            vehicle = vehicle,
                            onClick = { onNavigateToFuel(vehicle.id) },
                            onEdit = { onEditVehicle(vehicle.id) },
                            onFuelClick = { onNavigateToFuel(vehicle.id) },
                            onMaintenanceClick = { onNavigateToMaintenance(vehicle.id) },
                            onDelete = { viewModel.deleteVehicle(vehicle) }
                        )
                    }
                    // Spacer pour ne pas cacher le dernier item par la pub
                    if (canShowAds) {
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // Pub flottante en bas
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
