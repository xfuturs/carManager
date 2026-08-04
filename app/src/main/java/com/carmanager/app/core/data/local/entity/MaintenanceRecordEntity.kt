package com.carmanager.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId"), Index("date"), Index("nextDueDate")],
)
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val type: MaintenanceTypeEntity,
    val customLabel: String? = null,
    val date: Long,
    val mileage: Int,
    val cost: Double,
    val note: String? = null,
    val nextDueDate: Long? = null,
    val nextDueMileage: Int? = null,
    val remoteId: String? = null,
    val syncStatus: String = "LOCAL",
)
