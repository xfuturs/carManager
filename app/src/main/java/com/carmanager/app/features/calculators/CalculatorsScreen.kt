package com.carmanager.app.features.calculators

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.components.BannerAdSlot
import com.carmanager.app.core.ui.components.CarManagerTopLevelAppBar
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography
import com.carmanager.app.core.ui.theme.LocalAppUnits
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CalculatorsScreen(canShowAds: Boolean, viewModel: CalculatorsViewModel = viewModel()) {
    val units = LocalAppUnits.current
    LaunchedEffect(units.distance, units.currency) { viewModel.preferences(units.distance, units.currency) }
    val distanceLabel = stringResource(R.string.calc_distance, units.distance)
    val fuelPriceLabel = stringResource(R.string.calc_fuel_price, units.currency)
    val electricityPriceLabel = stringResource(R.string.calc_electric_price, units.currency)
    Scaffold(
        topBar = { CarManagerTopLevelAppBar(title = stringResource(R.string.nav_calculators)) },
        bottomBar = { if (canShowAds) BannerAdSlot() },
    ) { padding ->
        // Le nouveau formulaire repart en haut ; ses données restent dans le ViewModel.
        key(viewModel.selectedTool) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = CarManagerSpacing.screenHorizontal, top = CarManagerSpacing.firstContentTop,
                        end = CarManagerSpacing.screenHorizontal, bottom = CarManagerSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.medium),
            ) {
                Text(stringResource(R.string.calc_subtitle), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CalculatorToolSelector(viewModel.selectedTool, viewModel::selectTool)
                when (viewModel.selectedTool) {
                    CalculatorTool.TRIP -> TripCalculator(viewModel, distanceLabel, fuelPriceLabel, electricityPriceLabel, units.currency)
                    CalculatorTool.FILL -> FillCalculator(viewModel, fuelPriceLabel, units.currency)
                    CalculatorTool.RECHARGE -> RechargeCalculator(viewModel, electricityPriceLabel, units.currency)
                    CalculatorTool.COMPARISON -> ComparisonCalculator(viewModel, distanceLabel, fuelPriceLabel, electricityPriceLabel, units.currency)
                }
            }
        }
    }
}

@Composable
private fun CalculatorToolSelector(selected: CalculatorTool, onSelect: (CalculatorTool) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= 280.dp * fontScale && fontScale <= 1.3f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
            CalculatorTool.entries.chunked(columns).forEach { tools ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                    tools.forEach { tool ->
                        val isSelected = selected == tool
                        val color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .heightIn(min = CarManagerDimensions.touchTarget)
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tool) }),
                            shape = CarManagerShapes.control,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = color,
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(CarManagerSpacing.small),
                                horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (tool) {
                                    CalculatorTool.TRIP -> Icons.Default.Route
                                    CalculatorTool.FILL -> Icons.Default.LocalGasStation
                                    CalculatorTool.RECHARGE -> Icons.Default.EvStation
                                    CalculatorTool.COMPARISON -> Icons.AutoMirrored.Filled.CompareArrows
                                }, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(stringResource(when (tool) {
                                    CalculatorTool.TRIP -> R.string.calc_tool_trip
                                    CalculatorTool.FILL -> R.string.calc_tool_fill
                                    CalculatorTool.RECHARGE -> R.string.calc_tool_recharge
                                    CalculatorTool.COMPARISON -> R.string.calc_tool_compare
                                }), modifier = Modifier.weight(1f), style = CarManagerTypography.supporting,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripModeSelector(mode: TripMode, onSelect: (TripMode) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
        TripMode.entries.forEach { option ->
            FilterChip(selected = mode == option, onClick = { onSelect(option) },
                modifier = Modifier.heightIn(min = CarManagerDimensions.touchTarget), shape = CarManagerShapes.control,
                label = { Text(stringResource(if (option == TripMode.FUEL) R.string.calc_mode_fuel else R.string.calc_mode_electric)) },
                leadingIcon = if (mode == option) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null)
        }
    }
}

@Composable
private fun TripCalculator(vm: CalculatorsViewModel, distanceLabel: String, fuelPrice: String, electricPrice: String, currency: String) {
    val state = vm.trip
    CalculatorSection(stringResource(R.string.calc_trip_title)) {
        TripModeSelector(state.mode, vm::tripMode)
        CalculatorField(state.distance, distanceLabel) { value -> vm.editTrip { copy(distance = CalculatorInput(value)) } }
        CalculatorField(state.consumption, stringResource(if (state.mode == TripMode.FUEL) R.string.calc_fuel_consumption else R.string.calc_electric_consumption)) {
            value -> vm.editTrip { copy(consumption = CalculatorInput(value)) }
        }
        CalculatorField(state.price, if (state.mode == TripMode.FUEL) fuelPrice else electricPrice) {
            value -> vm.editTrip { copy(price = CalculatorInput(value)) }
        }
        CalculateButton(state.overflow, vm::calculateTrip)
        state.result?.let { result ->
            ResultPanel {
                MoneyResult(R.string.calc_total, result.cost, currency)
                Text(stringResource(if (state.mode == TripMode.FUEL) R.string.calc_liters_needed else R.string.calc_energy_needed, number(result.quantity)),
                    style = CarManagerTypography.supporting)
            }
        }
    }
}

