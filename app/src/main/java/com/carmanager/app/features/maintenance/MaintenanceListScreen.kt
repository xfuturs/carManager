package com.carmanager.app.features.maintenance

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.components.MaintenanceRecordItem
import com.carmanager.app.core.util.DateFormatter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceListScreen(
    onAddMaintenance: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MaintenanceListViewModel = hiltViewModel()
) {
    val records by viewModel.maintenanceRecords.collectAsState()

    val groupedRecords = remember(records) {
        records.groupBy { record ->
            DateFormatter.formatMonthYear(record.date)
        }
    }

    Scaffold(
        topBar = {
            CarManagerBackAppBar(
                title = stringResource(R.string.maintenance_title),
                onNavigateBack = onNavigateBack,
                backDescription = stringResource(R.string.cancel)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddMaintenance(viewModel.vehicleId) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.maintenance_add))
            }
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.maintenance_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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
                groupedRecords.forEach { (month, monthRecords) ->
                    item {
                        Text(
                            text = month.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    items(monthRecords) { record ->
                        MaintenanceRecordItem(record = record)
                    }
                }
            }
        }
    }
}
