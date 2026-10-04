package com.carmanager.app.features.documents

import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportSummary(val count: Int, val latest: Long?) {
    companion object {
        fun from(documents: List<Document>): ReportSummary {
            val reports = documents.filter { it.category == DocumentCategory.REPORTS }
            return ReportSummary(reports.size, reports.maxOfOrNull { it.date })
        }
    }
}

internal val ordinaryDocumentCategories = DocumentCategory.entries.filterNot { it == DocumentCategory.REPORTS }
internal fun reportDate(date: Long): String = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(Date(date))
