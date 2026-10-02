package com.carmanager.app.features.calculators

import kotlin.math.abs
import kotlin.math.max

enum class DistanceUnit { KM, MI }
enum class TripMode { FUEL, ELECTRIC }
enum class CheaperOption { FUEL, ELECTRIC, EQUAL }
data class TripEstimate(val quantity: Double, val cost: Double)
data class RechargeEstimate(val energyAdded: Double, val cost: Double)
data class ComparisonEstimate(
    val fuelCost: Double,
    val electricCost: Double,
    val difference: Double,
    val cheaper: CheaperOption,
    /** Économie par rapport à l'option la plus chère ; null si les deux sont gratuites. */
    val savingPercent: Double?,
)

/** Calculs sans Android, stockage ni réseau. Aucune valeur intermédiaire arrondie. */
object CostCalculator {
    private fun positive(value: Double) = require(value.isFinite() && value > 0)
    private fun price(value: Double) = require(value.isFinite() && value >= 0)
    private fun finite(value: Double): Double {
        require(value.isFinite())
        return value
    }

    fun distanceKm(distance: Double, unit: DistanceUnit): Double {
        positive(distance)
        return finite(if (unit == DistanceUnit.MI) distance * 1.609344 else distance)
    }

    fun trip(distance: Double, unit: DistanceUnit, consumptionPer100Km: Double, unitPrice: Double): TripEstimate {
        positive(consumptionPer100Km)
        price(unitPrice)
        val quantity = finite(distanceKm(distance, unit) / 100.0 * consumptionPer100Km)
        return TripEstimate(quantity, finite(quantity * unitPrice))
    }

    fun fuelFill(liters: Double, pricePerLiter: Double): Double {
        positive(liters)
        price(pricePerLiter)
        return finite(liters * pricePerLiter)
    }

    fun recharge(capacityKWh: Double, startPercent: Double, targetPercent: Double, pricePerKWh: Double): RechargeEstimate {
        positive(capacityKWh)
        price(pricePerKWh)
        require(startPercent.isFinite() && startPercent >= 0 && startPercent < 100)
        require(targetPercent.isFinite() && targetPercent > 0 && targetPercent <= 100 && targetPercent > startPercent)
        val energy = finite(capacityKWh * ((targetPercent - startPercent) / 100.0))
        return RechargeEstimate(energy, finite(energy * pricePerKWh))
    }

    fun compare(distance: Double, unit: DistanceUnit, fuelConsumption: Double, fuelPrice: Double,
                electricConsumption: Double, electricityPrice: Double): ComparisonEstimate {
        val fuel = trip(distance, unit, fuelConsumption, fuelPrice).cost
        val electric = trip(distance, unit, electricConsumption, electricityPrice).cost
        val difference = abs(fuel - electric)
        val highest = max(fuel, electric)
        return ComparisonEstimate(fuel, electric, difference, when {
            fuel < electric -> CheaperOption.FUEL
            electric < fuel -> CheaperOption.ELECTRIC
            else -> CheaperOption.EQUAL
        }, if (highest > 0) finite(difference / highest * 100.0) else null)
    }
}
