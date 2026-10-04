package com.carmanager.app.core.util

import kotlin.math.floor

/** Mise en page du rapport uniquement : mesures injectées, aucun Canvas ni accès aux données. */
internal object PdfTextWrapping {
    fun wrap(text: String, width: Float, measure: (String) -> Float): List<String> {
        require(width.isFinite() && width > 0)
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
            var line = ""
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) result += ""
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate) <= width) line = candidate
                else {
                    if (line.isNotEmpty()) { result += line; line = "" }
                    var rest = word
                    while (measure(rest) > width) {
                        var end = 0
                        var cursor = 0
                        while (cursor < rest.length) {
                            cursor += Character.charCount(rest.codePointAt(cursor))
                            if (measure(rest.substring(0, cursor)) > width) break
                            end = cursor
                        }
                        require(end > 0) { "Un glyphe dépasse la largeur de la colonne PDF" }
                        result += rest.substring(0, end)
                        rest = rest.substring(end)
                    }
                    line = rest
                }
            }
            if (line.isNotEmpty()) result += line
        }
        return result
    }

    /** Abbreviation explicite du seul en-tête de rappel ; le nom complet reste dans le corps. */
    fun compactHeader(lines: List<String>, width: Float, measure: (String) -> Float): List<String> {
        if (lines.size <= 2) return lines
        var last = lines[1]
        while (last.isNotEmpty() && measure("$last…") > width) {
            last = last.substring(0, last.offsetByCodePoints(last.length, -1))
        }
        return listOf(lines[0], "$last…")
    }
}

internal object PdfReportGeometry {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 38f
    const val FIRST_TOP = 36f
    const val CONTINUATION_TOP = 88f
    const val BODY_BOTTOM = 784f
    const val CONTINUED_ROW_LABEL_HEIGHT = 14f
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
}

internal enum class PdfTextStyle { BRAND, TITLE, VEHICLE, SECTION, BODY, BOLD, META, VALUE, FOOTER }
internal enum class PdfBlockKind { TEXT, BRAND, METRICS, SECTION, TABLE_HEADER, RECORD }
internal data class PdfTextLine(val text: String, val style: PdfTextStyle)
internal data class PdfColumn(val left: Float, val width: Float, val lines: List<PdfTextLine>, val rightAligned: Boolean = false)
internal data class PdfBlock(
    val columns: List<PdfColumn>,
    val lineHeight: Float,
    val paddingTop: Float = 0f,
    val paddingBottom: Float = 0f,
    val gapAfter: Float = 0f,
    val kind: PdfBlockKind = PdfBlockKind.TEXT,
    val continuedLabel: String = "Suite de l’intervention précédente"
) {
    init {
        require(columns.isNotEmpty() && lineHeight.isFinite() && lineHeight > 0)
        require(listOf(paddingTop, paddingBottom, gapAfter).all { it.isFinite() && it >= 0 })
    }
    val lineCount: Int get() = columns.maxOf { it.lines.size }.coerceAtLeast(1)
    val height: Float get() = paddingTop + lineCount * lineHeight + paddingBottom
    fun pieceHeight(lines: Int, continued: Boolean): Float = paddingTop + lines * lineHeight + paddingBottom +
        if (continued && kind == PdfBlockKind.RECORD) PdfReportGeometry.CONTINUED_ROW_LABEL_HEIGHT else 0f
}
internal data class PdfSection(val heading: PdfBlock, val tableHeader: PdfBlock?, val rows: List<PdfBlock>)
internal data class PdfPlacement(val page: Int, val top: Float, val block: PdfBlock, val firstLine: Int, val lineCount: Int) {
    val continued: Boolean get() = firstLine > 0
    val height: Float get() = block.pieceHeight(lineCount, continued)
}

/** Plan préalable ; une ligne n'est jamais coupée et aucune position n'entre dans la zone footer. */
internal class PdfPagePlanner(
    private val firstTop: Float = PdfReportGeometry.FIRST_TOP,
    private val continuationTop: Float = PdfReportGeometry.CONTINUATION_TOP,
    private val bottom: Float = PdfReportGeometry.BODY_BOTTOM
) {
    init { require(firstTop >= 0 && continuationTop >= 0 && bottom > maxOf(firstTop, continuationTop)) }

    fun plan(leading: List<PdfBlock>, sections: List<PdfSection>): List<PdfPlacement> {
        val placements = mutableListOf<PdfPlacement>()
        var page = 1
        var y = firstTop
        fun nextPage() { page++; y = continuationTop }
        fun place(block: PdfBlock, from: Int, count: Int) {
            val piece = PdfPlacement(page, y, block, from, count)
            check(piece.top + piece.height <= bottom + 0.01f)
            placements += piece
            y = (y + piece.height + if (from + count == block.lineCount) block.gapAfter else 0f).coerceAtMost(bottom)
        }
        fun prefix(section: PdfSection) {
            place(section.heading, 0, section.heading.lineCount)
            section.tableHeader?.let { place(it, 0, it.lineCount) }
        }
        fun flow(block: PdfBlock, section: PdfSection? = null) {
            val prefixHeight = section?.let { it.heading.height + it.heading.gapAfter +
                (it.tableHeader?.let { header -> header.height + header.gapAfter } ?: 0f) } ?: 0f
            val freshCapacity = bottom - continuationTop - prefixHeight
            require(block.pieceHeight(1, true) <= freshCapacity) { "Ligne trop haute pour une page PDF" }
            var from = 0
            while (from < block.lineCount) {
                val remainingLines = block.lineCount - from
                val remainingHeight = block.pieceHeight(remainingLines, from > 0)
                if (remainingHeight <= bottom - y) {
                    place(block, from, remainingLines)
                    break
                }
                // Déplacer toute la ligne/fiche si elle peut tenir entière sur une nouvelle page.
                if (remainingHeight <= freshCapacity ||
                    block.pieceHeight(1, from > 0) > bottom - y) {
                    nextPage(); section?.let { prefix(it) }
                    continue
                }
                val overhead = block.pieceHeight(0, from > 0)
                val count = floor((bottom - y - overhead) / block.lineHeight).toInt().coerceAtMost(remainingLines)
                check(count > 0)
                place(block, from, count)
                from += count
                nextPage(); section?.let { prefix(it) }
            }
        }
        leading.forEach { flow(it) }
        sections.forEach { section ->
            require(section.rows.isNotEmpty()) { "Une section vide nécessite sa ligne d'état vide" }
            val prefixHeight = section.heading.height + section.heading.gapAfter +
                (section.tableHeader?.let { it.height + it.gapAfter } ?: 0f)
            val first = section.rows.first()
            val capacity = bottom - continuationTop - prefixHeight
            val firstRequired = if (first.height <= capacity) first.height else first.pieceHeight(1, false)
            require(prefixHeight + firstRequired <= bottom - continuationTop)
            if (prefixHeight + firstRequired > bottom - y) nextPage()
            prefix(section)
            section.rows.forEach { flow(it, section) }
        }
        return placements
    }
}
