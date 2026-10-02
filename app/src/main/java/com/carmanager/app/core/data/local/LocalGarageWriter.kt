package com.carmanager.app.core.data.local

import com.carmanager.app.core.data.local.dao.*
import com.carmanager.app.core.data.local.entity.MileageRecordEntity
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.validation.FormValidationException
import com.carmanager.app.core.domain.validation.GarageValidation
import javax.inject.Inject

/** Une action métier = une seule transaction OwnedDatabaseAccess, sans repository imbriqué. */
class LocalGarageWriter @Inject constructor(
    private val access: OwnedDatabaseAccess,
    private val vehicles: VehicleDao,
    private val fuel: FuelRecordDao,
    private val maintenance: MaintenanceDao,
    private val mileage: MileageDao
) {
    suspend fun saveVehicle(vehicle: Vehicle): Long = access.write(vehicle.ownerKey) {
        GarageValidation.vehicle(vehicle)
        val now = System.currentTimeMillis()
        if (vehicle.id == 0L) {
            val id = vehicles.insert(vehicle.copy(brand = vehicle.brand.trim(), model = vehicle.model.trim(), createdAt = now, updatedAt = now).toEntity())
            appendMileage(id, vehicle.currentMileage, MileageSource.MANUAL, now)
            id
        } else {
            val current = checkNotNull(vehicles.getById(vehicle.id, vehicle.ownerKey)) { "Véhicule absent de cet espace." }
            check(vehicles.updateDetails(vehicle.id, vehicle.ownerKey, vehicle.brand.trim(), vehicle.model.trim(), vehicle.year,
                vehicle.fuelType.toEntity(), vehicle.type.name, vehicle.powerHp, vehicle.licensePlate,
                vehicle.tankCapacity, vehicle.batteryCapacity, maxOf(now, current.updatedAt)) == 1)
            // Un formulaire ancien ne peut écraser un compteur plus récent.
            if (vehicle.currentMileage > current.currentMileage)
                recordMileage(vehicle.id, vehicle.ownerKey, vehicle.currentMileage, MileageSource.MANUAL, now)
            vehicle.id
        }
    }

    suspend fun saveFuel(record: FuelRecord): Long = access.write(record.ownerKey, record.vehicleId) {
        check(record.id == 0L) { "Cette opération crée un nouveau relevé." }
        val vehicle = checkNotNull(vehicles.getById(record.vehicleId, record.ownerKey))
        GarageValidation.fuel(record, if (record.isElectric) vehicle.batteryCapacity else vehicle.tankCapacity)
        val id = fuel.insert(record.toEntity())
        recordMileage(record.vehicleId, record.ownerKey, record.mileage, MileageSource.FUEL, record.date)
        id
    }

    suspend fun saveMaintenance(record: MaintenanceRecord): Long = access.write(record.ownerKey, record.vehicleId) {
        check(record.id == 0L) { "Cette opération crée une nouvelle intervention." }
        GarageValidation.maintenance(record)
        val id = maintenance.insert(record.toEntity())
        recordMileage(record.vehicleId, record.ownerKey, record.mileage, MileageSource.MAINTENANCE, record.date)
        id
    }

    suspend fun saveMileage(record: MileageRecord): Long = access.write(record.ownerKey, record.vehicleId) {
        check(record.id == 0L) { "Cette opération crée un nouveau relevé." }
        GarageValidation.mileage(record.mileage)
        GarageValidation.performedDate(record.date)
        recordMileage(record.vehicleId, record.ownerKey, record.mileage, record.source, record.date)
    }

    private suspend fun recordMileage(id: Long, owner: String, value: Int, source: MileageSource, date: Long): Long {
        val current = checkNotNull(vehicles.getById(id, owner)) { "Véhicule absent de cet espace." }
        if (source == MileageSource.MANUAL && value < current.currentMileage)
            throw FormValidationException("Le relevé manuel ne peut pas être inférieur au compteur actuel (${current.currentMileage} km).")
        if (value > current.currentMileage)
            check(vehicles.advanceMileage(id, owner, value, maxOf(System.currentTimeMillis(), current.updatedAt)) == 1)
        return appendMileage(id, value, source, date)
    }

    private suspend fun appendMileage(id: Long, value: Int, source: MileageSource, date: Long): Long =
        mileage.insert(MileageRecordEntity(vehicleId = id, mileage = value, source = source.name, date = date))
}
