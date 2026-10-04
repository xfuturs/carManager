package com.carmanager.app.features.dashboard

import com.carmanager.app.features.documents.ReportSummary
import com.carmanager.app.core.domain.model.ReportSection

data class ReportDraft(val vehicleId: Long, val previous: ReportSummary,
    val selected: Set<ReportSection> = ReportSection.entries.toSet()) {
    val canGenerate: Boolean get() = selected.isNotEmpty()
    fun toggle(section: ReportSection) = copy(selected = if (section in selected) selected - section else selected + section)
}
