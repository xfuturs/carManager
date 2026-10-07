package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.features.dashboard.DashboardCalculator
import java.time.Instant
import java.time.ZoneId

internal val october = TemporalContext(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Paris"))
internal fun vehicle(id: Long = 1, type: FuelType = FuelType.GASOLINE, owner: String = "guest:local") =
    Vehicle(id, "Renault", "Clio", 2020, 5000, type, powerHp = 90, licensePlate = null,
        createdAt = 0, updatedAt = 0, ownerKey = owner)
internal fun fill(id: Long = 1, mileage: Int = 0, electric: Boolean = false, amount: Double = 50.0,
                  date: Long = october.monthStart + id * 1000, cost: Double = 70.0, vehicleId: Long = 1,
                  owner: String = "guest:local") =
    FuelRecord(id, vehicleId, date, mileage, amount, cost, isElectric = electric, ownerKey = owner)
internal fun intervention(id: Long = 1, date: Long = october.monthStart, cost: Double = 40.0,
                          due: Long? = null, dueMileage: Int? = null, type: MaintenanceType = MaintenanceType.OIL_CHANGE,
                          vehicleId: Long = 1, owner: String = "guest:local") =
    MaintenanceRecord(id, vehicleId, type, date = date, mileage = 1000, cost = cost,
        nextDueDate = due, nextDueMileage = dueMileage, ownerKey = owner)
internal fun document(id: Long = 1, category: DocumentCategory = DocumentCategory.OTHER,
                      title: String = "Document", vehicleId: Long = 1, owner: String = "guest:local") =
    Document(id, vehicleId, title, category, "private.pdf", 0, owner)
internal fun summary(type: FuelType, fuel: List<FuelRecord>): VehicleStats =
    DashboardCalculator.calculate(listOf(vehicle(type = type)), fuel, emptyList(), emptyList(), october).vehicles.single()
