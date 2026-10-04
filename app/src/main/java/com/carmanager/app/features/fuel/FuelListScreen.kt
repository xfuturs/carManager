package com.carmanager.app.features.fuel

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

@Composable
fun FuelListScreen(
    onAddFuel: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FuelListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { CarManagerBackAppBar(title = stringResource(R.string.fuel_title),
            onNavigateBack = onNavigateBack, backDescription = stringResource(R.string.cancel)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddFuel(viewModel.vehicleId) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fuel_add))
            }
        }
    ) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { records ->
            val groupedRecords = remember(records) { records.groupBy { DateFormatter.formatMonthYear(it.date) } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                if (records.isEmpty()) {
                    item { CompactEmptyState(Icons.Default.LocalGasStation, stringResource(R.string.fuel_empty), "Ajoutez un plein ou une recharge pour commencer le suivi.") }
                } else {
                    groupedRecords.forEach { (month, monthRecords) ->
                        item {
                            Text(month.replaceFirstChar { it.uppercase() }, style = CarManagerTypography.supporting,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(top = CarManagerSpacing.small).semantics { heading() })
                        }
                        items(monthRecords, key = { it.id }) { record -> FuelRecordItem(record = record) }
                    }
                }
            }
        }
    }
}
