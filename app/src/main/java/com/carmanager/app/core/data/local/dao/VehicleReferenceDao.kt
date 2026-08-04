package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.VehicleReferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleReferenceDao {
    @Query("SELECT DISTINCT brand FROM vehicle_references ORDER BY brand ASC")
    fun getAllBrands(): Flow<List<String>>

    @Query("SELECT model FROM vehicle_references WHERE brand = :brand ORDER BY model ASC")
    fun getModelsForBrand(brand: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(references: List<VehicleReferenceEntity>)

    @Query("SELECT COUNT(*) FROM vehicle_references")
    suspend fun getCount(): Int
}
