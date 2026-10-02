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
    @Query("SELECT maintenance_records.* FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND maintenance_records.vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long, ownerKey: String): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT maintenance_records.* FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId WHERE vehicles.ownerKey = :ownerKey ORDER BY date DESC")
    fun observeAll(ownerKey: String): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT maintenance_records.* FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId WHERE vehicles.ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<MaintenanceRecordEntity>

    @Query(
        """
        SELECT maintenance_records.* FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId
        WHERE vehicles.ownerKey = :ownerKey AND nextDueDate IS NOT NULL AND nextDueDate >= :now
        ORDER BY nextDueDate ASC
        LIMIT 1
        """,
    )
    fun observeNextUpcoming(now: Long, ownerKey: String): Flow<MaintenanceRecordEntity?>

    @Query(
        """
        SELECT COALESCE(SUM(cost), 0)
        FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId
        WHERE vehicles.ownerKey = :ownerKey AND maintenance_records.date >= :startOfMonth AND date < :endOfMonth
        """,
    )
    fun observeMonthlyTotal(startOfMonth: Long, endOfMonth: Long, ownerKey: String): Flow<Double>

    @Query("SELECT maintenance_records.* FROM maintenance_records JOIN vehicles ON vehicles.id = maintenance_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND maintenance_records.id = :id")
    suspend fun getById(id: Long, ownerKey: String): MaintenanceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MaintenanceRecordEntity): Long

    @Delete
    suspend fun delete(record: MaintenanceRecordEntity)
}
