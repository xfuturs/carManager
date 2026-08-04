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
    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records ORDER BY date DESC")
    fun observeAll(): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records")
    suspend fun getAll(): List<FuelRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(totalPrice), 0)
        FROM fuel_records
        WHERE date >= :startOfMonth AND date < :endOfMonth
        """,
    )
    fun observeMonthlyTotal(startOfMonth: Long, endOfMonth: Long): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecordEntity): Long

    @Delete
    suspend fun delete(record: FuelRecordEntity)
}
