package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.MileageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MileageDao {
    @Query("SELECT mileage_records.* FROM mileage_records JOIN vehicles ON vehicles.id = mileage_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND mileage_records.vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long, ownerKey: String): Flow<List<MileageRecordEntity>>

    @Query("SELECT mileage_records.* FROM mileage_records JOIN vehicles ON vehicles.id = mileage_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND mileage_records.vehicleId = :vehicleId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestByVehicle(vehicleId: Long, ownerKey: String): MileageRecordEntity?

    @Query("SELECT mileage_records.* FROM mileage_records JOIN vehicles ON vehicles.id = mileage_records.vehicleId WHERE vehicles.ownerKey = :ownerKey AND mileage_records.id = :id")
    suspend fun getById(id: Long, ownerKey: String): MileageRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mileageRecord: MileageRecordEntity): Long

    @Query("DELETE FROM mileage_records WHERE vehicleId = :vehicleId AND vehicleId IN (SELECT id FROM vehicles WHERE ownerKey = :ownerKey)")
    suspend fun deleteByVehicle(vehicleId: Long, ownerKey: String)
}
