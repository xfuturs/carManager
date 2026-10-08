package com.carmanager.app.consistency

import com.carmanager.app.core.data.local.*
import com.carmanager.app.core.data.local.dao.*
import com.carmanager.app.core.data.local.entity.*
import com.carmanager.app.core.data.mapper.toDomain
import com.carmanager.app.core.data.repository.MileageRepositoryImpl
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry
import com.carmanager.app.ownership.testVehicle
import io.mockk.*

/** Fake transactionnel : rollback du contrat testé, pas une instance Room Android. */
class GarageFixture {
    val registry = TestDeletionRegistry()
    val session = WorkspaceSession(registry).apply { completeBootstrap() }
    val vehicles = linkedMapOf(1L to testVehicle("local:device", 1).copy(currentMileage = 1000, createdAt = 77, updatedAt = 88,
        remoteId = "legacy-id", syncStatus = "LEGACY"), 2L to testVehicle("firebase:B", 2))
    val fuelRows = mutableListOf<FuelRecordEntity>()
    val maintenanceRows = mutableListOf<MaintenanceRecordEntity>()
    val history = mutableListOf<MileageRecordEntity>()
    val vehicleDao = mockk<VehicleDao>()
    val fuelDao = mockk<FuelRecordDao>()
    val maintenanceDao = mockk<MaintenanceDao>()
    val mileageDao = mockk<MileageDao>()
    val access = mockk<OwnedDatabaseAccess>()
    val writer = LocalGarageWriter(access, vehicleDao, fuelDao, maintenanceDao, mileageDao)
    var failAt: String? = null
    var changeOwnerAfterHistory = false
    var transactions = 0

    init {
        coEvery { access.write<Any>(any(), any(), any()) } coAnswers {
            val owner = firstArg<String>()
            val parent = secondArg<Long?>()
            val action = thirdArg<suspend () -> Any>()
            val savedVehicles = vehicles.toMap()
            val savedFuel = fuelRows.toList()
            val savedMaintenance = maintenanceRows.toList()
            val savedHistory = history.toList()
            transactions++
            try {
                session.requireWritable(owner)
                if (parent != null) check(vehicles[parent]?.ownerKey == owner)
                val result = action()
                session.requireWritable(owner)
                result
            } catch (e: Throwable) {
                vehicles.clear(); vehicles.putAll(savedVehicles)
                fuelRows.clear(); fuelRows.addAll(savedFuel)
                maintenanceRows.clear(); maintenanceRows.addAll(savedMaintenance)
                history.clear(); history.addAll(savedHistory)
                throw e
            }
        }
        coEvery { vehicleDao.getById(any(), any()) } coAnswers { vehicles[firstArg<Long>()]?.takeIf { it.ownerKey == secondArg<String>() } }
        coEvery { vehicleDao.insert(any()) } coAnswers {
            val id = (vehicles.keys.maxOrNull() ?: 0) + 1
            vehicles[id] = firstArg<VehicleEntity>().copy(id = id)
            id
        }
        coEvery { vehicleDao.advanceMileage(any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val current = vehicles[id]
            val value = thirdArg<Int>()
            val count = if (current != null && current.ownerKey == secondArg<String>() && value > current.currentMileage) {
                vehicles[id] = current.copy(currentMileage = value, updatedAt = arg(3)); 1
            } else 0
            if (failAt == "advance") error("Injected advance failure")
            count
        }
        coEvery { vehicleDao.updateDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val current = vehicles[id]
            if (current == null || current.ownerKey != secondArg<String>()) 0 else {
                vehicles[id] = current.copy(brand = arg(2), model = arg(3), year = arg(4), fuelType = arg(5), type = arg(6),
                    powerHp = arg(7), licensePlate = arg(8), tankCapacity = arg(9), batteryCapacity = arg(10), updatedAt = arg(11))
                1
            }
        }
        coEvery { fuelDao.insert(any()) } coAnswers {
            val id = fuelRows.size.toLong() + 1
            fuelRows += firstArg<FuelRecordEntity>().copy(id = id)
            id
        }
        coEvery { maintenanceDao.insert(any()) } coAnswers {
            val id = maintenanceRows.size.toLong() + 1
            maintenanceRows += firstArg<MaintenanceRecordEntity>().copy(id = id)
            id
        }
        coEvery { mileageDao.insert(any()) } coAnswers {
            val id = history.size.toLong() + 1
            history += firstArg<MileageRecordEntity>().copy(id = id)
            if (changeOwnerAfterHistory) session.beginBootstrap()
            if (failAt == "history") error("Injected journal failure")
            id
        }
    }
    fun vehicle(): Vehicle = checkNotNull(vehicles[1]).toDomain()
    fun mileageRepository() = MileageRepositoryImpl(mileageDao, session, access, writer)
    fun fuel(value: Int = 1500) = FuelRecord(ownerKey = "local:device", vehicleId = 1, date = 0, mileage = value, liters = 12.5, totalPrice = 25.5)
    fun maintenance(value: Int = 1500) = MaintenanceRecord(ownerKey = "local:device", vehicleId = 1, type = MaintenanceType.OIL_CHANGE,
        date = 0, mileage = value, cost = 12.5)
}
