package com.carmanager.app.features.fuel

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.ui.components.DatePickerField
import com.carmanager.app.core.ui.components.MileageSuggestions
import com.carmanager.app.core.ui.theme.FuelColor
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.SuccessGreen
import com.carmanager.app.core.util.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFuelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddFuelViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val units = LocalAppUnits.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onScanReceipt(it) }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.Success -> onNavigateBack()
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(message = event.message)
                }
            }
        }
    }

    val screenTitle = if (viewModel.isElectricEntry) "Ajouter une recharge" else stringResource(R.string.fuel_add)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CarManagerBackAppBar(
                title = screenTitle,
                onNavigateBack = onNavigateBack,
                backDescription = stringResource(R.string.cancel),
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !viewModel.isSaving && !viewModel.hasSaved && viewModel.isVehicleLoaded && !viewModel.isScanning) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sélecteur pour Hybride
            if (viewModel.fuelType == FuelType.HYBRID) {
                EnergyTypeSelector(
                    isElectric = viewModel.isElectricEntry,
                    onToggle = viewModel::onElectricEntryToggle
                )
            }

            // Bouton de Scan IA
            OutlinedButton(
                onClick = { pickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FuelColor)
            ) {
                if (viewModel.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FuelColor)
                } else {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (viewModel.isElectricEntry) "Scanner facture borne (IA)" else stringResource(R.string.ocr_scan_fuel))
                }
            }

            DatePickerField(
                label = if (viewModel.isElectricEntry) "Date de la recharge" else stringResource(R.string.fuel_date),
                selectedDate = viewModel.date,
                onDateSelected = viewModel::onDateChange
            )

            OutlinedTextField(
                value = viewModel.mileage,
                onValueChange = viewModel::onMileageChange,
                label = { Text(stringResource(R.string.vehicle_mileage)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("Dernier : ${viewModel.currentVehicleMileage} ${units.distance}") }
            )

            MileageSuggestions(
                onIncrementSelect = viewModel::onEstimatedMileageSelect
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = viewModel.liters,
                    onValueChange = viewModel::onLitersChange,
                    label = { Text(if (viewModel.isElectricEntry) "kWh *" else stringResource(R.string.fuel_liters) + " *") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = viewModel.showErrors && viewModel.liters.isBlank()
                )
                OutlinedTextField(
                    value = viewModel.totalPrice,
                    onValueChange = viewModel::onTotalPriceChange,
                    label = { Text(stringResource(R.string.fuel_total_price) + " (${units.currency}) *") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = viewModel.showErrors && viewModel.totalPrice.isBlank()
                )
            }

            // Suggestions adaptatives
            val capacity = if (viewModel.isElectricEntry) viewModel.batteryCapacity else viewModel.tankCapacity
            capacity?.let { cap ->
                EnergySuggestions(
                    capacity = cap,
                    isElectric = viewModel.isElectricEntry,
                    onSelect = viewModel::onLitersChange
                )
            }

            OutlinedTextField(
                value = viewModel.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.fuel_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Button(
                onClick = { viewModel.save() },
                enabled = !viewModel.isSaving && !viewModel.hasSaved && viewModel.isVehicleLoaded && !viewModel.isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun EnergyTypeSelector(
    isElectric: Boolean,
    onToggle: (Boolean) -> Unit
) {
    TabRow(selectedTabIndex = if (isElectric) 1 else 0) {
        Tab(selected = !isElectric, onClick = { onToggle(false) }) {
            @Suppress("DEPRECATION")
            Text("Essence / Gazoil", modifier = Modifier.padding(16.dp))
        }
        Tab(selected = isElectric, onClick = { onToggle(true) }) {
            @Suppress("DEPRECATION")
            Text("Électricité", modifier = Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnergySuggestions(capacity: Double, isElectric: Boolean, onSelect: (String) -> Unit) {
    val unit = if (isElectric) "kWh" else "L"
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Volume ($unit) - Capacité: ${capacity}$unit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0.25, 0.4, 0.5, 0.6, 0.75, 0.8, 0.9, 1.0).forEach { ratio ->
                val amount = (capacity * ratio).toInt()
                SuggestionChip(
                    onClick = { onSelect(amount.toString()) },
                    label = { Text("${amount}$unit") }
                )
            }
        }
    }
}
