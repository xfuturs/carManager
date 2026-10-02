package com.carmanager.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles", indices = [Index("ownerKey")])
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val brand: String,
    val model: String,
    val year: Int,
    val currentMileage: Int,
    val fuelType: FuelTypeEntity,
    val type: String = "CAR", // CAR, MOTORCYCLE, UTILITY
    val powerHp: Int?,
    val licensePlate: String?,
    val tankCapacity: Double? = null,
    val batteryCapacity: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Réservé pour synchronisation cloud future */
    val remoteId: String? = null,
    val syncStatus: String = "LOCAL",
    @ColumnInfo(defaultValue = "'guest:local'")
    val ownerKey: String = "guest:local",
)
