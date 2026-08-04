package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.MileageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MileageDao {
    @Query("SELECT * FROM mileage_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<MileageRecordEntity>>

    @Query("SELECT * FROM mileage_records WHERE vehicleId = :vehicleId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestByVehicle(vehicleId: Long): MileageRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mileageRecord: MileageRecordEntity): Long

    @Query("DELETE FROM mileage_records WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
