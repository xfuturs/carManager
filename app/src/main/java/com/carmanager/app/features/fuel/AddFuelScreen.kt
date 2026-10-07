package com.carmanager.app.features.fuel

import com.carmanager.app.core.ui.components.*
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.ui.theme.LocalAppUnits
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
        FormScreenContent(padding) {
            com.carmanager.app.core.ui.components.FormLoadFailure(viewModel.loadError, viewModel::retryLoading)
            if (viewModel.fuelType == FuelType.HYBRID) {
                FormSection("Énergie") {
                    EnergyTypeSelector(
                        isElectric = viewModel.isElectricEntry,
                        onToggle = viewModel::onElectricEntryToggle
                    )
                }
            }
            FormSection("Kilométrage") {
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
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
            }
            FormSection("Quantité et coût") {
                FormFieldPair(first = {
                        OutlinedTextField(
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            value = viewModel.liters,
                            onValueChange = viewModel::onLitersChange,
                            label = { Text(if (viewModel.isElectricEntry) "kWh *" else stringResource(R.string.fuel_liters) + " *") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = viewModel.showErrors && viewModel.liters.isBlank()
                        )
                    }, second = {
                        OutlinedTextField(
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            value = viewModel.totalPrice,
                            onValueChange = viewModel::onTotalPriceChange,
                            label = { Text(stringResource(R.string.fuel_total_price) + " (${units.currency}) *") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = viewModel.showErrors && viewModel.totalPrice.isBlank()
                        )
                })
                val capacity = if (viewModel.isElectricEntry) viewModel.batteryCapacity else viewModel.tankCapacity
                capacity?.let { cap ->
                    EnergySuggestions(
                        capacity = cap,
                        isElectric = viewModel.isElectricEntry,
                        onSelect = viewModel::onLitersChange
                    )
                }
            }
            FormSection("Date et détails") {
                DatePickerField(
                    label = if (viewModel.isElectricEntry) "Date de la recharge" else stringResource(R.string.fuel_date),
                    selectedDate = viewModel.date,
                    onDateSelected = viewModel::onDateChange
                )
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = viewModel.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.fuel_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedButton(
                    onClick = { pickerLauncher.launch("image/*") },
                    shape = CarManagerShapes.control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    if (viewModel.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ocr_scan_fuel), style = MaterialTheme.typography.labelSmall)
                    }
                }

            }
            FormSaveAction(stringResource(R.string.save),
                enabled = !viewModel.isSaving && !viewModel.hasSaved && viewModel.isVehicleLoaded && !viewModel.isScanning,
                onClick = { viewModel.save() })
        }

    }
}

@Composable
fun EnergyTypeSelector(
    isElectric: Boolean,
    onToggle: (Boolean) -> Unit
) {
    TabRow(selectedTabIndex = if (isElectric) 1 else 0) {
        Tab(modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget), selected = !isElectric, onClick = { onToggle(false) }) {
            @Suppress("DEPRECATION")
            Text("Carburant", modifier = Modifier.padding(8.dp))
        }
        Tab(modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget), selected = isElectric, onClick = { onToggle(true) }) {
            @Suppress("DEPRECATION")
            Text("Électricité", modifier = Modifier.padding(8.dp))
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
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
                    modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                    shape = CarManagerShapes.control,
                    onClick = { onSelect(amount.toString()) },
                    label = { Text("${amount}$unit") }
                )
            }
        }
    }
}
