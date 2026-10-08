package com.carmanager.app.core.domain.model

enum class MaintenanceType {
    OIL_CHANGE,
    TIRES,
    BRAKES,
    BELT,
    BATTERY,
    INSPECTION,
    REPAIR,
    TECHNICAL_INSPECTION,
    INSURANCE,
    OTHER,
}

data class MaintenanceRecord(
    val id: Long = 0,
    val vehicleId: Long,
    val type: MaintenanceType,
    val customLabel: String? = null,
    val date: Long,
    val mileage: Int,
    val cost: Double,
    val note: String? = null,
    val nextDueDate: Long? = null,
    val nextDueMileage: Int? = null,
    val ownerKey: String = com.carmanager.app.core.domain.session.LocalGarageOwner.KEY,
)
