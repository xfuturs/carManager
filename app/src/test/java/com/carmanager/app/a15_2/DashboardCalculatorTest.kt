package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.features.dashboard.DashboardCalculator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class DashboardCalculatorTest {
    private fun calculate(v: List<Vehicle> = listOf(vehicle()), f: List<FuelRecord> = emptyList(),
                          m: List<MaintenanceRecord> = emptyList(), d: List<Document> = emptyList(),
                          time: TemporalContext = october) = DashboardCalculator.calculate(v, f, m, d, time)
    @Test fun `empty garage is genuinely empty`() {
        val result = calculate(v = emptyList()); assertEquals(0, result.vehicleCount)
        assertTrue(result.vehicles.isEmpty()); assertEquals(0.0, result.monthlyFuelCost)
        assertEquals(0.0, result.monthlyMaintenanceCost); assertNull(result.nextMaintenance)
    }
    @Test fun `one vehicle without history has neutral consumption and missing documents`() {
        val result = calculate().vehicles.single(); assertNull(result.averageConsumption)
        assertEquals(0, result.relevantConsumptionSampleCount); assertTrue(result.isCTDocMissing)
        assertTrue(result.isInsuranceDocMissing); assertEquals(0.0, result.totalExpenses)
    }
    @Test fun `normal fuel summary preserves weighted average distance quantities and order`() {
        val records = listOf(fill(3, 2000, amount = 60.0), fill(1, 0), fill(2, 1000, amount = 40.0))
        val result = calculate(f = records).vehicles.single()
        assertEquals(5.0, result.averageConsumption); assertEquals(listOf(4.0, 6.0), result.consumptionHistory)
        assertEquals(2000, result.distanceTracked); assertEquals(150.0, result.totalLiters)
        assertEquals(210.0, result.totalFuelCost); assertEquals(3, result.fuelRecordsCount)
    }
    @Test fun `total expenses sums fuel and maintenance across all years`() {
        val old = Instant.parse("2025-10-01T12:00:00Z").toEpochMilli()
        val result = calculate(f = listOf(fill(date = old, cost = 100.0), fill(2, date = october.monthStart, cost = 200.0)),
            m = listOf(intervention(date = old, cost = 300.0), intervention(2, cost = 400.0))).vehicles.single()
        assertEquals(1000.0, result.totalExpenses); assertEquals(700.0, result.totalMaintenanceCost)
        assertEquals(400.0, result.yearlyMaintenanceCost); assertEquals(200.0, result.monthlyFuelCost)
    }
    @Test fun `month and year use half open local boundaries`() {
        val result = calculate(f = listOf(fill(date = october.monthStart - 1, cost = 1.0),
            fill(2, date = october.monthStart, cost = 2.0), fill(3, date = october.nextMonthStart - 1, cost = 4.0),
            fill(4, date = october.nextMonthStart, cost = 8.0)), m = listOf(
            intervention(date = october.yearStart - 1, cost = 1.0), intervention(2, date = october.yearStart, cost = 2.0),
            intervention(3, date = october.nextYearStart - 1, cost = 4.0), intervention(4, date = october.nextYearStart, cost = 8.0)))
        assertEquals(6.0, result.monthlyFuelCost); assertEquals(0.0, result.monthlyMaintenanceCost)
        assertEquals(6.0, result.vehicles.single().yearlyMaintenanceCost)
        assertEquals(30.0, result.vehicles.single().totalExpenses)
    }
    @Test fun `zero costs are valid domain values and sums remain zero`() {
        val result = calculate(f = listOf(fill(cost = 0.0)), m = listOf(intervention(cost = 0.0)))
        assertEquals(0.0, result.vehicles.single().totalExpenses)
        // Les modèles cost/totalPrice sont Double non nullables : aucune absence inventée.
    }
    @Test fun `maintenance only has matched month year and total periods`() {
        val result = calculate(m = listOf(intervention(cost = 42.5)))
        assertEquals(42.5, result.monthlyMaintenanceCost)
        assertEquals(42.5, result.vehicles.single().totalExpenses)
        assertEquals(42.5, result.vehicles.single().yearlyMaintenanceCost)
    }
    @Test fun `documents keep category and legacy administrative title rules`() {
        for (docs in listOf(listOf(document(category = DocumentCategory.TECHNICAL_INSPECTION), document(2, DocumentCategory.INSURANCE)),
            listOf(document(category = DocumentCategory.ADMINISTRATIVE, title = "Ancien CT"), document(2, DocumentCategory.ADMINISTRATIVE, "Assurance")))) {
            val result = calculate(d = docs).vehicles.single()
            assertFalse(result.isCTDocMissing); assertFalse(result.isInsuranceDocMissing)
        }
    }
    @Test fun `alerts are distinct and latest inspection insurance date is retained`() {
        val expired = october.instant.toEpochMilli() - 1000
        val result = calculate(m = listOf(intervention(due = expired, dueMileage = 4000, type = MaintenanceType.TECHNICAL_INSPECTION),
            intervention(2, due = expired - 1000, dueMileage = 3000, type = MaintenanceType.TECHNICAL_INSPECTION),
            intervention(3, due = expired + 10000, type = MaintenanceType.INSURANCE))).vehicles.single()
        assertEquals(2, result.alerts.size); assertEquals(expired, result.nextCTDate)
        assertEquals(expired + 10000, result.nextInsuranceDate)
    }
    @Test fun `deadlines keep OR eligibility and date then mileage stable ordering`() {
        val now = october.instant.toEpochMilli()
        val records = listOf(intervention(1, due = now + 1000, dueMileage = 9000),
            intervention(2, due = now + 1000, dueMileage = 8000), intervention(3, dueMileage = 7000),
            intervention(4, due = now - 3 * 86400000L, dueMileage = 100), intervention(5),
            intervention(6, due = now - 3 * 86400000L))
        val result = calculate(m = records)
        assertEquals(listOf(6L, 2L, 1L, 3L), result.upcomingDeadlines.map { it.second.id })
        assertEquals(1L, result.nextMaintenance!!.id)
    }
    @Test fun `owner and vehicle grouping never mix rows even with equal vehicle IDs`() {
        val result = calculate(v = listOf(vehicle(owner = "firebase:A"), vehicle(owner = "firebase:B")),
            f = listOf(fill(cost = 10.0, owner = "firebase:A"), fill(2, cost = 20.0, owner = "firebase:B"), fill(3, cost = 1000.0)),
            m = listOf(intervention(cost = 3.0, owner = "firebase:A")), d = listOf(document(owner = "firebase:B", category = DocumentCategory.INSURANCE)))
        assertEquals(listOf(13.0, 20.0), result.vehicles.map { it.totalExpenses })
        assertEquals(30.0, result.monthlyFuelCost); assertTrue(result.vehicles.first().isInsuranceDocMissing)
        assertFalse(result.vehicles.last().isInsuranceDocMissing)
    }
    private class Counted<T>(private val values: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size get() = values.size
        override fun get(index: Int): T { reads++; return values[index] }
    }
    @Test fun `50 vehicles 5000 mixed rows match independent repeated filter reference with one input traversal`() {
        val vehicles = (1L..50L).map { vehicle(it) }
        val fuel = vehicles.flatMap { v -> (0..59).map { fill(it + 1L, it * 100, amount = 5.0, cost = 2.0, vehicleId = v.id) } }
        val maintenance = vehicles.flatMap { v -> (0..29).map { intervention(it + 1L,
            date = october.monthStart - it * 86400000L, cost = 3.0, due = october.instant.toEpochMilli() + 1000 + it * 1000L, vehicleId = v.id) } }
        val docs = vehicles.flatMap { v -> (0..9).map { document(it + 1L, if (it == 0) DocumentCategory.INSURANCE else DocumentCategory.TECHNICAL_INSPECTION, vehicleId = v.id) } }
        assertEquals(5000, fuel.size + maintenance.size + docs.size)
        val countedFuel = Counted(fuel); val countedMaintenance = Counted(maintenance); val countedDocs = Counted(docs)
        val actual = calculate(vehicles, countedFuel, countedMaintenance, countedDocs)
        assertEquals(listOf(3000,1500,500), listOf(countedFuel.reads,countedMaintenance.reads,countedDocs.reads))
        val reference = vehicles.map { v ->
            val f = fuel.filter { it.vehicleId == v.id }.sortedBy { it.date }
            val m = maintenance.filter { it.vehicleId == v.id }
            val d = docs.filter { it.vehicleId == v.id }
            val average = (f.sumOf { it.liters } - f.first().liters) / (f.last().mileage - f.first().mileage) * 100
            VehicleStats(v, average, f.sumOf { it.totalPrice }, f.sumOf { it.liters }, f.size,
                f.last().mileage - f.first().mileage,
                f.filter { it.date >= october.monthStart && it.date < october.nextMonthStart }.sumOf { it.totalPrice },
                m.filter { it.date >= october.yearStart && it.date < october.nextYearStart }.sumOf { it.cost },
                consumptionHistory = f.zipWithNext { a,b -> b.liters / (b.mileage-a.mileage) * 100 }.takeLast(10),
                isCTDocMissing = d.none { it.category == DocumentCategory.TECHNICAL_INSPECTION },
                isInsuranceDocMissing = d.none { it.category == DocumentCategory.INSURANCE },
                totalMaintenanceCost = m.sumOf { it.cost }, relevantConsumptionSampleCount = f.size)
        }
        assertEquals(reference, actual.vehicles)
        assertEquals(reference.sumOf { it.monthlyFuelCost }, actual.monthlyFuelCost)
        assertEquals(150.0, actual.monthlyMaintenanceCost)
        val referenceDeadlines = vehicles.flatMap { v -> maintenance.filter { it.vehicleId == v.id }.map { v to it } }
            .sortedWith(compareBy({ it.second.nextDueDate }, { it.second.nextDueMileage ?: Int.MAX_VALUE }))
        assertEquals(referenceDeadlines, actual.upcomingDeadlines); assertEquals(maintenance.first(), actual.nextMaintenance)
    }
}
