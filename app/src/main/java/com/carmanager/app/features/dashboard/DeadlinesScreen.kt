package com.carmanager.app.features.dashboard

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.ui.components.LocalDataContent
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    DashboardLifecycle(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CarManagerBackAppBar(
                title = "Échéances à venir",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { stats ->
            if (stats.upcomingDeadlines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun rendez-vous prévu", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(stats.upcomingDeadlines) { (vehicle, record) ->
                        DeadlineItem(vehicle, record, requireNotNull(stats.temporalContext))
                    }
                }
            }
        }
    }
}

@Composable
fun DeadlineItem(vehicle: Vehicle, record: MaintenanceRecord, time: com.carmanager.app.core.domain.model.TemporalContext) {
    val units = LocalAppUnits.current
    val daysRemaining = record.nextDueDate?.let { 
        time.daysUntil(it)
    }
    
    val kmRemaining = record.nextDueMileage?.let { 
        it - vehicle.currentMileage
    }

    val isUrgent = (daysRemaining != null && daysRemaining < 7) || (kmRemaining != null && kmRemaining < 500)
    val isOverdue = (daysRemaining != null && daysRemaining < 0) || (kmRemaining != null && kmRemaining < 0)

    val color = when {
        isOverdue -> MaterialTheme.colorScheme.error
        isUrgent -> Color(0xFFFFA000) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getMaintenanceTypeName(record.type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${vehicle.brand} ${vehicle.model}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (daysRemaining != null) {
                    Text(
                        text = if (daysRemaining < 0) "Dépassement" else "J - $daysRemaining",
                        style = MaterialTheme.typography.titleLarge,
                        color = color,
                        fontWeight = FontWeight.Black
                    )
                }
                if (kmRemaining != null) {
                    Text(
                        text = if (kmRemaining < 0) "Kilométrage +" else "${com.carmanager.app.core.util.DistancePresentation.recorded(kmRemaining, units.distance)} restants",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (kmRemaining < 0) color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
