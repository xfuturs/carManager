package com.carmanager.app.core.data.mapper

import com.carmanager.app.core.data.local.entity.FuelRecordEntity
import com.carmanager.app.core.data.local.entity.FuelTypeEntity
import com.carmanager.app.core.data.local.entity.MaintenanceRecordEntity
import com.carmanager.app.core.data.local.entity.MaintenanceTypeEntity
import com.carmanager.app.core.data.local.entity.VehicleEntity
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.VehicleType

// --- Vehicle Mappers ---

fun com.carmanager.app.core.data.local.entity.MileageRecordEntity.toDomain(ownerKey: String) =
    com.carmanager.app.core.domain.model.MileageRecord(id, vehicleId, date, mileage,
        com.carmanager.app.core.domain.model.MileageSource.valueOf(source), ownerKey)

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    brand = brand,
    model = model,
    year = year,
    currentMileage = currentMileage,
    fuelType = fuelType.toDomain(),
    type = VehicleType.valueOf(type),
    powerHp = powerHp,
    licensePlate = licensePlate,
    tankCapacity = tankCapacity,
    batteryCapacity = batteryCapacity,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerKey = ownerKey
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    brand = brand,
    model = model,
    year = year,
    currentMileage = currentMileage,
    fuelType = fuelType.toEntity(),
    type = type.name,
    powerHp = powerHp,
    licensePlate = licensePlate,
    tankCapacity = tankCapacity,
    batteryCapacity = batteryCapacity,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerKey = ownerKey
)

fun FuelTypeEntity.toDomain(): FuelType = FuelType.valueOf(name)
fun FuelType.toEntity(): FuelTypeEntity = FuelTypeEntity.valueOf(name)

// --- Fuel Mappers ---

fun FuelRecordEntity.toDomain(ownerKey: String): FuelRecord = FuelRecord(
    id = id,
    vehicleId = vehicleId,
    date = date,
    mileage = mileage,
    liters = liters,
    totalPrice = totalPrice,
    note = note,
    isElectric = isElectric,
    ownerKey = ownerKey
)

fun FuelRecord.toEntity(): FuelRecordEntity = FuelRecordEntity(
    id = id,
    vehicleId = vehicleId,
    date = date,
    mileage = mileage,
    liters = liters,
    totalPrice = totalPrice,
    note = note,
    isElectric = isElectric
)

// --- Maintenance Mappers ---

fun MaintenanceRecordEntity.toDomain(ownerKey: String): MaintenanceRecord = MaintenanceRecord(
    id = id,
    vehicleId = vehicleId,
    type = type.toDomain(),
    customLabel = customLabel,
    date = date,
    mileage = mileage,
    cost = cost,
    note = note,
    nextDueDate = nextDueDate,
    nextDueMileage = nextDueMileage,
    ownerKey = ownerKey
)

fun MaintenanceRecord.toEntity(): MaintenanceRecordEntity = MaintenanceRecordEntity(
    id = id,
    vehicleId = vehicleId,
    type = type.toEntity(),
    customLabel = customLabel,
    date = date,
    mileage = mileage,
    cost = cost,
    note = note,
    nextDueDate = nextDueDate,
    nextDueMileage = nextDueMileage
)

fun MaintenanceTypeEntity.toDomain(): MaintenanceType = MaintenanceType.valueOf(name)
fun MaintenanceType.toEntity(): MaintenanceTypeEntity = MaintenanceTypeEntity.valueOf(name)
