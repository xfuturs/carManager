package com.carmanager.app.consistency

import com.carmanager.app.core.domain.validation.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class GarageValidationTest {
    @Test fun `decimal point comma and optional blanks are parsed without changing price semantics`() {
        assertEquals(12.5, NumericInput.decimal("12.5", "Prix"))
        assertEquals(12.5, NumericInput.decimal(" 12,5 ", "Prix"))
        assertNull(NumericInput.optionalDecimal(" ", "Capacité"))
        assertNull(NumericInput.optionalInteger("", "Puissance"))
        assertEquals(0, NumericInput.integer("0", "Kilométrage"))
    }

    @Test fun `malformed nonfinite overflow and mixed separators are rejected rather than discarded`() {
        for (text in listOf("NaN", "Infinity", "-Infinity", "12,5.6", "12..5", "1 234,5", "1e5", "abc", "", "9".repeat(400)))
            assertThrows(FormValidationException::class.java) { NumericInput.decimal(text, "Prix") }
        for (text in listOf("-1", "12,5", "abc", "2147483648", ""))
            assertThrows(FormValidationException::class.java) { NumericInput.integer(text, "Kilométrage") }
        assertThrows(FormValidationException::class.java) { NumericInput.optionalDecimal("abc", "Capacité") }
    }

    @Test fun `vehicle requires meaningful brand model valid year power and finite capacities`() {
        val vehicle = GarageFixture().vehicle()
        GarageValidation.vehicle(vehicle.copy(brand = "Artisan", model = "Prototype", tankCapacity = 0.5))
        for (invalid in listOf(vehicle.copy(brand = " "), vehicle.copy(model = "!!!"), vehicle.copy(year = 0),
            vehicle.copy(year = LocalDate.now().year + 1), vehicle.copy(powerHp = 0), vehicle.copy(powerHp = 3001),
            vehicle.copy(tankCapacity = Double.NaN), vehicle.copy(batteryCapacity = Double.POSITIVE_INFINITY), vehicle.copy(tankCapacity = -1.0)))
            assertThrows(FormValidationException::class.java) { GarageValidation.vehicle(invalid) }
    }

    @Test fun `fuel and electric require finite positive quantity total price valid mileage and capacity`() {
        val record = GarageFixture().fuel()
        for (electric in listOf(false, true)) {
            GarageValidation.fuel(record.copy(isElectric = electric), 20.0)
            for (invalid in listOf(record.copy(liters = 0.0), record.copy(liters = -1.0), record.copy(totalPrice = 0.0),
                record.copy(totalPrice = -1.0), record.copy(liters = Double.NaN), record.copy(totalPrice = Double.POSITIVE_INFINITY),
                record.copy(mileage = -1)))
                assertThrows(FormValidationException::class.java) { GarageValidation.fuel(invalid.copy(isElectric = electric)) }
            assertThrows(FormValidationException::class.java) { GarageValidation.fuel(record.copy(isElectric = electric), 10.0) }
            GarageValidation.fuel(record.copy(liters = 10.5, isElectric = electric), 10.0)
        }
    }

    @Test fun `maintenance rejects invalid costs performed future dates and incoherent next due values`() {
        val record = GarageFixture().maintenance()
        GarageValidation.maintenance(record.copy(cost = 0.0))
        GarageValidation.maintenance(record.copy(cost = NumericInput.decimal("12,5", "Coût")))
        val future = LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        for (invalid in listOf(record.copy(cost = -1.0), record.copy(cost = Double.NaN), record.copy(cost = Double.POSITIVE_INFINITY),
            record.copy(date = future), record.copy(nextDueMileage = 1), record.copy(nextDueDate = -86_400_000)))
            assertThrows(FormValidationException::class.java) { GarageValidation.maintenance(invalid) }
        assertThrows(FormValidationException::class.java) { GarageValidation.fuel(GarageFixture().fuel().copy(date = future)) }
    }
}
