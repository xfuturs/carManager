package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.MaintenanceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records ORDER BY date DESC")
    fun observeAll(): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records")
    suspend fun getAll(): List<MaintenanceRecordEntity>

    @Query(
        """
        SELECT * FROM maintenance_records
        WHERE nextDueDate IS NOT NULL AND nextDueDate >= :now
        ORDER BY nextDueDate ASC
        LIMIT 1
        """,
    )
    fun observeNextUpcoming(now: Long): Flow<MaintenanceRecordEntity?>

    @Query(
        """
        SELECT COALESCE(SUM(cost), 0)
        FROM maintenance_records
        WHERE date >= :startOfMonth AND date < :endOfMonth
        """,
    )
    fun observeMonthlyTotal(startOfMonth: Long, endOfMonth: Long): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MaintenanceRecordEntity): Long

    @Delete
    suspend fun delete(record: MaintenanceRecordEntity)
}
