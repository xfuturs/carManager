package com.carmanager.app.features.mileage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.components.*
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography
import com.carmanager.app.core.util.DateFormatter
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.ui.theme.LocalAppUnits

@Composable
fun MileageHistoryScreen(onNavigateBack: () -> Unit, viewModel: MileageHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val units = LocalAppUnits.current
    Scaffold(topBar = { CarManagerBackAppBar(title = "Historique du compteur", onNavigateBack = onNavigateBack) }) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { history ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                if (history.isEmpty()) {
                    item { CompactEmptyState(Icons.Default.Speed, "Aucun relevé enregistré",
                        "Les relevés manuels, pleins et entretiens apparaîtront ici.") }
                } else {
                    items(history, key = { it.id }) { record -> MileageHistoryItem(record, units.distance) }
                }
            }
        }
    }
}

@Composable
private fun MileageHistoryItem(record: MileageRecord, distanceUnit: String) {
    val sourceInfo = when(record.source) {
        MileageSource.MANUAL -> "Saisie manuelle" to Icons.Default.Edit
        MileageSource.FUEL -> "Plein de carburant" to Icons.Default.EvStation
        MileageSource.MAINTENANCE -> "Entretien / Réparation" to Icons.Default.Build
    }

    RecordRow(sourceInfo.second) {
        Text("${record.mileage} $distanceUnit", style = CarManagerTypography.cardTitle, color = MaterialTheme.colorScheme.primary)
        Text(DateFormatter.formatMedium(record.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(sourceInfo.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
