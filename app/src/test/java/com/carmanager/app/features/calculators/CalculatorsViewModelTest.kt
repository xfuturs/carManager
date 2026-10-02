package com.carmanager.app.features.calculators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalculatorsViewModelTest {
    private fun fuel(vm: CalculatorsViewModel) {
        vm.editTrip { copy(distance = CalculatorInput("100"), consumption = CalculatorInput("6"), price = CalculatorInput("2")) }
        vm.calculateTrip()
    }
    private fun fill(vm: CalculatorsViewModel) {
        vm.editFill { copy(liters = CalculatorInput("40"), price = CalculatorInput("2")) }
        vm.calculateFill()
    }
    private fun recharge(vm: CalculatorsViewModel) {
        vm.editRecharge { copy(capacity = CalculatorInput("60"), start = CalculatorInput("20"), target = CalculatorInput("80"), price = CalculatorInput("0,25")) }
        vm.calculateRecharge()
    }
    private fun compare(vm: CalculatorsViewModel) {
        vm.editComparison { copy(distance = CalculatorInput("100"), fuelConsumption = CalculatorInput("6"), fuelPrice = CalculatorInput("2"),
            electricConsumption = CalculatorInput("18"), electricPrice = CalculatorInput("0,25")) }
        vm.calculateComparison()
    }

    @Test fun `untouched fields have no errors then explicit calculate validates each required input`() {
        val vm = CalculatorsViewModel()
        assertNull(vm.trip.distance.error); assertNull(vm.fill.liters.error)
        assertNull(vm.recharge.start.error); assertNull(vm.comparison.electricPrice.error)
        vm.calculateTrip(); vm.calculateFill(); vm.calculateRecharge(); vm.calculateComparison()
        assertEquals(InputError.NUMBER, vm.trip.distance.error)
        assertEquals(InputError.NUMBER, vm.trip.consumption.error)
        assertEquals(InputError.NUMBER, vm.trip.price.error)
        assertEquals(InputError.NUMBER, vm.fill.liters.error)
        assertEquals(InputError.NUMBER, vm.recharge.start.error)
        assertEquals(InputError.NUMBER, vm.comparison.electricPrice.error)
        assertNull(vm.trip.result); assertNull(vm.fill.result); assertNull(vm.recharge.result); assertNull(vm.comparison.result)
    }

    @Test fun `comma dot and surrounding spaces produce identical trip results`() {
        val vm = CalculatorsViewModel()
        for (price in listOf("2,00", "2.00", " 2,00 ")) {
            vm.editTrip { copy(distance = CalculatorInput(" 100 "), consumption = CalculatorInput("6,0"), price = CalculatorInput(price)) }
            vm.calculateTrip()
            assertEquals(12.0, vm.trip.result!!.cost)
        }
    }

    @Test fun `malformed negative zero quantity and nonfinite inputs rejected with field errors`() {
        val vm = CalculatorsViewModel()
        for (invalid in listOf("", "1e2", "NaN", "Infinity", "12,5.2", "12..5", "9".repeat(400))) {
            vm.editFill { copy(liters = CalculatorInput(invalid), price = CalculatorInput("2")) }
            vm.calculateFill()
            assertEquals(InputError.NUMBER, vm.fill.liters.error)
            assertNull(vm.fill.result)
        }
        for (invalid in listOf("0", "-1")) {
            vm.editTrip { copy(distance = CalculatorInput(invalid), consumption = CalculatorInput("6"), price = CalculatorInput("2")) }
            vm.calculateTrip()
            assertEquals(InputError.POSITIVE, vm.trip.distance.error)
            assertNull(vm.trip.result)
        }
        vm.editFill { copy(liters = CalculatorInput("40"), price = CalculatorInput("-2")) }
        vm.calculateFill()
        assertEquals(InputError.PRICE, vm.fill.price.error)
        vm.editFill { copy(price = CalculatorInput("0")) }; vm.calculateFill()
        assertEquals(0.0, vm.fill.result)
    }

    @Test fun `editing any calculator clears only its own result`() {
        val vm = CalculatorsViewModel()
        fuel(vm); fill(vm); recharge(vm); compare(vm)
        vm.editTrip { copy(price = CalculatorInput("3")) }
        assertNull(vm.trip.result); assertNotNull(vm.fill.result); assertNotNull(vm.recharge.result); assertNotNull(vm.comparison.result)
        fuel(vm)
        vm.editFill { copy(liters = CalculatorInput("45")) }
        assertNull(vm.fill.result); assertNotNull(vm.trip.result); assertNotNull(vm.recharge.result); assertNotNull(vm.comparison.result)
        fill(vm)
        vm.editRecharge { copy(target = CalculatorInput("90")) }
        assertNull(vm.recharge.result); assertNotNull(vm.trip.result); assertNotNull(vm.fill.result); assertNotNull(vm.comparison.result)
        recharge(vm)
        vm.editComparison { copy(distance = CalculatorInput("200")) }
        assertNull(vm.comparison.result); assertNotNull(vm.trip.result); assertNotNull(vm.fill.result); assertNotNull(vm.recharge.result)
    }

    @Test fun `switching trip mode keeps distance but clears incompatible consumption price and result`() {
        val vm = CalculatorsViewModel(); fuel(vm)
        vm.tripMode(TripMode.ELECTRIC)
        assertEquals("100", vm.trip.distance.text)
        assertEquals("", vm.trip.consumption.text); assertEquals("", vm.trip.price.text); assertNull(vm.trip.result)
        vm.editTrip { copy(consumption = CalculatorInput("18"), price = CalculatorInput("0,25")) }
        vm.calculateTrip()
        assertEquals(18.0, vm.trip.result!!.quantity); assertEquals(4.5, vm.trip.result!!.cost)
        vm.tripMode(TripMode.FUEL)
        assertNull(vm.trip.result); assertEquals("", vm.trip.consumption.text)
    }

    @Test fun `preference changes clear affected results and miles are actually converted`() {
        val vm = CalculatorsViewModel(); fuel(vm); fill(vm); recharge(vm); compare(vm)
        vm.preferences("mi", "€")
        assertNull(vm.trip.result); assertNull(vm.comparison.result); assertNotNull(vm.fill.result); assertNotNull(vm.recharge.result)
        vm.calculateTrip(); vm.calculateComparison()
        assertEquals(19.312128, vm.trip.result!!.cost, 1e-10)
        assertEquals(7.242048, vm.comparison.result!!.electricCost, 1e-10)
        vm.preferences("mi", "CHF")
        assertNull(vm.trip.result); assertNull(vm.fill.result); assertNull(vm.recharge.result); assertNull(vm.comparison.result)
        assertEquals("2", vm.trip.price.text)
        vm.calculateTrip()
        assertEquals(19.312128, vm.trip.result!!.cost, 1e-10)
        vm.preferences("mi", "CHF")
        assertNotNull(vm.trip.result)
    }

    @Test fun `recharge percentage validation has actionable bounds and order errors`() {
        val vm = CalculatorsViewModel(); recharge(vm)
        assertEquals(36.0, vm.recharge.result!!.energyAdded)
        assertEquals(9.0, vm.recharge.result!!.cost)
        for (target in listOf("20", "10")) {
            vm.editRecharge { copy(target = CalculatorInput(target)) }; vm.calculateRecharge()
            assertEquals(InputError.TARGET_ORDER, vm.recharge.target.error); assertNull(vm.recharge.result)
        }
        vm.editRecharge { copy(start = CalculatorInput("100"), target = CalculatorInput("101")) }; vm.calculateRecharge()
        assertEquals(InputError.START_PERCENT, vm.recharge.start.error)
        assertEquals(InputError.TARGET_PERCENT, vm.recharge.target.error)
    }

    @Test fun `finite inputs whose multiplication overflows show no result and permit correction`() {
        val vm = CalculatorsViewModel()
        vm.editFill { copy(liters = CalculatorInput("9".repeat(308)), price = CalculatorInput("20")) }
        vm.calculateFill()
        assertTrue(vm.fill.overflow); assertNull(vm.fill.result)
        vm.editFill { copy(liters = CalculatorInput("40")) }
        assertFalse(vm.fill.overflow)
        vm.calculateFill(); assertEquals(800.0, vm.fill.result)
    }
}
