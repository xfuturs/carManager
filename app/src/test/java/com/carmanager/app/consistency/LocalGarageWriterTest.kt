package com.carmanager.app.consistency

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.validation.FormValidationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LocalGarageWriterTest {
    @Test fun `fuel failures roll back inserted record odometer and journal then permit one retry`() = runTest {
        for (stage in listOf("advance", "history")) {
            val f = GarageFixture()
            val before = f.vehicles.toMap()
            f.failAt = stage
            assertNotNull(runCatching { f.writer.saveFuel(f.fuel()) }.exceptionOrNull())
            assertEquals(before, f.vehicles)
            assertTrue(f.fuelRows.isEmpty()); assertTrue(f.history.isEmpty())
            f.failAt = null
            f.writer.saveFuel(f.fuel())
            assertEquals(1, f.fuelRows.size); assertEquals(1, f.history.size)
            assertEquals(1500, f.vehicles[1]?.currentMileage)
            assertEquals(2, f.transactions)
        }
    }

    @Test fun `maintenance failures roll back all database rows without partial intervention`() = runTest {
        for (stage in listOf("advance", "history")) {
            val f = GarageFixture()
            val before = f.vehicles.toMap()
            f.failAt = stage
            assertNotNull(runCatching { f.writer.saveMaintenance(f.maintenance()) }.exceptionOrNull())
            assertEquals(before, f.vehicles)
            assertTrue(f.maintenanceRows.isEmpty()); assertTrue(f.history.isEmpty())
            f.failAt = null
            f.writer.saveMaintenance(f.maintenance())
            assertEquals(1, f.maintenanceRows.size); assertEquals(1, f.history.size)
            assertEquals(MileageSource.MAINTENANCE.name, f.history.single().source)
        }
    }

    @Test fun `owner transition during write rolls back while foreign and blocked owners cannot write`() = runTest {
        val f = GarageFixture()
        val before = f.vehicles.toMap()
        f.changeOwnerAfterHistory = true
        assertNotNull(runCatching { f.writer.saveFuel(f.fuel()) }.exceptionOrNull())
        assertEquals(before, f.vehicles); assertTrue(f.fuelRows.isEmpty()); assertTrue(f.history.isEmpty())
        assertNotNull(runCatching { f.writer.saveMaintenance(f.maintenance()) }.exceptionOrNull())
        f.session.setAuthenticatedUid("A")
        assertNotNull(runCatching { f.writer.saveFuel(f.fuel().copy(vehicleId = 2)) }.exceptionOrNull())
        f.registry.block("firebase:A")
        assertNotNull(runCatching { f.writer.saveMaintenance(f.maintenance()) }.exceptionOrNull())
        assertEquals(before, f.vehicles); assertTrue(f.maintenanceRows.isEmpty())
    }

    @Test fun `equal and lower historical fuel maintenance preserve odometer and accurate journal`() = runTest {
        val f = GarageFixture()
        f.writer.saveFuel(f.fuel(1000))
        f.writer.saveMaintenance(f.maintenance(900))
        assertEquals(1000, f.vehicles[1]?.currentMileage)
        assertEquals(listOf(1000, 900), f.history.map { it.mileage })
        assertEquals(listOf("FUEL", "MAINTENANCE"), f.history.map { it.source })
        assertEquals(listOf(0L, 0L), f.history.map { it.date })
        assertEquals(88L, f.vehicles[1]?.updatedAt)
    }

    @Test fun `manual greater and equal journal once lower fails without misleading history`() = runTest {
        val f = GarageFixture()
        fun reading(value: Int) = MileageRecord(vehicleId = 1, ownerKey = "firebase:A", date = 0, mileage = value, source = MileageSource.MANUAL)
        f.writer.saveMileage(reading(1500)); f.writer.saveMileage(reading(1500))
        assertEquals(listOf(1500, 1500), f.history.map { it.mileage })
        assertInstanceOf(FormValidationException::class.java, runCatching { f.writer.saveMileage(reading(1400)) }.exceptionOrNull())
        assertEquals(2, f.history.size); assertEquals(1500, f.vehicles[1]?.currentMileage)
        f.failAt = "history"
        assertNotNull(runCatching { f.writer.saveMileage(reading(2000)) }.exceptionOrNull())
        assertEquals(1500, f.vehicles[1]?.currentMileage)
    }

    @Test fun `vehicle creation includes one initial journal even at zero and rolls back on journal failure`() = runTest {
        val f = GarageFixture()
        val before = f.vehicles.toMap()
        val newVehicle = f.vehicle().copy(id = 0, currentMileage = 0, createdAt = 0)
        f.failAt = "history"
        assertNotNull(runCatching { f.writer.saveVehicle(newVehicle) }.exceptionOrNull())
        assertEquals(before, f.vehicles); assertTrue(f.history.isEmpty())
        f.failAt = null
        val id = f.writer.saveVehicle(newVehicle)
        assertEquals(0, f.history.single().mileage)
        assertEquals(id, f.history.single().vehicleId)
        assertEquals(f.vehicles[id]?.createdAt, f.history.single().date)
        assertEquals("MANUAL", f.history.single().source)
    }

    @Test fun `vehicle edit preserves created owner remote metadata and newer odometer`() = runTest {
        val f = GarageFixture()
        val stale = f.vehicle()
        f.vehicles[1] = f.vehicles[1]!!.copy(currentMileage = 4000)
        f.writer.saveVehicle(stale.copy(model = "Nouveau modèle", createdAt = 0, updatedAt = 0))
        val saved = f.vehicles[1]!!
        assertEquals(77L, saved.createdAt); assertEquals("firebase:A", saved.ownerKey)
        assertEquals("legacy-id", saved.remoteId); assertEquals("LEGACY", saved.syncStatus)
        assertEquals(4000, saved.currentMileage); assertEquals("Nouveau modèle", saved.model)
        assertTrue(saved.updatedAt >= 88); assertTrue(f.history.isEmpty())
        f.writer.saveVehicle(stale.copy(currentMileage = 5000))
        assertEquals(5000, f.vehicles[1]?.currentMileage)
        assertEquals(5000, f.history.single().mileage)
        assertEquals("MANUAL", f.history.single().source)
    }
}
