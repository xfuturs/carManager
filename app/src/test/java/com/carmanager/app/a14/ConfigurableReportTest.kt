package com.carmanager.app.a14

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.util.*
import com.carmanager.app.features.dashboard.ReportDraft
import com.carmanager.app.features.documents.ReportSummary
import com.carmanager.app.features.documents.ordinaryDocumentCategories
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConfigurableReportTest {
    private fun selected(mask: Int) = ReportSection.entries.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.reversed().toSet()
    private fun block(text: String, kind: PdfBlockKind = PdfBlockKind.TEXT, lines: Int = 1) = PdfBlock(
        listOf(PdfColumn(38f, 246f, List(lines) { PdfTextLine("$text:$it", PdfTextStyle.BODY) })), 13f, 4f, 4f, 8f, kind)
    private fun sections(mask: Int, length: Int, empty: Boolean = false) = StructuredReportRows.selectedSections(selected(mask)) { section ->
        PdfSection(block(section.label, PdfBlockKind.SECTION), if (empty) null else block("Colonnes", PdfBlockKind.TABLE_HEADER),
            if (empty) listOf(block("Aucun enregistrement : ${section.label}")) else listOf(block(section.name, PdfBlockKind.RECORD, length), block("Suivant", PdfBlockKind.RECORD)))
    }
    @ParameterizedTest @ValueSource(ints = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15])
    fun `every nonempty subset contains exactly selected sections in deterministic order`(mask: Int) {
        val built = mutableListOf<ReportSection>()
        val selected = selected(mask)
        val result = StructuredReportRows.selectedSections(selected) { built += it; it.label }
        assertEquals(ReportSection.entries.filter { it in selected }, built)
        assertEquals(built.map { it.label }, result)
        assertEquals(Integer.bitCount(mask), result.size)
    }
    private fun checkPlan(plan: List<PdfPlacement>) {
        val pages = plan.map { it.page }.distinct()
        assertEquals((1..pages.last()).toList(), pages)
        assertTrue(plan.all { it.lineCount > 0 && it.top + it.height <= PdfReportGeometry.BODY_BOTTOM })
        plan.groupBy { it.page }.values.forEach { placements ->
            placements.zipWithNext().forEach { (a,b) -> assertTrue(a.top + a.height <= b.top) }
            placements.forEachIndexed { i, item -> if (item.block.kind == PdfBlockKind.SECTION) {
                assertTrue(i < placements.lastIndex, "No orphan section")
                if (placements[i + 1].block.kind == PdfBlockKind.TABLE_HEADER) assertTrue(i + 2 <= placements.lastIndex)
            } }
        }
    }
    @ParameterizedTest @ValueSource(ints = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15])
    fun `empty selected histories are compact without unselected or blank pages`(mask: Int) {
        val plan = PdfPagePlanner().plan(listOf(block("Car Manager")), sections(mask, 1, true))
        checkPlan(plan); assertEquals(1, plan.last().page)
        assertEquals(Integer.bitCount(mask), plan.count { it.block.kind == PdfBlockKind.SECTION })
        assertEquals(0, plan.count { it.block.kind == PdfBlockKind.TABLE_HEADER })
    }
    @ParameterizedTest @ValueSource(ints = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15])
    fun `mixed sections and extreme multiline rows retain all lines and repeated headers`(mask: Int) {
        val sections = sections(mask, 240)
        val plan = PdfPagePlanner().plan(listOf(block("Car Manager")), sections)
        checkPlan(plan)
        sections.flatMap { it.rows }.forEach { row ->
            val pieces = plan.filter { it.block === row }
            assertEquals((0 until row.lineCount).toList(), pieces.flatMap { (it.firstLine until it.firstLine + it.lineCount).toList() })
            pieces.filter { it.continued }.forEach { piece ->
                val index = plan.indexOf(piece)
                assertEquals(PdfBlockKind.TABLE_HEADER, plan[index - 1].block.kind)
                assertEquals(PdfBlockKind.SECTION, plan[index - 2].block.kind)
            }
        }
        assertTrue(plan.last().page > 4)
    }
    @Test fun `new configuration always resets all four after cancellation or partial previous selection`() {
        val draft = ReportDraft(7, ReportSummary(2, 123))
        assertEquals(ReportSection.entries.toSet(), draft.selected)
        val none = ReportSection.entries.fold(draft) { current, section -> current.toggle(section) }
        assertFalse(none.canGenerate); assertTrue(none.toggle(ReportSection.MILEAGE_HISTORY).canGenerate)
        assertEquals(4, ReportDraft(7, draft.previous).selected.size)
    }
    @Test fun `zero selected sections are rejected before layout`() {
        assertThrows(IllegalArgumentException::class.java) { StructuredReportRows.selectedOrder(emptySet()) }
    }
    @Test fun `report summary uses REPORTS only and maximum date regardless of order`() {
        fun doc(id: Long, category: DocumentCategory, date: Long) = Document(id, 7, "Doc", category, "/$id", date)
        assertEquals(ReportSummary(2, 300), ReportSummary.from(listOf(doc(1, DocumentCategory.REPORTS, 300), doc(2, DocumentCategory.PHOTOS, 900), doc(3, DocumentCategory.REPORTS, 100))))
        assertEquals(ReportSummary(0, null), ReportSummary.from(emptyList()))
        assertEquals(DocumentCategory.entries.filterNot { it == DocumentCategory.REPORTS }, ordinaryDocumentCategories)
    }
    @Test fun `fuel and charging format only stored quantities prices notes and mileage in French`() {
        val rows = StructuredReportRows.fuel(listOf(FuelRecord(1,7,0,1200,12.5,25.2,"Ligne 1\nLigne 2"), FuelRecord(2,7,0,1300,34.7,12.8,isElectric = true)))
        assertTrue(rows[0].description.contains("Plein • 12,50 L")); assertTrue(rows[0].description.endsWith("Ligne 1\nLigne 2"))
        assertTrue(rows[1].description.contains("Recharge • 34,70 kWh")); assertEquals("25,20 €", rows[0].cost)
        assertTrue(rows.all { it.mileage.endsWith(" km") && it.date.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) })
    }
    @Test fun `mileage uses existing histories and all actual source labels without recomputation`() {
        val rows = StructuredReportRows.mileage(MileageSource.entries.map { MileageRecord(1,7,0,1200,it) })
        assertEquals(listOf("Relevé manuel", "Plein ou recharge", "Intervention"), rows.map { it.description })
        assertTrue(rows.all { it.cost == null }); assertEquals(1, rows.map { it.mileage }.distinct().size)
    }
    @Test fun `long charging note wraps without losing accented text or exceeding width`() {
        val note = "Révision électrique 🚗\n".repeat(300)
        val row = StructuredReportRows.fuel(listOf(FuelRecord(1,7,0,1,1.0,2.0,note,isElectric = true))).single()
        val lines = PdfTextWrapping.wrap(row.description, 246f) { it.codePointCount(0,it.length) * 5f }
        assertTrue(lines.size > 300); assertTrue(lines.all { it.codePointCount(0,it.length) * 5f <= 246f })
        assertEquals(row.description.replace(Regex("\\s"), ""), lines.joinToString("").replace(Regex("\\s"), ""))
    }
}
