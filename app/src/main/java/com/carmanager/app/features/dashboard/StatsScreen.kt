@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmanager.app.features.dashboard

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.BuildConfig
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.ui.components.*
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.util.DateFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    DashboardLifecycle(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val units = LocalAppUnits.current
    val snackbar = remember { SnackbarHostState() }
    val reportActions = com.carmanager.app.features.documents.reportUiActions(snackbar)
    val generatedReport by viewModel.generatedReport.collectAsStateWithLifecycle()
    val draft by viewModel.reportDraft.collectAsStateWithLifecycle()
    draft?.let { ReportConfigurationDialog(it, viewModel::toggleReportSection, viewModel::cancelReportDraft, {
        if (isPremium || BuildConfig.DEBUG) viewModel.confirmReportDraft()
        else viewModel.cancelReportDraft()
    }) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                if (event is com.carmanager.app.core.util.UiEvent.ShowSnackbar) snackbar.showSnackbar(event.message)
            }
        }
    }
    generatedReport?.let { report ->
        AlertDialog(onDismissRequest = viewModel::closeReportResult,
            title = { Text("Rapport enregistré dans Car Manager") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(report.title)
                Text("Retrouvez-le dans Documents > Rapports Car Manager.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { reportActions.save(report); viewModel.closeReportResult() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Enregistrer une copie") }
                TextButton(onClick = { reportActions.share(report); viewModel.closeReportResult() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Partager") }
                TextButton(onClick = { reportActions.open(report); viewModel.closeReportResult() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ouvrir") }
            } }, confirmButton = { TextButton(onClick = viewModel::closeReportResult) { Text("Fermer") } })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { CarManagerBackAppBar(title = "Statistiques globales", onNavigateBack = onNavigateBack) }) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { stats ->
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (viewModel.isPreparingReport || viewModel.isGeneratingReport) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (stats.vehicles.isEmpty()) {
                    item { CompactEmptyState(Icons.Default.BarChart, "Aucune statistique pour le moment",
                        "Ajoutez un véhicule, puis des pleins ou des entretiens pour suivre vos dépenses.") }
                } else {
                    item { SecondarySectionTitle("Dépenses ce mois") }
                    item {
                        StatsMetricGroup {
                            StatsMetric("Carburant", com.carmanager.app.core.util.CurrencyPresentation.format(stats.monthlyFuelCost, units.currency))
                            StatsMetric("Entretien", com.carmanager.app.core.util.CurrencyPresentation.format(stats.monthlyMaintenanceCost, units.currency))
                        }
                    }
                    item { SecondarySectionTitle("Performance par véhicule") }
                    items(stats.vehicles, key = { it.vehicle.id }) { vehicleStats ->
                        VehicleStatsSummary(vehicleStats, isPremium) { viewModel.prepareReport(vehicleStats.vehicle.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleStatsSummary(stats: com.carmanager.app.core.domain.model.VehicleStats, isPremium: Boolean, onGenerateReport: () -> Unit) {
    val vehicle = stats.vehicle
    val units = LocalAppUnits.current
    val fuelUnit = if (vehicle.fuelType == FuelType.ELECTRIC) "kWh/100 km" else "L/100 km"

    SecondaryPanel {
        SecondarySectionTitle("${vehicle.brand} ${vehicle.model}")
        StatsMetricGroup {
            StatsMetric("Consommation moyenne", VehicleStatsPresentation.consumption(stats))
            StatsMetric("Dépenses totales", com.carmanager.app.core.util.CurrencyPresentation.format(stats.totalExpenses, units.currency, 0))
            StatsMetric("Distance", com.carmanager.app.core.util.DistancePresentation.recorded(stats.distanceTracked, units.distance))
        }
        Text("Dernier relevé : ${DateFormatter.formatShort(vehicle.updatedAt)}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (stats.consumptionHistory.size > 1) {
            ConsumptionGraph(stats.consumptionHistory, fuelUnit)
        } else {
            Text("Évolution de la consommation : données insuffisantes. Ajoutez des pleins ou recharges pour afficher la courbe.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isPremium) {
            OutlinedButton(onClick = onGenerateReport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = CarManagerShapes.control) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Générer le rapport de revente (PDF)", Modifier.weight(1f))
            }
        } else {
            Surface(Modifier.fillMaxWidth(), shape = CarManagerShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rapport de revente PDF · Premium", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Fonction verrouillée. Activez Premium dans Paramètres pour générer et partager ce rapport.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // La garde constante retire cette action du code compilé en release.
                        if (BuildConfig.DEBUG && PdfAccessPolicy.resolve(BuildConfig.DEBUG, isPremium) == PdfAccess.DEBUG_TEST) {
                            DebugPdfTestAction(onGenerateReport)
                        }
                    }
                }
            }
        }
    }
}

/** Les tuiles se replient ; aucune hauteur fixe ne contraint les valeurs agrandies. */
@Composable
private fun StatsMetricGroup(content: @Composable FlowRowScope.() -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 320.dp * fontScale && fontScale <= 1.3f) 2 else 1
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun FlowRowScope.StatsMetric(label: String, value: String) {
    Surface(Modifier.weight(1f), shape = CarManagerShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConsumptionGraph(history: List<Double>, fuelUnit: String) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(history) {
        modelProducer.runTransaction { lineSeries { series(history) } }
    }
    val line = rememberLine(fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.primary)))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Évolution de la consommation ($fuelUnit)", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CartesianChartHost(chart = rememberCartesianChart(rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(line))), modelProducer = modelProducer,
            modifier = Modifier.height(160.dp).fillMaxWidth().semantics {
                contentDescription = "Consommation, ${history.size} relevés dans l'ordre existant, en $fuelUnit : " +
                    history.joinToString(" ; ") { "%.1f".format(it) }
            })
    }
}
