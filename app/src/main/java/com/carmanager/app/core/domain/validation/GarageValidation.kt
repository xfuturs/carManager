package com.carmanager.app.core.domain.validation

import com.carmanager.app.core.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FormValidationException(message: String) : IllegalArgumentException(message)

/** Saisie décimale sans séparateur de milliers, exposant ou valeur non finie. */
object NumericInput {
    fun decimal(text: String, label: String): Double {
        val value = text.trim()
        if (!Regex("[+-]?[0-9]+([.,][0-9]+)?").matches(value))
            throw FormValidationException("$label : saisissez un nombre valide (ex. 12,5).")
        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw FormValidationException("$label : valeur trop grande ou invalide.")
    }
    fun integer(text: String, label: String): Int {
        val value = text.trim()
        if (!Regex("[0-9]+").matches(value))
            throw FormValidationException("$label : saisissez un entier positif ou nul.")
        return value.toIntOrNull() ?: throw FormValidationException("$label : valeur trop grande.")
    }
    fun optionalDecimal(text: String, label: String): Double? =
        if (text.isBlank()) null else decimal(text, label)
    fun optionalInteger(text: String, label: String): Int? =
        if (text.isBlank()) null else integer(text, label)
}

object GarageValidation {
    private fun valid(condition: Boolean, message: String) {
        if (!condition) throw FormValidationException(message)
    }
    fun day(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    fun performedDate(date: Long) = valid(day(date) <= LocalDate.now(), "La date de réalisation ne peut pas être future.")
    fun mileage(value: Int) = valid(value >= 0, "Le kilométrage doit être positif ou nul.")
    fun vehicle(vehicle: Vehicle) {
        valid(vehicle.brand.any { it.isLetterOrDigit() } && vehicle.model.any { it.isLetterOrDigit() }, "La marque et le modèle sont obligatoires.")
        valid(vehicle.year in 1900..LocalDate.now().year, "L'année doit être comprise entre 1900 et ${LocalDate.now().year}.")
        mileage(vehicle.currentMileage)
        valid(vehicle.powerHp == null || vehicle.powerHp in 1..3000, "La puissance doit être comprise entre 1 et 3000 ch.")
        for (capacity in listOfNotNull(vehicle.tankCapacity, vehicle.batteryCapacity))
            valid(capacity.isFinite() && capacity > 0 && capacity <= 1000, "La capacité doit être supérieure à 0 et au plus égale à 1000 L/kWh.")
    }
    fun fuel(record: FuelRecord, capacity: Double? = null) {
        valid(record.liters.isFinite() && record.liters > 0, "La quantité (L/kWh) doit être supérieure à zéro.")
        valid(record.totalPrice.isFinite() && record.totalPrice > 0, "Le prix total doit être supérieur à zéro.")
        mileage(record.mileage)
        performedDate(record.date)
        valid(capacity == null || record.liters <= capacity * 1.05, "La quantité dépasse la capacité du véhicule (tolérance 5 %).")
    }
    fun maintenance(record: MaintenanceRecord) {
        valid(record.cost.isFinite() && record.cost >= 0, "Le coût doit être positif ou nul.")
        mileage(record.mileage)
        performedDate(record.date)
        valid(record.nextDueMileage == null || record.nextDueMileage >= record.mileage, "La prochaine échéance kilométrique doit être au moins égale au relevé.")
        valid(record.nextDueDate == null || day(record.nextDueDate) >= day(record.date), "La prochaine échéance ne peut pas précéder la réalisation.")
    }
}
