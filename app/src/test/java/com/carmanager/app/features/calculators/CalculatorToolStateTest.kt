package com.carmanager.app.features.calculators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalculatorToolStateTest {
    @Test fun `default trip and switching tools keep every draft result and trip mode`() {
        val vm = CalculatorsViewModel()
        assertEquals(CalculatorTool.TRIP, vm.selectedTool)
        vm.tripMode(TripMode.ELECTRIC)
        vm.editTrip { copy(distance = CalculatorInput("100"), consumption = CalculatorInput("18"), price = CalculatorInput("0,25")) }
        vm.calculateTrip()
        vm.editFill { copy(liters = CalculatorInput("40"), price = CalculatorInput("2")) }; vm.calculateFill()
        vm.editRecharge { copy(capacity = CalculatorInput("60"), start = CalculatorInput("20"), target = CalculatorInput("80"), price = CalculatorInput("0,25")) }; vm.calculateRecharge()
        vm.editComparison { copy(distance = CalculatorInput("100"), fuelConsumption = CalculatorInput("6"), fuelPrice = CalculatorInput("2"),
            electricConsumption = CalculatorInput("18"), electricPrice = CalculatorInput("0,25")) }; vm.calculateComparison()
        val drafts = listOf(vm.trip, vm.fill, vm.recharge, vm.comparison)
        CalculatorTool.entries.forEach { tool ->
            vm.selectTool(tool)
            assertEquals(tool, vm.selectedTool)
            assertEquals(drafts, listOf(vm.trip, vm.fill, vm.recharge, vm.comparison))
        }
        vm.selectTool(CalculatorTool.TRIP)
        assertEquals(TripMode.ELECTRIC, vm.trip.mode)
        assertEquals(4.5, vm.trip.result!!.cost)
    }

    @Test fun `switching and reselecting do not calculate or erase field errors and overflow`() {
        val vm = CalculatorsViewModel()
        vm.calculateTrip()
        vm.editFill { copy(liters = CalculatorInput("9".repeat(308)), price = CalculatorInput("20")) }; vm.calculateFill()
        assertTrue(vm.fill.overflow)
        val trip = vm.trip; val fill = vm.fill
        repeat(2) {
            vm.selectTool(CalculatorTool.FILL); vm.selectTool(CalculatorTool.TRIP)
            vm.selectTool(CalculatorTool.TRIP)
            assertEquals(trip, vm.trip); assertEquals(fill, vm.fill)
            assertEquals(InputError.NUMBER, vm.trip.distance.error)
            assertNull(vm.trip.result)
            assertTrue(vm.fill.overflow)
        }
    }

    @Test fun `unit and currency changes still invalidate results without resetting active tool or drafts`() {
        val vm = CalculatorsViewModel()
        vm.selectTool(CalculatorTool.COMPARISON)
        vm.editComparison { copy(distance = CalculatorInput("100"), fuelConsumption = CalculatorInput("6"), fuelPrice = CalculatorInput("2"),
            electricConsumption = CalculatorInput("18"), electricPrice = CalculatorInput("0,25")) }; vm.calculateComparison()
        vm.preferences("mi", "CHF")
        assertEquals(CalculatorTool.COMPARISON, vm.selectedTool)
        assertNull(vm.comparison.result)
        assertEquals("100", vm.comparison.distance.text)
        vm.calculateComparison()
        assertEquals(19.312128, vm.comparison.result!!.fuelCost, 1e-10)
        vm.selectTool(CalculatorTool.RECHARGE)
        assertNotNull(vm.comparison.result)
        vm.preferences("mi", "CHF")
        assertEquals(CalculatorTool.RECHARGE, vm.selectedTool)
        assertNotNull(vm.comparison.result)
    }
}
