package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.carmanager.app.core.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT DISTINCT ownerKey FROM vehicles WHERE ownerKey != :canonical")
    suspend fun legacyOwners(canonical: String): List<String>

    @Query("UPDATE vehicles SET ownerKey = :canonical WHERE ownerKey != :canonical")
    suspend fun consolidateOwners(canonical: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM vehicles WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query("SELECT * FROM vehicles WHERE ownerKey = :ownerKey ORDER BY brand ASC, model ASC")
    fun observeAll(ownerKey: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id AND ownerKey = :ownerKey")
    fun observeById(id: Long, ownerKey: String): Flow<VehicleEntity?>

    @Query("SELECT COUNT(*) FROM vehicles WHERE ownerKey = :ownerKey")
    fun observeCount(ownerKey: String): Flow<Int>

    @Query("SELECT * FROM vehicles WHERE id = :id AND ownerKey = :ownerKey")
    suspend fun getById(id: Long, ownerKey: String): VehicleEntity?

    @Query("DELETE FROM vehicles WHERE ownerKey = :ownerKey")
    suspend fun deleteOwner(ownerKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET currentMileage = :mileage, updatedAt = :updatedAt WHERE id = :id AND ownerKey = :ownerKey AND currentMileage < :mileage")
    suspend fun advanceMileage(id: Long, ownerKey: String, mileage: Int, updatedAt: Long): Int

    @Query("UPDATE vehicles SET brand = :brand, model = :model, year = :year, fuelType = :fuelType, type = :type, powerHp = :powerHp, licensePlate = :licensePlate, tankCapacity = :tankCapacity, batteryCapacity = :batteryCapacity, updatedAt = :updatedAt WHERE id = :id AND ownerKey = :ownerKey")
    suspend fun updateDetails(id: Long, ownerKey: String, brand: String, model: String, year: Int,
        fuelType: com.carmanager.app.core.data.local.entity.FuelTypeEntity, type: String,
        powerHp: Int?, licensePlate: String?, tankCapacity: Double?, batteryCapacity: Double?, updatedAt: Long): Int

    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}
