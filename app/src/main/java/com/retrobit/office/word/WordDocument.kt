package com.retrobit.office.word

import android.graphics.Color

/**
 * Word Document Model - Mirip MS Word
 * Mendukung paragraf, run, style, list, table, image, header/footer
 */
data class WordDocument(
    var title: String = "Dokumen Tanpa Judul",
    var author: String = "RetroBit Office",
    var paragraphs: MutableList<Paragraph> = mutableListOf(),
    var pageSettings: PageSettings = PageSettings(),
    var headers: MutableList<String> = mutableListOf(),
    var footers: MutableList<String> = mutableListOf(),
    // Ink annotations (free draw layer)
    var inkStrokes: MutableList<com.retrobit.office.core.stylus.InkStroke> = mutableListOf()
) {
    fun addParagraph(text: String = "", style: ParagraphStyle = ParagraphStyle()): Paragraph {
        val p = Paragraph(text = text, style = style)
        paragraphs.add(p)
        return p
    }

    fun wordCount(): Int = paragraphs.sumOf { it.text.split("\\s+".toRegex()).count { w -> w.isNotBlank() } }
    fun charCount(): Int = paragraphs.sumOf { it.text.length }
}

data class PageSettings(
    var paperSize: PaperSize = PaperSize.A4,
    var orientation: Orientation = Orientation.PORTRAIT,
    var marginTopMm: Float = 25.4f,
    var marginBottomMm: Float = 25.4f,
    var marginLeftMm: Float = 25.4f,
    var marginRightMm: Float = 25.4f
)

enum class PaperSize(val widthMm: Float, val heightMm: Float) {
    A4(210f, 297f),
    A3(297f, 420f),
    LETTER(215.9f, 279.4f),
    LEGAL(215.9f, 355.6f),
    FOLIO(215.9f, 330.2f) // umum di Indonesia
}
enum class Orientation { PORTRAIT, LANDSCAPE }

data class Paragraph(
    var text: String = "",
    var style: ParagraphStyle = ParagraphStyle(),
    var runs: MutableList<TextRun> = mutableListOf(),
    var bullet: BulletStyle? = null,
    var indentLevel: Int = 0
) {
    init {
        if (runs.isEmpty() && text.isNotEmpty()) {
            runs.add(TextRun(text))
        }
    }
}

data class ParagraphStyle(
    var alignment: Alignment = Alignment.LEFT,
    var lineSpacing: Float = 1.15f,
    var spaceBeforePt: Float = 0f,
    var spaceAfterPt: Float = 8f,
    var indentLeftPt: Float = 0f,
    var indentRightPt: Float = 0f,
    var styleName: String = "Normal" // Normal, Heading 1, Heading 2, etc.
)

enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }
enum class BulletStyle { BULLET, NUMBER, NONE }

data class TextRun(
    var text: String,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strikethrough: Boolean = false,
    var subscript: Boolean = false,
    var superscript: Boolean = false,
    var fontName: String = "Calibri",
    var fontSizePt: Float = 11f,
    var color: Int = Color.BLACK,
    var highlightColor: Int? = null,
    var backgroundColor: Int? = null
)

// Table support
data class WordTable(
    var rows: Int,
    var cols: Int,
    var cells: MutableList<MutableList<TableCell>> = MutableList(rows) { MutableList(cols) { TableCell() } },
    var borderStyle: BorderStyle = BorderStyle.SINGLE,
    var widthPercent: Int = 100
)

data class TableCell(
    var text: String = "",
    var backgroundColor: Int? = null,
    var colSpan: Int = 1,
    var rowSpan: Int = 1,
    var runs: MutableList<TextRun> = mutableListOf()
)

enum class BorderStyle { NONE, SINGLE, DOUBLE, DASHED }
