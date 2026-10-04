package com.carmanager.app.core.util

import com.carmanager.app.core.domain.model.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class StructuredReportRow(val date: String, val description: String, val mileage: String, val cost: String? = null)

/** Valeurs locales formatées seulement ; aucune consommation ni agrégat recalculé. */
internal object StructuredReportRows {
    private fun date(value: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(value))
    private fun integer(value: Int) = NumberFormat.getIntegerInstance(Locale.FRANCE).format(value) + " km"
    private fun decimal(value: Double) = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(value)
    fun fuel(records: List<FuelRecord>): List<StructuredReportRow> = records.map {
        val label = if (it.isElectric) "Recharge" else "Plein"
        val quantity = decimal(it.liters) + if (it.isElectric) " kWh" else " L"
        StructuredReportRow(date(it.date), "$label • $quantity" + (it.note?.takeIf(String::isNotBlank)?.let { note -> "\nNote : $note" } ?: ""),
            integer(it.mileage), "${decimal(it.totalPrice)} €")
    }
    fun mileage(records: List<MileageRecord>): List<StructuredReportRow> = records.map {
        StructuredReportRow(date(it.date), when (it.source) {
            MileageSource.MANUAL -> "Relevé manuel"
            MileageSource.FUEL -> "Plein ou recharge"
            MileageSource.MAINTENANCE -> "Intervention"
        }, integer(it.mileage))
    }
    fun selectedOrder(sections: Set<ReportSection>): List<ReportSection> {
        require(sections.isNotEmpty())
        return ReportSection.entries.filter { it in sections }
    }
    fun <T> selectedSections(sections: Set<ReportSection>, build: (ReportSection) -> T): List<T> = selectedOrder(sections).map(build)
}
