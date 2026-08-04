package com.carmanager.app.features.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.ui.components.MileageSuggestions
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.ui.theme.VehicleColor
import com.carmanager.app.core.util.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVehicleScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddEditVehicleViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val brands by viewModel.allBrands.collectAsState()
    val models by viewModel.modelsForBrand.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val units = LocalAppUnits.current

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            @Suppress("DEPRECATION")
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditMode) stringResource(R.string.vehicle_edit)
                        else stringResource(R.string.vehicle_add),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.save() }) {
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
            // SÉLECTEUR DE MARQUE
            SearchableDropdown(
                label = stringResource(R.string.vehicle_brand) + " *",
                value = viewModel.brand,
                options = brands,
                isCustom = viewModel.isCustomBrand,
                onValueChange = { valName, custom -> viewModel.onBrandChange(valName, custom) },
                isError = viewModel.showErrors && viewModel.brand.isBlank()
            )

            // SÉLECTEUR DE MODÈLE
            SearchableDropdown(
                label = stringResource(R.string.vehicle_model) + " *",
                value = viewModel.model,
                options = models,
                isCustom = viewModel.isCustomModel,
                onValueChange = { valName, custom -> viewModel.onModelChange(valName, custom) },
                isError = viewModel.showErrors && viewModel.model.isBlank(),
                enabled = viewModel.brand.isNotBlank() && !viewModel.isCustomBrand
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = viewModel.year,
                    onValueChange = viewModel::onYearChange,
                    label = { Text(stringResource(R.string.vehicle_year)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = viewModel.isYearError
                )
                OutlinedTextField(
                    value = viewModel.mileage,
                    onValueChange = viewModel::onMileageChange,
                    label = { Text(stringResource(R.string.vehicle_mileage)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { 
                        if (viewModel.isEditMode) Text("Dernier : ${viewModel.currentVehicleMileage} ${units.distance}")
                        else Text(units.distance)
                    }
                )
            }

            if (viewModel.isEditMode) {
                MileageSuggestions(
                    onIncrementSelect = viewModel::onEstimatedMileageSelect
                )
            }

            VehicleTypeSelector(
                selectedType = viewModel.vehicleType,
                onTypeSelected = viewModel::onVehicleTypeChange
            )

            FuelTypeDropdown(
                selectedType = viewModel.fuelType,
                onTypeSelected = viewModel::onFuelTypeChange
            )

            OutlinedTextField(
                value = viewModel.licensePlate,
                onValueChange = viewModel::onLicensePlateChange,
                label = { Text(stringResource(R.string.vehicle_license_plate)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = viewModel.powerHp,
                    onValueChange = viewModel::onPowerHpChange,
                    label = { Text(stringResource(R.string.vehicle_power)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = viewModel.isPowerError
                )
                
                val showTank = viewModel.fuelType != FuelType.ELECTRIC
                val showBattery = viewModel.fuelType == FuelType.ELECTRIC || viewModel.fuelType == FuelType.HYBRID

                if (showTank) {
                    OutlinedTextField(
                        value = viewModel.tankCapacity,
                        onValueChange = viewModel::onTankCapacityChange,
                        label = { Text("Réservoir (L)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = viewModel.isCapacityError
                    )
                }
                
                if (showBattery) {
                    OutlinedTextField(
                        value = viewModel.batteryCapacity,
                        onValueChange = viewModel::onBatteryCapacityChange,
                        label = { Text("Batterie (kWh)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = viewModel.isCapacityError
                    )
                }
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VehicleColor,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.large
            ) {
                @Suppress("DEPRECATION")
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.vehicle_delete_confirm)) },
            text = { Text(stringResource(R.string.vehicle_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVehicle()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdown(
    label: String,
    value: String,
    options: List<String>,
    isCustom: Boolean,
    onValueChange: (String, Boolean) -> Unit,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isCustom) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it, true) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { onValueChange("", false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Revenir à la liste")
                    }
                },
                isError = isError
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded && enabled,
                onExpandedChange = { if (enabled) expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    isError = isError,
                    enabled = enabled
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (options.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Chargement ou liste vide...", color = Color.Gray) },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onValueChange(option, false)
                                    expanded = false
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Autre (saisir manuellement)", color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onValueChange("", true)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleTypeSelector(
    selectedType: VehicleType,
    onTypeSelected: (VehicleType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Type de véhicule",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VehicleTypeItem(
                label = "Voiture",
                icon = Icons.Default.DirectionsCar,
                isSelected = selectedType == VehicleType.CAR,
                onClick = { onTypeSelected(VehicleType.CAR) },
                modifier = Modifier.weight(1f)
            )
            VehicleTypeItem(
                label = "Moto",
                icon = Icons.Default.TwoWheeler,
                isSelected = selectedType == VehicleType.MOTORCYCLE,
                onClick = { onTypeSelected(VehicleType.MOTORCYCLE) },
                modifier = Modifier.weight(1f)
            )
            VehicleTypeItem(
                label = "Utilitaire",
                icon = Icons.Default.LocalShipping,
                isSelected = selectedType == VehicleType.UTILITY,
                onClick = { onTypeSelected(VehicleType.UTILITY) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun VehicleTypeItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) VehicleColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelTypeDropdown(
    selectedType: FuelType,
    onTypeSelected: (FuelType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = getFuelTypeName(selectedType),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.vehicle_fuel_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FuelType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(getFuelTypeName(type)) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun getFuelTypeName(type: FuelType): String {
    return when (type) {
        FuelType.GASOLINE -> stringResource(R.string.fuel_gasoline)
        FuelType.DIESEL -> stringResource(R.string.fuel_diesel)
        FuelType.ELECTRIC -> stringResource(R.string.fuel_electric)
        FuelType.HYBRID -> stringResource(R.string.fuel_hybrid)
        FuelType.LPG -> stringResource(R.string.fuel_lpg)
        FuelType.OTHER -> stringResource(R.string.fuel_other)
    }
}
