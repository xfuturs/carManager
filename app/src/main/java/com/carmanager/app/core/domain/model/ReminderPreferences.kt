package com.carmanager.app.core.domain.model

enum class ReminderCategory(val label: String) {
    MAINTENANCE("Entretien"), TECHNICAL_INSPECTION("Contrôle technique"), INSURANCE("Assurance");
    companion object { fun of(type: MaintenanceType) = when (type) {
        MaintenanceType.TECHNICAL_INSPECTION -> TECHNICAL_INSPECTION
        MaintenanceType.INSURANCE -> INSURANCE
        else -> MAINTENANCE
    } }
}

data class ReminderPreferences(val enabled: Boolean = true, val maintenance: Boolean = true,
    val technicalInspection: Boolean = true, val insurance: Boolean = true, val leadDaysSet: Set<Int> = setOf(0)) {
    init { require(leadDaysSet.isNotEmpty() && leadDaysSet.all { it in LEAD_DAYS }) }
    fun toggleLead(days: Int): ReminderPreferences {
        require(days in LEAD_DAYS)
        if (days in leadDaysSet && leadDaysSet.size == 1) return this
        return copy(leadDaysSet = if (days in leadDaysSet) leadDaysSet - days else leadDaysSet + days)
    }
    fun categoryEnabled(category: ReminderCategory): Boolean = when (category) {
        ReminderCategory.MAINTENANCE -> maintenance
        ReminderCategory.TECHNICAL_INSPECTION -> technicalInspection
        ReminderCategory.INSURANCE -> insurance
    }
    fun allows(type: MaintenanceType) = enabled && categoryEnabled(ReminderCategory.of(type))
    fun withCategory(category: ReminderCategory, value: Boolean) = when (category) {
        ReminderCategory.MAINTENANCE -> copy(maintenance = value)
        ReminderCategory.TECHNICAL_INSPECTION -> copy(technicalInspection = value)
        ReminderCategory.INSURANCE -> copy(insurance = value)
    }
    companion object { val LEAD_DAYS = listOf(0, 1, 3, 7, 14, 30) }
}
