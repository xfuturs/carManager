package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {
    @Query("SELECT fuel_records.* FROM fuel_records JOIN vehicles ON vehicles.id = fuel_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND fuel_records.vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long, ownerKey: String): Flow<List<FuelRecordEntity>>

    @Query("SELECT fuel_records.* FROM fuel_records JOIN vehicles ON vehicles.id = fuel_records.vehicleId WHERE vehicles.ownerKey = :ownerKey ORDER BY date DESC")
    fun observeAll(ownerKey: String): Flow<List<FuelRecordEntity>>

    @Query("SELECT fuel_records.* FROM fuel_records JOIN vehicles ON vehicles.id = fuel_records.vehicleId WHERE vehicles.ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<FuelRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(totalPrice), 0)
        FROM fuel_records JOIN vehicles ON vehicles.id = fuel_records.vehicleId
        WHERE vehicles.ownerKey = :ownerKey AND fuel_records.date >= :startOfMonth AND date < :endOfMonth
        """,
    )
    fun observeMonthlyTotal(startOfMonth: Long, endOfMonth: Long, ownerKey: String): Flow<Double>

    @Query("SELECT fuel_records.* FROM fuel_records JOIN vehicles ON vehicles.id = fuel_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND fuel_records.id = :id")
    suspend fun getById(id: Long, ownerKey: String): FuelRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecordEntity): Long

    @Delete
    suspend fun delete(record: FuelRecordEntity)
}
