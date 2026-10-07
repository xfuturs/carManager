package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.VehicleStats
import java.util.Locale

object VehicleStatsPresentation {
    fun consumption(stats: VehicleStats): String {
        val unit = if (stats.vehicle.fuelType == FuelType.ELECTRIC) "kWh/100 km" else "L/100 km"
        return if (stats.hasConsumption) String.format(Locale.FRANCE, "%.1f %s", stats.averageConsumption, unit)
        else "-- $unit"
    }
}
