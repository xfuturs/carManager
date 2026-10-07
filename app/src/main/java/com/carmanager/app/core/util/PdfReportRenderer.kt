package com.carmanager.app.core.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.ReportSection
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Présentation seulement ; les entrées, leur ordre et les contrats fichier/partage sont inchangés. */
internal class PdfReportRenderer(private val context: Context,
    private val presentation: ReportPresentationSettings = ReportPresentationSettings()) {
    private val teal = Color.rgb(0, 106, 98)
    private val charcoal = Color.rgb(35, 43, 45)
    private val muted = Color.rgb(92, 103, 105)
    private val light = Color.rgb(244, 247, 246)
    private val divider = Color.rgb(218, 226, 224)
    private val left = PdfReportGeometry.MARGIN
    private val width = PdfReportGeometry.CONTENT_WIDTH
    private val right = left + width
    private val missing = "Non renseigné"
    // Kilométrages historiques sans métadonnée d’unité : conserver la convention PDF km, sans conversion.
    private val integers = NumberFormat.getIntegerInstance(Locale.FRANCE)
    private val money = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }
    private val dates = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    private val paints = PdfTextStyle.entries.associateWith { style ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = when (style) {
                PdfTextStyle.TITLE -> 22f
                PdfTextStyle.VEHICLE, PdfTextStyle.VALUE -> 18f
                PdfTextStyle.SECTION -> 14f
                PdfTextStyle.BRAND -> 11f
                PdfTextStyle.BODY, PdfTextStyle.BOLD -> 10.5f
                PdfTextStyle.META -> 9.5f
                PdfTextStyle.FOOTER -> 8f
            }
            color = when (style) {
                PdfTextStyle.BRAND, PdfTextStyle.SECTION, PdfTextStyle.VALUE -> teal
                PdfTextStyle.META, PdfTextStyle.FOOTER -> muted
                else -> charcoal
            }
            typeface = Typeface.create(Typeface.DEFAULT, if (style in setOf(PdfTextStyle.BRAND,
                    PdfTextStyle.TITLE, PdfTextStyle.VEHICLE, PdfTextStyle.SECTION, PdfTextStyle.BOLD,
                    PdfTextStyle.VALUE)) Typeface.BOLD else Typeface.NORMAL)
        }
    }
    private fun lines(text: String, style: PdfTextStyle, columnWidth: Float): List<PdfTextLine> =
        PdfTextWrapping.wrap(text, columnWidth, paints.getValue(style)::measureText).map { PdfTextLine(it, style) }
    private fun block(columns: List<PdfColumn>, kind: PdfBlockKind = PdfBlockKind.TEXT,
                      top: Float = 0f, bottom: Float = 0f, gap: Float = 0f, minimumLine: Float = 0f): PdfBlock {
        val lineHeight = maxOf(minimumLine, columns.flatMap { it.lines }.maxOfOrNull {
            ceil(paints.getValue(it.style).fontSpacing)
        } ?: ceil(paints.getValue(PdfTextStyle.BODY).fontSpacing))
        return PdfBlock(columns, lineHeight, top, bottom, gap, kind)
    }
    private fun text(text: String, style: PdfTextStyle, gap: Float = 0f, kind: PdfBlockKind = PdfBlockKind.TEXT) =
        block(listOf(PdfColumn(left, width, lines(text, style, width))), kind, gap = gap,
            bottom = if (kind == PdfBlockKind.SECTION) 6f else 0f)
    private fun date(value: Long) = dates.format(Date(value))
    private fun mileage(value: Int) = presentation.mileage(value)
    private fun identity(vehicle: Vehicle) = listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { missing }

    fun render(document: PdfDocument, vehicle: Vehicle, records: List<MaintenanceRecord>,
        fuelRecords: List<FuelRecord> = emptyList(), mileageRecords: List<MileageRecord> = emptyList(),
        sections: Set<ReportSection> = ReportSection.entries.toSet(), generatedAt: Long = System.currentTimeMillis()) {
        require(sections.isNotEmpty())
        val generated = date(generatedAt)
        val energy = context.getString(when (vehicle.fuelType) {
            FuelType.GASOLINE -> R.string.fuel_gasoline
            FuelType.DIESEL -> R.string.fuel_diesel
            FuelType.ELECTRIC -> R.string.fuel_electric
            FuelType.HYBRID -> R.string.fuel_hybrid
            FuelType.LPG -> R.string.fuel_lpg
            FuelType.OTHER -> R.string.fuel_other
        })
        val metricWidth = (width - 12f) / 2f
        fun metric(x: Float, label: String, value: String) = PdfColumn(x + 12f, metricWidth - 24f,
            lines(label, PdfTextStyle.META, metricWidth - 24f) + lines(value, PdfTextStyle.VALUE, metricWidth - 24f))
        fun detail(x: Float, label: String, value: String) = PdfColumn(x, metricWidth,
            lines(label, PdfTextStyle.META, metricWidth) + lines(value, PdfTextStyle.BODY, metricWidth))
        val leading = mutableListOf(
            block(listOf(PdfColumn(left + 44f, width - 44f, lines("Car Manager", PdfTextStyle.BRAND, width - 44f))),
                PdfBlockKind.BRAND, gap = 10f, minimumLine = 32f),
            text("Rapport du véhicule", PdfTextStyle.TITLE, gap = 6f),
            text("Généré le $generated", PdfTextStyle.META, gap = 18f),
            text(identity(vehicle), PdfTextStyle.VEHICLE, gap = if (ReportSection.VEHICLE_INFORMATION in sections) 6f else 14f)
        )
        if (ReportSection.VEHICLE_INFORMATION in sections) {
            leading += text("${vehicle.year.takeIf { it > 0 } ?: missing} • $energy", PdfTextStyle.META, gap = 14f)
            leading += block(listOf(metric(left, "Kilométrage actuel", mileage(vehicle.currentMileage)),
                metric(left + metricWidth + 12f, "Énergie", energy)), PdfBlockKind.METRICS, top = 10f, bottom = 10f, gap = 18f)
        }
        val info = PdfSection(text("Informations du véhicule", PdfTextStyle.SECTION, gap = 8f, kind = PdfBlockKind.SECTION), null,
            listOf(block(listOf(detail(left, "Plaque d’immatriculation", vehicle.licensePlate?.takeIf { it.isNotBlank() } ?: missing),
                detail(left + metricWidth + 12f, "Puissance", vehicle.powerHp?.takeIf { it > 0 }?.let { "${integers.format(it)} ch" } ?: missing)), gap = 14f),
                text("Rapport généré à partir des informations enregistrées dans Car Manager.", PdfTextStyle.META, gap = 18f)))
        val tableHeader = block(listOf(
            PdfColumn(left, 65f, lines("Date", PdfTextStyle.META, 65f)),
            PdfColumn(left + 77f, 246f, lines("Opération", PdfTextStyle.META, 246f)),
            PdfColumn(left + 339f, 80f, lines("Kilométrage", PdfTextStyle.META, 80f), true),
            PdfColumn(left + 433f, 86f, lines("Coût", PdfTextStyle.META, 86f), true)
        ), PdfBlockKind.TABLE_HEADER, top = 7f, bottom = 7f)
        val maintenance = PdfSection(text("Historique des interventions", PdfTextStyle.SECTION, gap = 8f, kind = PdfBlockKind.SECTION),
            tableHeader.takeIf { records.isNotEmpty() },
            if (records.isEmpty()) listOf(text("Aucun entretien enregistré.", PdfTextStyle.BODY, gap = 8f))
            else records.map(::maintenanceRow))
        fun structured(section: ReportSection, rows: List<StructuredReportRow>, empty: String): PdfSection {
            val header = if (rows.isEmpty()) null else block(listOf(
                PdfColumn(left, 65f, lines("Date", PdfTextStyle.META, 65f)),
                PdfColumn(left + 77f, 246f, lines(if (section == ReportSection.MILEAGE_HISTORY) "Source" else "Plein / recharge", PdfTextStyle.META, 246f)),
                PdfColumn(left + 339f, 80f, lines("Kilométrage", PdfTextStyle.META, 80f), true),
                PdfColumn(left + 433f, 86f, lines(if (section == ReportSection.MILEAGE_HISTORY) "" else "Coût", PdfTextStyle.META, 86f), true)
            ), PdfBlockKind.TABLE_HEADER, top = 7f, bottom = 7f)
            val blocks = if (rows.isEmpty()) listOf(text(empty, PdfTextStyle.BODY, gap = 8f)) else rows.map { row ->
                block(listOf(PdfColumn(left, 65f, lines(row.date, PdfTextStyle.BODY, 65f)),
                    PdfColumn(left + 77f, 246f, lines(row.description, PdfTextStyle.BODY, 246f)),
                    PdfColumn(left + 339f, 80f, lines(row.mileage, PdfTextStyle.BODY, 80f), true),
                    PdfColumn(left + 433f, 86f, lines(row.cost ?: "", PdfTextStyle.BODY, 86f), true)),
                    PdfBlockKind.RECORD, top = 9f, bottom = 9f).copy(continuedLabel = "Suite de l’enregistrement précédent")
            }
            return PdfSection(text(section.label, PdfTextStyle.SECTION, gap = 8f, kind = PdfBlockKind.SECTION), header, blocks)
        }
        val selected = StructuredReportRows.selectedSections(sections) { section -> when (section) {
            ReportSection.VEHICLE_INFORMATION -> info
            ReportSection.MILEAGE_HISTORY -> structured(section, StructuredReportRows.mileage(mileageRecords, presentation), "Aucun relevé kilométrique enregistré.")
            ReportSection.FUEL_AND_CHARGING_HISTORY -> structured(section, StructuredReportRows.fuel(fuelRecords, presentation), "Aucun plein ni recharge enregistré.")
            ReportSection.MAINTENANCE_HISTORY -> maintenance
        } }
        val plan = PdfPagePlanner().plan(leading, selected)
        val pages = plan.maxOf { it.page }
        val compactName = PdfTextWrapping.compactHeader(PdfTextWrapping.wrap(identity(vehicle), width,
            paints.getValue(PdfTextStyle.META)::measureText), width, paints.getValue(PdfTextStyle.META)::measureText)
        plan.groupBy { it.page }.forEach { (number, placements) ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PdfReportGeometry.PAGE_WIDTH,
                PdfReportGeometry.PAGE_HEIGHT, number).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            if (number > 1) continuationHeader(canvas, compactName)
            placements.forEach { draw(canvas, it) }
            footer(canvas, generated, number, pages)
            document.finishPage(page)
        }
    }

    private fun maintenanceRow(record: MaintenanceRecord): PdfBlock {
        val label = record.customLabel?.takeIf { it.isNotBlank() } ?: context.getString(when (record.type) {
            MaintenanceType.OIL_CHANGE -> R.string.maintenance_oil_change
            MaintenanceType.TIRES -> R.string.maintenance_tires
            MaintenanceType.BRAKES -> R.string.maintenance_brakes
            MaintenanceType.BELT -> R.string.maintenance_belt
            MaintenanceType.BATTERY -> R.string.maintenance_battery
            MaintenanceType.INSPECTION -> R.string.maintenance_inspection
            MaintenanceType.REPAIR -> R.string.maintenance_repair
            MaintenanceType.TECHNICAL_INSPECTION -> R.string.maintenance_technical_inspection
            MaintenanceType.INSURANCE -> R.string.maintenance_insurance
            MaintenanceType.OTHER -> R.string.maintenance_other
        })
        val operation = lines(label, PdfTextStyle.BOLD, 246f).toMutableList()
        record.note?.takeIf { it.isNotBlank() }?.let { operation += lines("Note : $it", PdfTextStyle.META, 246f) }
        record.nextDueDate?.let { operation += lines("Prochaine échéance : ${date(it)}", PdfTextStyle.META, 246f) }
        record.nextDueMileage?.let { operation += lines("Prochaine échéance : ${mileage(it)}", PdfTextStyle.META, 246f) }
        return block(listOf(
            PdfColumn(left, 65f, lines(date(record.date), PdfTextStyle.BODY, 65f)),
            PdfColumn(left + 77f, 246f, operation),
            PdfColumn(left + 339f, 80f, lines(mileage(record.mileage), PdfTextStyle.BODY, 80f), true),
            PdfColumn(left + 433f, 86f, lines(CurrencyPresentation.format(record.cost, presentation.currencySymbol), PdfTextStyle.BODY, 86f), true)
        ), PdfBlockKind.RECORD, top = 9f, bottom = 9f)
    }

    private fun draw(canvas: Canvas, placement: PdfPlacement) {
        val block = placement.block
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light }
        val top = placement.top
        if (block.kind == PdfBlockKind.METRICS) block.columns.forEach { column ->
            canvas.drawRoundRect(RectF(column.left - 12f, top, column.left + column.width + 12f, top + placement.height), 6f, 6f, background)
        }
        if (block.kind == PdfBlockKind.TABLE_HEADER) canvas.drawRect(left - 4f, top, right + 4f, top + placement.height, background)
        if (block.kind == PdfBlockKind.BRAND && !placement.continued) {
            background.color = teal
            canvas.drawRoundRect(RectF(left, top, left + 32f, top + 32f), 6f, 6f, background)
            ContextCompat.getDrawable(context, R.drawable.ic_car_manager_mark)?.mutate()?.apply {
                setBounds(left.toInt() + 3, top.toInt() + 3, left.toInt() + 29, top.toInt() + 29)
                draw(canvas)
            }
        }
        val extra = if (placement.continued && block.kind == PdfBlockKind.RECORD) {
            val paint = paints.getValue(PdfTextStyle.META)
            canvas.drawText(block.continuedLabel, left, top + block.paddingTop - paint.fontMetrics.ascent, paint)
            PdfReportGeometry.CONTINUED_ROW_LABEL_HEIGHT
        } else 0f
        block.columns.forEach { column ->
            for (index in placement.firstLine until placement.firstLine + placement.lineCount) {
                val line = column.lines.getOrNull(index) ?: continue
                val paint = paints.getValue(line.style)
                val y = top + block.paddingTop + extra + (index - placement.firstLine) * block.lineHeight +
                    if (block.kind == PdfBlockKind.BRAND) 9f else 0f
                val x = if (column.rightAligned) column.left + column.width - paint.measureText(line.text) else column.left
                canvas.drawText(line.text, x, y - paint.fontMetrics.ascent, paint)
            }
        }
        if (block.kind == PdfBlockKind.SECTION || block.kind == PdfBlockKind.RECORD) {
            val paint = Paint().apply { color = divider; strokeWidth = 0.6f }
            canvas.drawLine(left, top + placement.height - 1f, right, top + placement.height - 1f, paint)
        }
    }

    private fun continuationHeader(canvas: Canvas, name: List<String>) {
        canvas.drawText("Car Manager • Rapport du véhicule", left, 46f, paints.getValue(PdfTextStyle.BRAND))
        name.forEachIndexed { i, line -> canvas.drawText(line, left, 62f + i * 12f, paints.getValue(PdfTextStyle.META)) }
        canvas.drawLine(left, 80f, right, 80f, Paint().apply { color = divider; strokeWidth = 0.6f })
    }
    private fun footer(canvas: Canvas, generated: String, page: Int, count: Int) {
        canvas.drawLine(left, 794f, right, 794f, Paint().apply { color = divider; strokeWidth = 0.6f })
        val paint = paints.getValue(PdfTextStyle.FOOTER)
        canvas.drawText("Car Manager • Généré le $generated", left, 808f, paint)
        val number = "Page $page / $count"
        canvas.drawText(number, right - paint.measureText(number), 808f, paint)
    }
}