@Composable
private fun FillCalculator(vm: CalculatorsViewModel, priceLabel: String, currency: String) {
    val state = vm.fill
    CalculatorSection(stringResource(R.string.calc_fill_title)) {
        CalculatorField(state.liters, stringResource(R.string.calc_liters)) { value -> vm.editFill { copy(liters = CalculatorInput(value)) } }
        CalculatorField(state.price, priceLabel) { value -> vm.editFill { copy(price = CalculatorInput(value)) } }
        CalculateButton(state.overflow, vm::calculateFill)
        state.result?.let { ResultPanel { MoneyResult(R.string.calc_total, it, currency) } }
    }
}

@Composable
private fun RechargeCalculator(vm: CalculatorsViewModel, priceLabel: String, currency: String) {
    val state = vm.recharge
    CalculatorSection(stringResource(R.string.calc_recharge_title)) {
        CalculatorField(state.capacity, stringResource(R.string.calc_capacity)) { value -> vm.editRecharge { copy(capacity = CalculatorInput(value)) } }
        CalculatorField(state.start, stringResource(R.string.calc_start)) { value -> vm.editRecharge { copy(start = CalculatorInput(value)) } }
        CalculatorField(state.target, stringResource(R.string.calc_target)) { value -> vm.editRecharge { copy(target = CalculatorInput(value)) } }
        CalculatorField(state.price, priceLabel) { value -> vm.editRecharge { copy(price = CalculatorInput(value)) } }
        CalculateButton(state.overflow, vm::calculateRecharge)
        state.result?.let { result ->
            ResultPanel {
                MoneyResult(R.string.calc_total, result.cost, currency)
                Text(stringResource(R.string.calc_energy_added, number(result.energyAdded)), style = CarManagerTypography.supporting)
            }
        }
        CalculatorNote(R.string.calc_recharge_note)
    }
}

@Composable
private fun ComparisonCalculator(vm: CalculatorsViewModel, distanceLabel: String, fuelPrice: String, electricPrice: String, currency: String) {
    val state = vm.comparison
    CalculatorSection(stringResource(R.string.calc_compare_title)) {
        CalculatorField(state.distance, distanceLabel) { value -> vm.editComparison { copy(distance = CalculatorInput(value)) } }
        CalculatorGroup(stringResource(R.string.calc_fuel_total)) {
            CalculatorField(state.fuelConsumption, stringResource(R.string.calc_fuel_consumption)) { value -> vm.editComparison { copy(fuelConsumption = CalculatorInput(value)) } }
            CalculatorField(state.fuelPrice, fuelPrice) { value -> vm.editComparison { copy(fuelPrice = CalculatorInput(value)) } }
        }
        CalculatorGroup(stringResource(R.string.calc_electric_total)) {
            CalculatorField(state.electricConsumption, stringResource(R.string.calc_electric_consumption)) { value -> vm.editComparison { copy(electricConsumption = CalculatorInput(value)) } }
            CalculatorField(state.electricPrice, electricPrice) { value -> vm.editComparison { copy(electricPrice = CalculatorInput(value)) } }
        }
        CalculateButton(state.overflow, vm::calculateComparison)
        state.result?.let { result ->
            ResultPanel {
                MoneyResult(R.string.calc_fuel_total, result.fuelCost, currency)
                MoneyResult(R.string.calc_electric_total, result.electricCost, currency)
                MoneyResult(R.string.calc_difference, result.difference, currency)
                Text(stringResource(when (result.cheaper) {
                    CheaperOption.FUEL -> R.string.calc_fuel_cheaper
                    CheaperOption.ELECTRIC -> R.string.calc_electric_cheaper
                    CheaperOption.EQUAL -> R.string.calc_equal
                }), style = CarManagerTypography.supporting)
                result.savingPercent?.let { Text(stringResource(R.string.calc_saving, number(it, 1, 1)), style = MaterialTheme.typography.labelSmall) }
            }
        }
        CalculatorNote(R.string.calc_compare_note)
    }
}

@Composable
private fun CalculatorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = CarManagerShapes.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(CarManagerSpacing.medium), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
            Text(title, style = CarManagerTypography.sectionHeading, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun CalculatorGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
        Text(title, style = CarManagerTypography.supporting, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun CalculatorField(input: CalculatorInput, label: String, onChange: (String) -> Unit) {
    val error = input.error
    val support: (@Composable () -> Unit)? = if (error == null) null else {
        { Text(stringResource(when (error) {
            InputError.NUMBER -> R.string.calc_error_number
            InputError.POSITIVE -> R.string.calc_error_positive
            InputError.PRICE -> R.string.calc_error_price
            InputError.START_PERCENT -> R.string.calc_error_start
            InputError.TARGET_PERCENT -> R.string.calc_error_target
            InputError.TARGET_ORDER -> R.string.calc_error_order
        })) }
    }
    OutlinedTextField(value = input.text, onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = CarManagerShapes.control,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null, supportingText = support)
}

@Composable
private fun CalculateButton(overflow: Boolean, calculate: () -> Unit) {
    Button(onClick = calculate, modifier = Modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget),
        shape = CarManagerShapes.control,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text(stringResource(R.string.calc_calculate), style = CarManagerTypography.buttonLabel)
    }
    if (overflow) Text(stringResource(R.string.calc_error_overflow), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ResultPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = CarManagerShapes.control) {
        Column(Modifier.padding(CarManagerSpacing.medium), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small), content = content)
    }
}

@Composable
private fun MoneyResult(label: Int, amount: Double, currency: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall)) {
        Text(stringResource(R.string.calc_money_value, number(amount, 2, 2), currency),
            style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CalculatorNote(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun number(value: Double, min: Int = 0, max: Int = 2): String = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
    minimumFractionDigits = min
    maximumFractionDigits = max
}.format(value)
