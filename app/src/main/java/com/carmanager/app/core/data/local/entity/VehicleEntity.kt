package com.carmanager.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
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
)
