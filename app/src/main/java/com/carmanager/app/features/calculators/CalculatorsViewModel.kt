package com.carmanager.app.features.calculators

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.carmanager.app.core.domain.validation.FormValidationException
import com.carmanager.app.core.domain.validation.NumericInput

enum class InputError { NUMBER, POSITIVE, PRICE, START_PERCENT, TARGET_PERCENT, TARGET_ORDER }
data class CalculatorInput(val text: String = "", val error: InputError? = null)
data class TripState(
    val mode: TripMode = TripMode.FUEL,
    val distance: CalculatorInput = CalculatorInput(),
    val consumption: CalculatorInput = CalculatorInput(),
    val price: CalculatorInput = CalculatorInput(),
    val result: TripEstimate? = null,
    val overflow: Boolean = false,
)
data class FillState(
    val liters: CalculatorInput = CalculatorInput(),
    val price: CalculatorInput = CalculatorInput(),
    val result: Double? = null,
    val overflow: Boolean = false,
)
data class RechargeState(
    val capacity: CalculatorInput = CalculatorInput(),
    val start: CalculatorInput = CalculatorInput(),
    val target: CalculatorInput = CalculatorInput(),
    val price: CalculatorInput = CalculatorInput(),
    val result: RechargeEstimate? = null,
    val overflow: Boolean = false,
)
data class ComparisonState(
    val distance: CalculatorInput = CalculatorInput(),
    val fuelConsumption: CalculatorInput = CalculatorInput(),
    val fuelPrice: CalculatorInput = CalculatorInput(),
    val electricConsumption: CalculatorInput = CalculatorInput(),
    val electricPrice: CalculatorInput = CalculatorInput(),
    val result: ComparisonEstimate? = null,
    val overflow: Boolean = false,
)

/** État temporaire par entrée de navigation. Aucun dépôt ni service injecté. */
class CalculatorsViewModel : ViewModel() {
    var trip by mutableStateOf(TripState())
        private set
    var fill by mutableStateOf(FillState())
        private set
    var recharge by mutableStateOf(RechargeState())
        private set
    var comparison by mutableStateOf(ComparisonState())
        private set
    var distanceUnit by mutableStateOf(DistanceUnit.KM)
        private set
    private var currency = "€"

    fun preferences(distance: String, symbol: String) {
        val unit = if (distance == "mi") DistanceUnit.MI else DistanceUnit.KM
        if (unit != distanceUnit) {
            trip = trip.copy(result = null, overflow = false)
            comparison = comparison.copy(result = null, overflow = false)
        }
        if (symbol != currency) {
            trip = trip.copy(result = null, overflow = false)
            fill = fill.copy(result = null, overflow = false)
            recharge = recharge.copy(result = null, overflow = false)
            comparison = comparison.copy(result = null, overflow = false)
        }
        distanceUnit = unit
        currency = symbol
    }

    fun editTrip(change: TripState.() -> TripState) { trip = trip.change().copy(result = null, overflow = false) }
    fun editFill(change: FillState.() -> FillState) { fill = fill.change().copy(result = null, overflow = false) }
    fun editRecharge(change: RechargeState.() -> RechargeState) { recharge = recharge.change().copy(result = null, overflow = false) }
    fun editComparison(change: ComparisonState.() -> ComparisonState) { comparison = comparison.change().copy(result = null, overflow = false) }
    fun tripMode(mode: TripMode) {
        if (mode != trip.mode) trip = trip.copy(mode = mode, consumption = CalculatorInput(), price = CalculatorInput(), result = null, overflow = false)
    }

    private data class Checked(val input: CalculatorInput, val value: Double?)
    private fun check(input: CalculatorInput, rule: InputError = InputError.POSITIVE): Checked {
        val value = try { NumericInput.decimal(input.text, "") } catch (_: FormValidationException) {
            return Checked(input.copy(error = InputError.NUMBER), null)
        }
        val valid = when (rule) {
            InputError.PRICE -> value >= 0
            InputError.START_PERCENT -> value >= 0 && value < 100
            InputError.TARGET_PERCENT -> value > 0 && value <= 100
            else -> value > 0
        }
        return Checked(input.copy(error = if (valid) null else rule), if (valid) value else null)
    }

    fun calculateTrip() {
        val d = check(trip.distance); val c = check(trip.consumption); val p = check(trip.price, InputError.PRICE)
        trip = trip.copy(distance = d.input, consumption = c.input, price = p.input, result = null, overflow = false)
        if (d.value == null || c.value == null || p.value == null) return
        try { trip = trip.copy(result = CostCalculator.trip(d.value, distanceUnit, c.value, p.value)) }
        catch (_: IllegalArgumentException) { trip = trip.copy(overflow = true) }
    }

    fun calculateFill() {
        val l = check(fill.liters); val p = check(fill.price, InputError.PRICE)
        fill = fill.copy(liters = l.input, price = p.input, result = null, overflow = false)
        if (l.value == null || p.value == null) return
        try { fill = fill.copy(result = CostCalculator.fuelFill(l.value, p.value)) }
        catch (_: IllegalArgumentException) { fill = fill.copy(overflow = true) }
    }

    fun calculateRecharge() {
        val c = check(recharge.capacity); val s = check(recharge.start, InputError.START_PERCENT)
        var t = check(recharge.target, InputError.TARGET_PERCENT); val p = check(recharge.price, InputError.PRICE)
        if (s.value != null && t.value != null && t.value <= s.value)
            t = Checked(t.input.copy(error = InputError.TARGET_ORDER), null)
        recharge = recharge.copy(capacity = c.input, start = s.input, target = t.input, price = p.input, result = null, overflow = false)
        if (c.value == null || s.value == null || t.value == null || p.value == null) return
        try { recharge = recharge.copy(result = CostCalculator.recharge(c.value, s.value, t.value, p.value)) }
        catch (_: IllegalArgumentException) { recharge = recharge.copy(overflow = true) }
    }

    fun calculateComparison() {
        val d = check(comparison.distance); val f = check(comparison.fuelConsumption)
        val fp = check(comparison.fuelPrice, InputError.PRICE); val e = check(comparison.electricConsumption)
        val ep = check(comparison.electricPrice, InputError.PRICE)
        comparison = comparison.copy(distance = d.input, fuelConsumption = f.input, fuelPrice = fp.input,
            electricConsumption = e.input, electricPrice = ep.input, result = null, overflow = false)
        if (d.value == null || f.value == null || fp.value == null || e.value == null || ep.value == null) return
        try { comparison = comparison.copy(result = CostCalculator.compare(d.value, distanceUnit, f.value, fp.value, e.value, ep.value)) }
        catch (_: IllegalArgumentException) { comparison = comparison.copy(overflow = true) }
    }
}
