package com.carmanager.app.features.maintenance

import com.carmanager.app.core.ui.components.*
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.ui.theme.LocalAppUnits
import com.carmanager.app.core.util.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaintenanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddMaintenanceViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val units = LocalAppUnits.current
    val context = LocalContext.current

    fun notificationPermissionStatus(): NotificationPermissionStatus = NotificationPermissionStatus.from(
        sdkInt = Build.VERSION.SDK_INT,
        granted = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    LaunchedEffect(viewModel) {
        viewModel.notificationPermissionRequests.collect {
            // La permission a pu changer depuis le clic ; ne pas afficher un dialogue devenu inutile.
            if (Build.VERSION.SDK_INT >= 33 && notificationPermissionStatus() == NotificationPermissionStatus.MISSING) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationPermissionResult(granted = true)
            }
        }
    }

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

    @Composable
    fun getMaintenanceTypeName(type: MaintenanceType): String {
        return when (type) {
            MaintenanceType.OIL_CHANGE -> stringResource(R.string.maintenance_oil_change)
            MaintenanceType.TIRES -> stringResource(R.string.maintenance_tires)
            MaintenanceType.BRAKES -> stringResource(R.string.maintenance_brakes)
            MaintenanceType.BELT -> stringResource(R.string.maintenance_belt)
            MaintenanceType.BATTERY -> stringResource(R.string.maintenance_battery)
            MaintenanceType.INSPECTION -> stringResource(R.string.maintenance_inspection)
            MaintenanceType.REPAIR -> stringResource(R.string.maintenance_repair)
            MaintenanceType.TECHNICAL_INSPECTION -> stringResource(R.string.maintenance_technical_inspection)
            MaintenanceType.INSURANCE -> stringResource(R.string.maintenance_insurance)
            MaintenanceType.OTHER -> stringResource(R.string.maintenance_other)
        }
    }

    val screenTitle = when (viewModel.type) {
        MaintenanceType.TECHNICAL_INSPECTION -> stringResource(R.string.maintenance_add_ct)
        MaintenanceType.INSURANCE -> stringResource(R.string.maintenance_add_insurance)
        else -> stringResource(R.string.maintenance_add)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CarManagerBackAppBar(
                title = screenTitle,
                onNavigateBack = onNavigateBack,
                backDescription = stringResource(R.string.cancel),
                actions = {
                    IconButton(onClick = { viewModel.save(notificationPermissionStatus()) }, enabled = !viewModel.isSaving && !viewModel.hasSaved && viewModel.isVehicleLoaded && !viewModel.isScanning) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        FormScreenContent(padding) {
            FormSection("Opération") {
                MaintenanceTypeDropdown(
                    selectedType = viewModel.type,
                    onTypeSelected = viewModel::onTypeChange,
                    getTypeName = { getMaintenanceTypeName(it) },
                    enabled = !viewModel.isTypeLocked
                )
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = viewModel.cost,
                    onValueChange = viewModel::onCostChange,
                    label = { Text(stringResource(R.string.maintenance_cost) + " (${units.currency})") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                DatePickerField(
                    label = "Date de réalisation",
                    selectedDate = viewModel.date,
                    onDateSelected = viewModel::onDateChange
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
                        Text(stringResource(R.string.ocr_scan_maintenance), style = MaterialTheme.typography.labelSmall)
                    }
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
            FormSection("Prochaine échéance (facultative)") {
                DatePickerField(
                    label = stringResource(R.string.maintenance_next_due_date),
                    selectedDate = viewModel.nextDueDate,
                    onDateSelected = viewModel::onNextDueDateChange
                )
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = viewModel.nextDueMileage,
                    onValueChange = viewModel::onNextDueMileageChange,
                    label = { Text(stringResource(R.string.maintenance_next_due_mileage)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { Text(units.distance) }
                )
                MaintenanceSuggestions(
                    type = viewModel.type,
                    onMileageSelect = viewModel::applyMileageIncrement,
                    onDateSelect = viewModel::applyDateIncrement
                )
            }
            FormSection("Détails") {
                OutlinedTextField(
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = viewModel.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.fuel_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            FormSaveAction(stringResource(R.string.save),
                enabled = !viewModel.isSaving && !viewModel.hasSaved && viewModel.isVehicleLoaded && !viewModel.isScanning,
                onClick = { viewModel.save(notificationPermissionStatus()) })
        }

    }
}

@Composable
fun MaintenanceSuggestions(
    type: MaintenanceType,
    onMileageSelect: (Int) -> Unit,
    onDateSelect: (Int, Int) -> Unit
) {
    val units = LocalAppUnits.current
    val kmSuggestions = when (type) {
        MaintenanceType.OIL_CHANGE -> listOf(5000, 10000, 15000, 20000, 25000)
        MaintenanceType.INSPECTION -> listOf(15000, 20000, 30000, 40000, 60000)
        MaintenanceType.TIRES -> listOf(20000, 30000, 40000, 50000, 60000)
        MaintenanceType.BELT -> listOf(60000, 80000, 100000, 120000, 150000)
        else -> emptyList()
    }

    val timeSuggestions = when (type) {
        MaintenanceType.OIL_CHANGE -> listOf(6 to "mois", 1 to "an", 2 to "ans")
        MaintenanceType.INSPECTION -> listOf(1 to "an", 2 to "ans")
        MaintenanceType.TIRES -> listOf(2 to "ans", 3 to "ans", 5 to "ans")
        MaintenanceType.BELT -> listOf(5 to "ans", 6 to "ans", 8 to "ans", 10 to "ans")
        MaintenanceType.BATTERY -> listOf(3 to "ans", 4 to "ans", 5 to "ans")
        MaintenanceType.TECHNICAL_INSPECTION -> listOf(1 to "an", 2 to "ans")
        MaintenanceType.INSURANCE -> listOf(1 to "an")
        else -> emptyList()
    }

    if (kmSuggestions.isNotEmpty() || timeSuggestions.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (kmSuggestions.isNotEmpty()) {
                SuggestionLine(
                    title = "Prochaine étape (${units.distance})",
                    suggestions = kmSuggestions.map { "+${it / 1000}k ${units.distance}" },
                    onSelect = { label ->
                        val valueStr = label.replace("+", "").split(" ")[0].replace("k", "")
                        val value = valueStr.toInt() * 1000
                        onMileageSelect(value)
                    }
                )
            }

            if (timeSuggestions.isNotEmpty()) {
                SuggestionLine(
                    title = "Prochaine étape (Temps)",
                    suggestions = timeSuggestions.map { "+${it.first} ${it.second}" },
                    onSelect = { label ->
                        val parts = label.replace("+", "").split(" ")
                        val value = parts[0].toInt()
                        val unit = parts[1]
                        if (unit.contains("mois")) {
                            onDateSelect(0, value)
                        } else {
                            onDateSelect(value, 0)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionLine(
    title: String,
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
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
            suggestions.forEach { label ->
                SuggestionChip(
                    modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget),
                    shape = CarManagerShapes.control,
                    onClick = { onSelect(label) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceTypeDropdown(
    selectedType: MaintenanceType,
    onTypeSelected: (MaintenanceType) -> Unit,
    getTypeName: @Composable (MaintenanceType) -> String,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            shape = CarManagerShapes.control,
            textStyle = MaterialTheme.typography.bodyMedium,
            value = getTypeName(selectedType),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.maintenance_type)) },
            trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        if (enabled) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MaintenanceType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(getTypeName(type)) },
                        onClick = {
                            onTypeSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
