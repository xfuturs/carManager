package com.carmanager.app.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.ui.components.StatCard
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.OnFuelColor
import com.carmanager.app.core.ui.theme.MaintenanceColor
import com.carmanager.app.core.ui.theme.VehicleColor
import com.carmanager.app.core.util.DateFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.uiState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val units = LocalAppUnits.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Statistiques Globales", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    "Répartition des dépenses ce mois",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "Carburant",
                        value = "%.2f".format(stats.monthlyFuelCost) + " " + units.currency,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Entretien",
                        value = "%.2f".format(stats.monthlyMaintenanceCost) + " " + units.currency,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Performance par véhicule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            items(stats.vehicles) { vehicleStats ->
                VehicleStatsSummary(
                    stats = vehicleStats,
                    isPremium = isPremium,
                    onGenerateReport = { viewModel.generateReport(context, vehicleStats.vehicle.id) }
                )
            }
        }
    }
}

@Composable
fun VehicleStatsSummary(
    stats: com.carmanager.app.core.domain.model.VehicleStats,
    isPremium: Boolean,
    onGenerateReport: () -> Unit
) {
    val vehicle = stats.vehicle
    val units = LocalAppUnits.current
    val fuelUnit = if (vehicle.fuelType == FuelType.ELECTRIC) "kWh/100" else "L/100"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${vehicle.brand} ${vehicle.model}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VehicleColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn(label = "Conso. Moy.", value = if (stats.fuelRecordsCount > 1) "%.1f".format(stats.averageConsumption) + " " + fuelUnit else "-- $fuelUnit")
                StatColumn(label = "Dépenses Totales", value = "%.0f".format(stats.totalFuelCost + stats.yearlyMaintenanceCost) + " " + units.currency)
                StatColumn(label = "Distance", value = "${stats.distanceTracked} ${units.distance}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Dernier relevé : ${DateFormatter.formatShort(vehicle.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (stats.consumptionHistory.size > 1) {
                Spacer(modifier = Modifier.height(24.dp))
                ConsumptionGraph(stats.consumptionHistory)
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onGenerateReport,
                modifier = Modifier.fillMaxWidth(),
                enabled = isPremium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremium) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPremium) "GÉNÉRER RAPPORT REVENTE (PDF)" else "RAPPORT PDF (PREMIUM UNIQUEMENT)")
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConsumptionGraph(history: List<Double>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(history) {
        modelProducer.runTransaction {
            lineSeries { series(history) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Évolution de la consommation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        CartesianChartHost(
            chart = rememberCartesianChart(rememberLineCartesianLayer()),
            modelProducer = modelProducer,
            modifier = Modifier.height(120.dp).fillMaxWidth()
        )
    }
}
