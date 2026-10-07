package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.features.dashboard.VehicleStatsPresentation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConsumptionAvailabilityTest {
    @Test fun `thermal two fills has average and matching sample count`() {
        val stats = summary(FuelType.GASOLINE, listOf(fill(), fill(2,1000)))
        assertEquals(5.0, stats.averageConsumption); assertEquals(2, stats.relevantConsumptionSampleCount)
        assertTrue(stats.hasConsumption); assertEquals("5,0 L/100 km", VehicleStatsPresentation.consumption(stats))
    }
    @Test fun `thermal one fill is unavailable`() { unavailable(FuelType.GASOLINE,listOf(fill())) }
    @Test fun `electric two charges has electric average`() {
        val stats=summary(FuelType.ELECTRIC,listOf(fill(electric=true,amount=20.0),fill(2,100,electric=true,amount=20.0)))
        assertEquals(20.0, stats.averageConsumption); assertEquals(2,stats.relevantConsumptionSampleCount)
        assertEquals("20,0 kWh/100 km",VehicleStatsPresentation.consumption(stats))
    }
    @Test fun `thermal records cannot make electric series available`() { unavailable(FuelType.ELECTRIC,listOf(fill(),fill(2,1000))) }
    @Test fun `hybrid two charges has no fake thermal zero`() { unavailable(FuelType.HYBRID,listOf(fill(electric=true),fill(2,1000,electric=true))) }
    @Test fun `hybrid mixed history uses only thermal pairs`() {
        val stats=summary(FuelType.HYBRID,listOf(fill(),fill(2,1000),fill(3,1500,electric=true,amount=500.0)))
        assertEquals(5.0,stats.averageConsumption); assertEquals(2,stats.relevantConsumptionSampleCount)
        assertEquals(3,stats.fuelRecordsCount); assertEquals(listOf(5.0),stats.consumptionHistory)
        assertEquals("5,0 L/100 km",VehicleStatsPresentation.consumption(stats))
    }
    @Test fun `one thermal and many charges is unavailable`() { unavailable(FuelType.HYBRID,listOf(fill())+(2L..10L).map { fill(it,(it*100).toInt(),electric=true) }) }
    @Test fun `duplicate odometers have no valid sample`() { unavailable(FuelType.GASOLINE,listOf(fill(),fill(2))) }
    @Test fun `decreasing odometers have no valid sample`() { unavailable(FuelType.GASOLINE,listOf(fill(mileage=1000),fill(2,500))) }
    @Test fun `zero amount cannot count as a consumption sample`() { unavailable(FuelType.GASOLINE,listOf(fill(),fill(2,1000,amount=0.0))) }
    @Test fun `nonfinite amount cannot count as a consumption sample`() { unavailable(FuelType.ELECTRIC,listOf(fill(electric=true),fill(2,1000,electric=true,amount=Double.NaN))) }
    @Test fun `invalid pairs do not dilute weighted average or add valid count`() {
        val stats=summary(FuelType.GASOLINE,listOf(fill(),fill(2,1000),fill(3,1000,amount=1000.0)))
        assertEquals(5.0,stats.averageConsumption); assertEquals(2,stats.relevantConsumptionSampleCount)
        assertEquals(listOf(5.0),stats.consumptionHistory)
    }
    private fun unavailable(type: FuelType, records: List<FuelRecord>) {
        val stats=summary(type,records); assertNull(stats.averageConsumption); assertFalse(stats.hasConsumption)
        assertEquals(0,stats.relevantConsumptionSampleCount); assertTrue(stats.consumptionHistory.isEmpty())
        assertTrue(VehicleStatsPresentation.consumption(stats).startsWith("-- "))
    }
}
