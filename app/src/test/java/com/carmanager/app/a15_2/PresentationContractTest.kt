package com.carmanager.app.a15_2

import com.carmanager.app.core.util.*
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.features.calculators.CostCalculator
import com.carmanager.app.features.calculators.DistanceUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PresentationContractTest {
    @Test fun `42 point 50 keeps identical number across currency symbols with French formatting`() {
        assertEquals("42,50 €",CurrencyPresentation.format(42.5,"€"))
        assertEquals("42,50 $",CurrencyPresentation.format(42.5,"$"))
    }
    @Test fun `whole metric precision remains zero decimals`() {
        assertEquals("43 €",CurrencyPresentation.format(42.6,"€",0))
        assertEquals("43 €",CurrencyPresentation.format(42.5,"€",0))
    }
    @Test fun `legacy stored distance formats whole readings without guessing conversion`() {
        assertEquals("100 km",DistancePresentation.recorded(100,"km"))
        assertEquals("100 mi",DistancePresentation.recorded(100,"mi"))
        assertEquals("0 mi",DistancePresentation.recorded(0,"mi"))
    }
    @Test fun `new PDF preserves historical km convention even if miles preference is captured`() {
        val settings=ReportPresentationSettings("mi","$")
        assertEquals("mi",settings.distanceUnit); assertEquals("100 km",settings.mileage(100))
        val rows=StructuredReportRows.mileage(listOf(MileageRecord(vehicleId=1,date=0,mileage=100,source=MileageSource.MANUAL)),settings)
        assertEquals("100 km",rows.single().mileage)
    }
    @Test fun `fuel and charging PDF rows use captured symbol and unchanged quantity and odometer`() {
        val records=listOf(fill(cost=42.5),fill(2,1000,electric=true,cost=42.5))
        val before=records.toList(); val rows=StructuredReportRows.fuel(records,ReportPresentationSettings("mi","$"))
        assertEquals(listOf("42,50 $","42,50 $"),rows.map { it.cost })
        assertEquals(listOf("0 km","1\u202f000 km"),rows.map { it.mileage })
        assertTrue(rows[0].description.contains("50,00 L")); assertTrue(rows[1].description.contains("50,00 kWh"))
        assertEquals(before,records)
    }
    @Test fun `A6 explicitly unit tagged calculator retains exact mile factor`() {
        assertEquals(160.9344,CostCalculator.distanceKm(100.0,DistanceUnit.MI),0.00000001)
        assertEquals(100.0,CostCalculator.distanceKm(100.0,DistanceUnit.KM))
    }
}
