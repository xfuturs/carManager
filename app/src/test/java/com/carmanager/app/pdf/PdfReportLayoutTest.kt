package com.carmanager.app.pdf

import com.carmanager.app.core.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PdfReportLayoutTest {
    private val measure: (String) -> Float = { it.codePointCount(0, it.length) * 5f }
    private fun block(id: String, count: Int = 1, kind: PdfBlockKind = PdfBlockKind.TEXT,
                      padding: Float = 0f, gap: Float = 0f) = PdfBlock(listOf(PdfColumn(0f, 100f,
        List(count) { PdfTextLine("$id:$it", PdfTextStyle.BODY) })), 10f, padding, padding, gap, kind)
    private fun planner() = PdfPagePlanner(0f, 20f, 100f)
    private fun section(rows: List<PdfBlock>) = PdfSection(block("heading", kind = PdfBlockKind.SECTION),
        block("columns", kind = PdfBlockKind.TABLE_HEADER), rows)

    @Test fun blockFitsWithoutPageBreak() {
        val plan = planner().plan(listOf(block("a", 6)), emptyList())
        assertEquals(1, plan.single().page); assertEquals(0f, plan.single().top)
    }
    @Test fun exactBoundaryFitsWithoutPageBreak() {
        val plan = planner().plan(listOf(block("a", 8)), emptyList())
        assertEquals(1, plan.single().page); assertEquals(80f, plan.single().height)
    }
    @Test fun blockExceedingRemainingSpaceMovesWholeToNextPage() {
        val plan = planner().plan(listOf(block("a", 7), block("b", 5)), emptyList())
        assertEquals(listOf(1, 2), plan.map { it.page }); assertEquals(5, plan.last().lineCount)
    }
    @Test fun headingAndFirstRowMoveTogetherRatherThanOrphan() {
        val plan = planner().plan(listOf(block("intro", 7)), listOf(section(listOf(block("first", 3)))))
        assertEquals(2, plan[1].page); assertEquals(2, plan[2].page); assertEquals(2, plan[3].page)
    }
    @Test fun emptySectionStaysCompactAndHasMeaningfulLine() {
        val empty = PdfSection(block("heading", kind = PdfBlockKind.SECTION), null, listOf(block("Aucun entretien enregistré.")))
        val plan = planner().plan(emptyList(), listOf(empty))
        assertEquals(2, plan.size); assertEquals(20f, plan.sumOf { it.height.toDouble() }.toFloat())
    }
    @Test fun sectionWithoutEmptyMessageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { planner().plan(emptyList(), listOf(section(emptyList()))) }
    }
    @Test fun wrappedContentProducesTallerRow() {
        val short = PdfTextWrapping.wrap("Vidange", 50f, measure)
        val long = PdfTextWrapping.wrap("Révision complète du véhicule avec contrôle de sécurité", 50f, measure)
        fun measured(lines: List<String>) = PdfBlock(listOf(PdfColumn(0f, 50f, lines.map { PdfTextLine(it, PdfTextStyle.BODY) })), 12f, 4f, 4f)
        assertTrue(measured(long).height > measured(short).height)
    }
    @Test fun longNotePushesNextRowDownWithoutOverlap() {
        val plan = PdfPagePlanner(0f, 0f, 500f).plan(listOf(block("note", 20, padding = 4f), block("next")), emptyList())
        assertTrue(plan[1].top >= plan[0].top + plan[0].height)
    }
    @Test fun continuationPageRepeatsSectionAndTableHeader() {
        val plan = planner().plan(emptyList(), listOf(section(List(5) { block("row$it", 3, PdfBlockKind.RECORD) })))
        for (page in plan.map { it.page }.distinct().drop(1)) {
            val onPage = plan.filter { it.page == page }
            assertEquals(PdfBlockKind.SECTION, onPage[0].block.kind)
            assertEquals(PdfBlockKind.TABLE_HEADER, onPage[1].block.kind)
            assertEquals(PdfBlockKind.RECORD, onPage[2].block.kind)
        }
    }
    @Test fun footerZoneIsNeverUsedByContent() {
        val plan = PdfPagePlanner().plan(List(120) { block("row$it", 3, padding = 5f) }, emptyList())
        assertTrue(plan.all { it.top + it.height <= PdfReportGeometry.BODY_BOTTOM })
        assertTrue(PdfReportGeometry.BODY_BOTTOM < PdfReportGeometry.PAGE_HEIGHT - 32f)
    }
    @Test fun pageNumbersIncrementWithoutBlankPages() {
        val plan = planner().plan(List(10) { block("row$it", 6) }, emptyList())
        assertEquals((1..10).toList(), plan.map { it.page })
    }
    @Test fun noNegativeRemainingHeightEvenWhenGapWouldCrossBoundary() {
        val plan = planner().plan(listOf(block("a", 7, gap = 100f), block("b")), emptyList())
        assertTrue(plan.all { 100f - it.top - it.height >= 0f })
        assertEquals(2, plan.last().page)
    }
    @Test fun largeRecordSetCreatesManyPlansInExactInputOrder() {
        val rows = List(1_000) { block("record$it", 2, PdfBlockKind.RECORD) }
        val plan = PdfPagePlanner().plan(emptyList(), listOf(section(rows)))
        assertTrue(plan.last().page > 20)
        assertEquals(rows, plan.filter { it.block.kind == PdfBlockKind.RECORD }.map { it.block })
    }
    @Test fun mixedShortLongRowsStayOrderedAndAllLinesArePreserved() {
        val rows = listOf(block("a", 1, PdfBlockKind.RECORD), block("b", 100, PdfBlockKind.RECORD), block("c", 4, PdfBlockKind.RECORD))
        val plan = planner().plan(emptyList(), listOf(section(rows)))
        val pieces = plan.filter { it.block.kind == PdfBlockKind.RECORD }
        assertEquals(rows, pieces.map { it.block }.distinct())
        rows.forEach { row -> assertEquals((0 until row.lineCount).toList(), pieces.filter { it.block === row }.flatMap { (it.firstLine until it.firstLine + it.lineCount).toList() }) }
    }
    @Test fun exceptionallyTallRecordSplitsOnlyBetweenLinesAndReservesContinuationLabel() {
        val row = block("note", 100, PdfBlockKind.RECORD, padding = 3f)
        val plan = planner().plan(emptyList(), listOf(section(listOf(row))))
        val pieces = plan.filter { it.block === row }
        assertTrue(pieces.size > 2); assertTrue(pieces.drop(1).all { it.continued })
        assertTrue(plan.all { it.top + it.height <= 100f })
        assertEquals(100, pieces.sumOf { it.lineCount })
        assertEquals(row.paddingTop + row.paddingBottom + pieces[1].lineCount * row.lineHeight +
            PdfReportGeometry.CONTINUED_ROW_LABEL_HEIGHT, pieces[1].height)
    }
    @Test fun multiColumnHeightUsesTallestCell() {
        val row = PdfBlock(listOf(PdfColumn(0f, 50f, listOf(PdfTextLine("date", PdfTextStyle.BODY))),
            PdfColumn(60f, 100f, List(4) { PdfTextLine("note$it", PdfTextStyle.META) })), 13f, 5f, 5f)
        assertEquals(4, row.lineCount); assertEquals(62f, row.height)
    }
    @Test fun invalidGeometryAndImpossibleLineFailRatherThanClip() {
        assertThrows(IllegalArgumentException::class.java) { PdfPagePlanner(0f, 100f, 90f) }
        assertThrows(IllegalArgumentException::class.java) { planner().plan(listOf(block("huge", padding = 100f)), emptyList()) }
    }
    @Test fun wrapUsesMeasuredWidthRatherThanCharacterLimit() {
        val variable: (String) -> Float = { s -> s.sumOf { if (it == 'W') 10 else 2 }.toFloat() }
        assertEquals(listOf("WW", "WW"), PdfTextWrapping.wrap("WWWW", 20f, variable))
        assertEquals(listOf("iiiiiiiiii"), PdfTextWrapping.wrap("iiiiiiiiii", 20f, variable))
    }
    @Test fun accentsAndWordsArePreservedWhileWrapping() {
        val input = "Révision complète échéance véhicule électrique"
        val result = PdfTextWrapping.wrap(input, 65f, measure)
        assertTrue(result.all { measure(it) <= 65f })
        assertEquals(input.replace(" ", ""), result.joinToString("").replace(" ", ""))
    }
    @Test fun unbrokenPlateOrLongModelCanWrapWithoutLosingCharacters() {
        val input = "ABCDEFGHIJK0123456789".repeat(8)
        val result = PdfTextWrapping.wrap(input, 35f, measure)
        assertEquals(input, result.joinToString("")); assertTrue(result.all { measure(it) <= 35f })
    }
    @Test fun longNotePreservesExplicitParagraphBreaks() {
        assertEquals(listOf("Une note", "", "Deuxième", "ligne"), PdfTextWrapping.wrap("Une note\r\n\r\nDeuxième ligne", 40f, measure))
    }
    @Test fun surrogatePairsAreNotSplitInsideAGlyph() {
        val result = PdfTextWrapping.wrap("🚗🚗🚗🚗🚗", 10f, measure)
        assertEquals(listOf("🚗🚗", "🚗🚗", "🚗"), result)
    }
    @Test fun emptyTextAndImpossibleGlyphAreHandledDeterministically() {
        assertEquals(listOf(""), PdfTextWrapping.wrap("  ", 10f, measure))
        assertThrows(IllegalArgumentException::class.java) { PdfTextWrapping.wrap("W", 1f, measure) }
    }
    @Test fun compactContinuationIdentityHasExplicitEllipsisButBodyRemainsComplete() {
        val full = PdfTextWrapping.wrap("Véhicule avec un modèle particulièrement long", 40f, measure)
        val copy = full.toList(); val compact = PdfTextWrapping.compactHeader(full, 40f, measure)
        assertEquals(2, compact.size); assertTrue(compact.last().endsWith("…"))
        assertTrue(compact.all { measure(it) <= 40f }); assertEquals(copy, full)
    }
}
