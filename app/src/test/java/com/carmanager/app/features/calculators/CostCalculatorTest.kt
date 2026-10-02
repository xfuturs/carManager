package com.carmanager.app.features.calculators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CostCalculatorTest {
    @Test fun `fuel trip estimates six liters and twelve cost without rounding`() {
        val result = CostCalculator.trip(100.0, DistanceUnit.KM, 6.0, 2.0)
        assertEquals(6.0, result.quantity, 1e-10)
        assertEquals(12.0, result.cost, 1e-10)
        assertEquals(1.234567 * 2.345678, CostCalculator.fuelFill(1.234567, 2.345678), 1e-12)
    }

    @Test fun `electric trip estimates eighteen kWh and four point five cost`() {
        val result = CostCalculator.trip(100.0, DistanceUnit.KM, 18.0, 0.25)
        assertEquals(18.0, result.quantity, 1e-10)
        assertEquals(4.5, result.cost, 1e-10)
    }

    @Test fun `miles convert before trip and comparison formulas`() {
        assertEquals(160.9344, CostCalculator.distanceKm(100.0, DistanceUnit.MI), 1e-10)
        val trip = CostCalculator.trip(100.0, DistanceUnit.MI, 6.0, 2.0)
        assertEquals(9.656064, trip.quantity, 1e-10)
        assertEquals(19.312128, trip.cost, 1e-10)
        val compare = CostCalculator.compare(100.0, DistanceUnit.MI, 6.0, 2.0, 18.0, 0.25)
        assertEquals(19.312128, compare.fuelCost, 1e-10)
        assertEquals(7.242048, compare.electricCost, 1e-10)
    }

    @Test fun `fuel fill multiplies quantity and unit price`() {
        assertEquals(87.75, CostCalculator.fuelFill(45.0, 1.95), 1e-10)
    }

    @Test fun `recharge sixty kWh from twenty to eighty adds thirty six for nine`() {
        val result = CostCalculator.recharge(60.0, 20.0, 80.0, 0.25)
        assertEquals(36.0, result.energyAdded, 1e-10)
        assertEquals(9.0, result.cost, 1e-10)
        assertEquals(60.0, CostCalculator.recharge(60.0, 0.0, 100.0, 0.0).energyAdded)
    }

    @Test fun `recharge rejects equal reversed and out of range percentages`() {
        for ((start, target) in listOf(20.0 to 20.0, 80.0 to 20.0, -1.0 to 80.0,
            100.0 to 100.0, 0.0 to 0.0, 0.0 to 101.0, Double.NaN to 80.0, 0.0 to Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.recharge(60.0, start, target, 0.25) }
    }

    @Test fun `comparison identifies cheaper option and percentage relative to higher cost`() {
        val electric = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 2.0, 18.0, 0.25)
        assertEquals(CheaperOption.ELECTRIC, electric.cheaper)
        assertEquals(7.5, electric.difference, 1e-10)
        assertEquals(62.5, electric.savingPercent!!, 1e-10)
        val fuel = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 1.0, 18.0, 1.0)
        assertEquals(CheaperOption.FUEL, fuel.cheaper)
        assertEquals(12.0, fuel.difference)
        assertEquals(100.0 * 12.0 / 18.0, fuel.savingPercent!!, 1e-10)
        val equal = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 1.0, 12.0, 0.5)
        assertEquals(CheaperOption.EQUAL, equal.cheaper)
        assertEquals(0.0, equal.difference)
        assertEquals(0.0, equal.savingPercent)
    }

    @Test fun `zero prices accepted in all tools and comparison never divides by zero`() {
        for (consumption in listOf(6.0, 18.0)) assertEquals(0.0, CostCalculator.trip(100.0, DistanceUnit.KM, consumption, 0.0).cost)
        assertEquals(0.0, CostCalculator.fuelFill(40.0, 0.0))
        assertEquals(0.0, CostCalculator.recharge(60.0, 20.0, 80.0, 0.0).cost)
        val bothFree = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 0.0, 18.0, 0.0)
        assertEquals(CheaperOption.EQUAL, bothFree.cheaper)
        assertNull(bothFree.savingPercent)
        val freeElectric = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 2.0, 18.0, 0.0)
        assertEquals(CheaperOption.ELECTRIC, freeElectric.cheaper)
        assertEquals(100.0, freeElectric.savingPercent)
        val freeFuel = CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, 0.0, 18.0, 0.25)
        assertEquals(CheaperOption.FUEL, freeFuel.cheaper)
        assertEquals(100.0, freeFuel.savingPercent)
    }

    @Test fun `positive quantities and finite nonnegative prices required`() {
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.trip(invalid, DistanceUnit.KM, 6.0, 2.0) }
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.trip(100.0, DistanceUnit.KM, invalid, 2.0) }
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.fuelFill(invalid, 2.0) }
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.recharge(invalid, 20.0, 80.0, 0.25) }
        }
        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.trip(100.0, DistanceUnit.KM, 6.0, invalid) }
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.fuelFill(40.0, invalid) }
            assertThrows(IllegalArgumentException::class.java) { CostCalculator.recharge(60.0, 20.0, 80.0, invalid) }
        }
    }

    @Test fun `overflow rejected for unit conversion quantities and totals`() {
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.distanceKm(Double.MAX_VALUE, DistanceUnit.MI) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.trip(Double.MAX_VALUE, DistanceUnit.KM, 1000.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.fuelFill(Double.MAX_VALUE, 2.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.recharge(Double.MAX_VALUE, 0.0, 100.0, 2.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.compare(100.0, DistanceUnit.KM, 6.0, Double.MAX_VALUE, 18.0, 0.25) }
    }
}
