package com.carmanager.app.features.calculators

import com.carmanager.app.core.ui.components.CarManagerTopLevelAppBar
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.theme.LocalAppUnits
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorsScreen(viewModel: CalculatorsViewModel = viewModel()) {
    val units = LocalAppUnits.current
    LaunchedEffect(units.distance, units.currency) { viewModel.preferences(units.distance, units.currency) }
    val distanceLabel = stringResource(R.string.calc_distance, units.distance)
    val fuelPriceLabel = stringResource(R.string.calc_fuel_price, units.currency)
    val electricityPriceLabel = stringResource(R.string.calc_electric_price, units.currency)
    Scaffold(
        topBar = {
            CarManagerTopLevelAppBar(title = stringResource(R.string.nav_calculators))
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.calc_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CalculatorCard(stringResource(R.string.calc_trip_title)) {
                val state = viewModel.trip
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.mode == TripMode.FUEL, onClick = { viewModel.tripMode(TripMode.FUEL) },
                        label = { Text(stringResource(R.string.calc_mode_fuel)) })
                    FilterChip(selected = state.mode == TripMode.ELECTRIC, onClick = { viewModel.tripMode(TripMode.ELECTRIC) },
                        label = { Text(stringResource(R.string.calc_mode_electric)) })
                }
                CalculatorField(state.distance, distanceLabel) { value -> viewModel.editTrip { copy(distance = CalculatorInput(value)) } }
                CalculatorField(state.consumption, stringResource(if (state.mode == TripMode.FUEL) R.string.calc_fuel_consumption else R.string.calc_electric_consumption)) {
                    value -> viewModel.editTrip { copy(consumption = CalculatorInput(value)) }
                }
                CalculatorField(state.price, if (state.mode == TripMode.FUEL) fuelPriceLabel else electricityPriceLabel) {
                    value -> viewModel.editTrip { copy(price = CalculatorInput(value)) }
                }
                CalculateButton(state.overflow, viewModel::calculateTrip)
                state.result?.let { result ->
                    ResultPanel {
                        Text(stringResource(if (state.mode == TripMode.FUEL) R.string.calc_liters_needed else R.string.calc_energy_needed, number(result.quantity)))
                        MoneyResult(R.string.calc_total, result.cost, units.currency)
                    }
                }
            }
            CalculatorCard(stringResource(R.string.calc_fill_title)) {
                val state = viewModel.fill
                CalculatorField(state.liters, stringResource(R.string.calc_liters)) { value -> viewModel.editFill { copy(liters = CalculatorInput(value)) } }
                CalculatorField(state.price, fuelPriceLabel) { value -> viewModel.editFill { copy(price = CalculatorInput(value)) } }
                CalculateButton(state.overflow, viewModel::calculateFill)
                state.result?.let { ResultPanel { MoneyResult(R.string.calc_total, it, units.currency) } }
            }
            CalculatorCard(stringResource(R.string.calc_recharge_title)) {
                val state = viewModel.recharge
                CalculatorField(state.capacity, stringResource(R.string.calc_capacity)) { value -> viewModel.editRecharge { copy(capacity = CalculatorInput(value)) } }
                CalculatorField(state.start, stringResource(R.string.calc_start)) { value -> viewModel.editRecharge { copy(start = CalculatorInput(value)) } }
                CalculatorField(state.target, stringResource(R.string.calc_target)) { value -> viewModel.editRecharge { copy(target = CalculatorInput(value)) } }
                CalculatorField(state.price, electricityPriceLabel) { value -> viewModel.editRecharge { copy(price = CalculatorInput(value)) } }
                CalculateButton(state.overflow, viewModel::calculateRecharge)
                state.result?.let { result ->
                    ResultPanel {
                        Text(stringResource(R.string.calc_energy_added, number(result.energyAdded)))
                        MoneyResult(R.string.calc_total, result.cost, units.currency)
                    }
                }
                Text(stringResource(R.string.calc_recharge_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CalculatorCard(stringResource(R.string.calc_compare_title)) {
                val state = viewModel.comparison
                CalculatorField(state.distance, distanceLabel) { value -> viewModel.editComparison { copy(distance = CalculatorInput(value)) } }
                CalculatorField(state.fuelConsumption, stringResource(R.string.calc_fuel_consumption)) { value -> viewModel.editComparison { copy(fuelConsumption = CalculatorInput(value)) } }
                CalculatorField(state.fuelPrice, fuelPriceLabel) { value -> viewModel.editComparison { copy(fuelPrice = CalculatorInput(value)) } }
                CalculatorField(state.electricConsumption, stringResource(R.string.calc_electric_consumption)) { value -> viewModel.editComparison { copy(electricConsumption = CalculatorInput(value)) } }
                CalculatorField(state.electricPrice, electricityPriceLabel) { value -> viewModel.editComparison { copy(electricPrice = CalculatorInput(value)) } }
                CalculateButton(state.overflow, viewModel::calculateComparison)
                state.result?.let { result ->
                    ResultPanel {
                        MoneyResult(R.string.calc_fuel_total, result.fuelCost, units.currency)
                        MoneyResult(R.string.calc_electric_total, result.electricCost, units.currency)
                        MoneyResult(R.string.calc_difference, result.difference, units.currency)
                        Text(stringResource(when (result.cheaper) {
                            CheaperOption.FUEL -> R.string.calc_fuel_cheaper
                            CheaperOption.ELECTRIC -> R.string.calc_electric_cheaper
                            CheaperOption.EQUAL -> R.string.calc_equal
                        }))
                        result.savingPercent?.let { Text(stringResource(R.string.calc_saving, number(it, 1, 1))) }
                    }
                }
                Text(stringResource(R.string.calc_compare_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalculatorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CalculatorField(input: CalculatorInput, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = input.text, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = input.error != null,
        supportingText = { input.error?.let { error -> Text(stringResource(when (error) {
            InputError.NUMBER -> R.string.calc_error_number
            InputError.POSITIVE -> R.string.calc_error_positive
            InputError.PRICE -> R.string.calc_error_price
            InputError.START_PERCENT -> R.string.calc_error_start
            InputError.TARGET_PERCENT -> R.string.calc_error_target
            InputError.TARGET_ORDER -> R.string.calc_error_order
        })) } },
    )
}

@Composable
private fun CalculateButton(overflow: Boolean, calculate: () -> Unit) {
    Button(onClick = calculate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calc_calculate)) }
    if (overflow) Text(stringResource(R.string.calc_error_overflow), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ResultPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun MoneyResult(label: Int, amount: Double, currency: String) {
    Text(stringResource(label, number(amount, 2, 2), currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

private fun number(value: Double, min: Int = 0, max: Int = 2): String = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
    minimumFractionDigits = min
    maximumFractionDigits = max
}.format(value)
