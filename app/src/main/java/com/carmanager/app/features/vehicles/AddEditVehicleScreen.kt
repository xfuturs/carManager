package com.carmanager.app.features.vehicles

import com.carmanager.app.core.ui.components.*
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.ui.theme.LocalAppUnits
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
            CarManagerBackAppBar(
                title = if (viewModel.isEditMode) stringResource(R.string.vehicle_edit) else stringResource(R.string.vehicle_add),
                onNavigateBack = onNavigateBack,
                backDescription = stringResource(R.string.cancel),
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !viewModel.isSaving && !viewModel.hasSaved) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = !viewModel.isSaving && !viewModel.hasSaved) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        FormScreenContent(padding) {
            FormSection("Identité") {
                SearchableDropdown(
                    label = stringResource(R.string.vehicle_brand) + " *",
                    value = viewModel.brand,
                    options = brands,
                    isCustom = viewModel.isCustomBrand,
                    onValueChange = { valName, custom -> viewModel.onBrandChange(valName, custom) },
                    isError = viewModel.showErrors && viewModel.brand.isBlank()
                )
                SearchableDropdown(
                    label = stringResource(R.string.vehicle_model) + " *",
                    value = viewModel.model,
                    options = models,
                    isCustom = viewModel.isCustomModel,
                    onValueChange = { valName, custom -> viewModel.onModelChange(valName, custom) },
                    isError = viewModel.showErrors && viewModel.model.isBlank(),
                    enabled = viewModel.brand.isNotBlank()
                )
                FormFieldPair(first = {
                        OutlinedTextField(
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            value = viewModel.year,
                            onValueChange = viewModel::onYearChange,
                            label = { Text(stringResource(R.string.vehicle_year)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = viewModel.isYearError
                        )
                    }, second = {
                        OutlinedTextField(
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            value = viewModel.licensePlate,
                            onValueChange = viewModel::onLicensePlateChange,
                            label = { Text(stringResource(R.string.vehicle_license_plate)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                })
            }
            FormSection("Informations techniques") {
                VehicleTypeSelector(
                    selectedType = viewModel.vehicleType,
                    onTypeSelected = viewModel::onVehicleTypeChange
                )
                FuelTypeDropdown(
                    selectedType = viewModel.fuelType,
                    onTypeSelected = viewModel::onFuelTypeChange
                )
                FormFieldPair(first = {
                        OutlinedTextField(
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            value = viewModel.powerHp,
                            onValueChange = viewModel::onPowerHpChange,
                            label = { Text(stringResource(R.string.vehicle_power)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = viewModel.isPowerError
                        )
                    }, second = {
                        val showTank = viewModel.fuelType != FuelType.ELECTRIC
                        val showBattery = viewModel.fuelType == FuelType.ELECTRIC || viewModel.fuelType == FuelType.HYBRID
                        if (showTank) {
                            OutlinedTextField(
                                shape = CarManagerShapes.control,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                value = viewModel.tankCapacity,
                                onValueChange = viewModel::onTankCapacityChange,
                                label = { Text("Réservoir (L)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                isError = viewModel.isCapacityError
                            )
                        }
                        if (showBattery) {
                            OutlinedTextField(
                                shape = CarManagerShapes.control,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                value = viewModel.batteryCapacity,
                                onValueChange = viewModel::onBatteryCapacityChange,
                                label = { Text("Batterie (kWh)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                isError = viewModel.isCapacityError
                            )
                        }
                })
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
                    placeholder = {
                        if (viewModel.isEditMode) Text("Dernier : ${viewModel.currentVehicleMileage} ${units.distance}")
                        else Text(units.distance)
                    }
                )
                if (viewModel.isEditMode) {
                    MileageSuggestions(
                        onIncrementSelect = viewModel::onEstimatedMileageSelect
                    )
                }
            }
            FormSaveAction(stringResource(R.string.save),
                enabled = !viewModel.isSaving && !viewModel.hasSaved,
                onClick = { viewModel.save() })
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
                shape = CarManagerShapes.control,
                textStyle = MaterialTheme.typography.bodyMedium,
                value = value,
                onValueChange = { onValueChange(it, true) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { onValueChange("", false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Revenir à la liste")
                    }
                },
                isError = isError,
                enabled = enabled
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded && enabled,
                onExpandedChange = { if (enabled) expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
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
                            text = { Text("Chargement ou liste vide...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VehicleTypeItem(
                label = "Voiture",
                icon = Icons.Default.DirectionsCar,
                isSelected = selectedType == VehicleType.CAR,
                onClick = { onTypeSelected(VehicleType.CAR) },
                modifier = Modifier
            )
            VehicleTypeItem(
                label = "Moto",
                icon = Icons.Default.TwoWheeler,
                isSelected = selectedType == VehicleType.MOTORCYCLE,
                onClick = { onTypeSelected(VehicleType.MOTORCYCLE) },
                modifier = Modifier
            )
            VehicleTypeItem(
                label = "Utilitaire",
                icon = Icons.Default.LocalShipping,
                isSelected = selectedType == VehicleType.UTILITY,
                onClick = { onTypeSelected(VehicleType.UTILITY) },
                modifier = Modifier
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
    Surface(
        modifier = modifier.heightIn(min = CarManagerDimensions.touchTarget)
        .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = CarManagerShapes.control,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(CarManagerSpacing.small), horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
            shape = CarManagerShapes.control,
            textStyle = MaterialTheme.typography.bodyMedium,
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
