package com.carmanager.app.core.domain.model

enum class MileageSource {
    MANUAL,
    FUEL,
    MAINTENANCE
}

data class MileageRecord(
    val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val mileage: Int,
    val source: MileageSource,
    val ownerKey: String = com.carmanager.app.core.domain.session.LocalGarageOwner.KEY,
)
