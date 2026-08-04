package com.carmanager.app.core.data.local.converter

import androidx.room.TypeConverter
import com.carmanager.app.core.data.local.entity.FuelTypeEntity
import com.carmanager.app.core.data.local.entity.MaintenanceTypeEntity

class Converters {
    @TypeConverter
    fun fromFuelType(value: FuelTypeEntity): String = value.name

    @TypeConverter
    fun toFuelType(value: String): FuelTypeEntity = FuelTypeEntity.valueOf(value)

    @TypeConverter
    fun fromMaintenanceType(value: MaintenanceTypeEntity): String = value.name

    @TypeConverter
    fun toMaintenanceType(value: String): MaintenanceTypeEntity = MaintenanceTypeEntity.valueOf(value)
}
