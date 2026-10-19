package com.retrobit.office.excel

import android.graphics.Color
import kotlin.math.max

/**
 * Excel Spreadsheet Model - Mirip MS Excel
 * Mendukung cell, formula, style, merge, chart, filter
 */
data class SpreadsheetModel(
    var sheetName: String = "Sheet1",
    var sheets: MutableList<Sheet> = mutableListOf(Sheet()),
    var activeSheetIndex: Int = 0
) {
    val activeSheet: Sheet get() = sheets[activeSheetIndex]
}

data class Sheet(
    var name: String = "Sheet1",
    var rows: MutableList<Row> = MutableList(100) { Row(it) }, // 100 rows default
    var cols: MutableList<Column> = MutableList(20) { Column(it) }, // 20 cols (A-T)
    var mergedCells: MutableList<CellRange> = mutableListOf(),
    var frozenRow: Int = -1,
    var frozenCol: Int = -1,
    var filterEnabled: Boolean = false
) {
    fun getCell(row: Int, col: Int): Cell {
        ensureSize(row, col)
        return rows[row].cells[col]
    }

    fun setCell(row: Int, col: Int, value: String, style: CellStyle? = null) {
        ensureSize(row, col)
        rows[row].cells[col].apply {
            this.value = value
            this.displayValue = value
            this.formula = null
            if (style != null) this.style = style
        }
    }

    /** Hitung ulang seluruh formula setelah data berubah. */
    fun recalculate() {
        rows.forEach { r -> r.cells.forEach { c ->
            c.formula?.let { c.displayValue = evaluateFormula(it) }
        } }
    }

    fun insertRow(at: Int, addCells: Boolean = true) {
        val idx = at.coerceIn(0, rows.size)
        rows.add(idx, Row(idx))
        if (addCells) rows[idx].cells = MutableList(cols.size.coerceAtLeast(20)) { Cell() }
        reindex()
    }

    fun deleteRow(at: Int) {
        if (rows.size <= 1 || at !in rows.indices) return
        rows.removeAt(at)
        reindex()
    }

    fun insertColumn(at: Int) {
        val idx = at.coerceIn(0, cols.size)
        rows.forEach { r ->
            while (r.cells.size < cols.size) r.cells.add(Cell())
            r.cells.add(idx, Cell())
        }
        cols.add(idx, Column(idx, widthDp = 96f))
        reindex()
    }

    fun deleteColumn(at: Int) {
        if (cols.size <= 1 || at !in cols.indices) return
        cols.removeAt(at)
        rows.forEach { r -> if (at < r.cells.size) r.cells.removeAt(at) }
        reindex()
    }

    private fun reindex() {
        rows.forEachIndexed { i, r -> r.index = i }
        cols.forEachIndexed { i, c -> c.index = i }
    }

    /** Urutkan baris data (header baris 0 dipertahankan) berdasarkan kolom. */
    fun sortByColumn(col: Int, ascending: Boolean) {
        if (rows.size <= 1) return
        val header = rows[0]
        val data = rows.drop(1).sortedWith(compareBy<Row> { row ->
            val raw = row.cells.getOrNull(col)?.let { it.displayValue.ifEmpty { it.value } } ?: ""
            raw.replace(".", "").replace(",", ".").toDoubleOrNull()
        }.thenBy { row ->
            row.cells.getOrNull(col)?.let { it.displayValue.ifEmpty { it.value } }?.lowercase() ?: ""
        })
        val ordered = if (ascending) data else data.reversed()
        rows = (listOf(header) + ordered).toMutableList()
        reindex()
        recalculate()
    }

    /** Salinan dalam (untuk undo/redo). */
    fun deepCopy(): Sheet = copy(
        rows = rows.map { r -> r.copy(cells = r.cells.map { c ->
            c.copy(style = c.style.copy(border = c.style.border.copy()))
        }.toMutableList()) }.toMutableList(),
        cols = cols.map { it.copy() }.toMutableList(),
        mergedCells = mergedCells.toMutableList()
    )

    fun setFormula(row: Int, col: Int, formula: String) {
        ensureSize(row, col)
        rows[row].cells[col].apply {
            this.formula = formula
            this.displayValue = evaluateFormula(formula)
        }
    }

    private fun ensureSize(row: Int, col: Int) {
        while (rows.size <= row) rows.add(Row(rows.size))
        while (cols.size <= col) cols.add(Column(cols.size))
        rows.forEach { r ->
            while (r.cells.size <= max(cols.size, col + 1)) r.cells.add(Cell())
        }
    }

    private fun evaluateFormula(formula: String): String {
        // Simple formula evaluator: =SUM(A1:A5), =AVERAGE, =IF, etc.
        return try {
            FormulaEngine.evaluate(this, formula)
        } catch (e: Exception) {
            "#ERROR"
        }
    }

    fun getColumnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }
}

data class Row(
    var index: Int,
    var cells: MutableList<Cell> = MutableList(20) { Cell() },
    var heightDp: Float = 28f,
    var hidden: Boolean = false
)

data class Column(
    var index: Int,
    var widthDp: Float = 96f,
    var hidden: Boolean = false
)

data class Cell(
    var value: String = "",
    var displayValue: String = "",
    var formula: String? = null,
    var style: CellStyle = CellStyle(),
    var comment: String? = null
) {
    val isFormula: Boolean get() = formula != null
    val isEmpty: Boolean get() = value.isBlank() && formula.isNullOrBlank()
}

data class CellStyle(
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var fontName: String = "Calibri",
    var fontSizePt: Float = 11f,
    var fontColor: Int = Color.BLACK,
    var bgColor: Int = Color.WHITE,
    var alignment: CellAlignment = CellAlignment.LEFT,
    var verticalAlignment: VerticalAlignment = VerticalAlignment.CENTER,
    var numberFormat: NumberFormat = NumberFormat.GENERAL,
    var decimals: Int = 0,
    var border: CellBorder = CellBorder(),
    var wrapText: Boolean = false
)

enum class CellAlignment { LEFT, CENTER, RIGHT, FILL }
enum class VerticalAlignment { TOP, CENTER, BOTTOM }
enum class NumberFormat {
    GENERAL, NUMBER, CURRENCY_IDR, CURRENCY_USD, PERCENT, DATE, TIME, ACCOUNTING
}

data class CellBorder(
    var top: Boolean = false,
    var bottom: Boolean = false,
    var left: Boolean = false,
    var right: Boolean = false,
    var color: Int = Color.parseColor("#BDBDBD"),
    var style: BorderStyle = BorderStyle.THIN
)
enum class BorderStyle { NONE, THIN, MEDIUM, THICK, DOUBLE }

data class CellRange(
    var startRow: Int, var startCol: Int,
    var endRow: Int, var endCol: Int
) {
    fun contains(r: Int, c: Int): Boolean = r in startRow..endRow && c in startCol..endCol
}

// Simple formula engine
object FormulaEngine {
    fun evaluate(sheet: Sheet, formula: String): String {
        var f = formula.trim()
        if (!f.startsWith("=")) return f
        f = f.substring(1).uppercase()

        // SUM
        if (f.startsWith("SUM(")) {
            val range = f.substring(4, f.length - 1)
            return sumRange(sheet, range).toString()
        }
        if (f.startsWith("AVERAGE(")) {
            val range = f.substring(8, f.length - 1)
            val vals = collectRange(sheet, range)
            return if (vals.isNotEmpty()) (vals.sum() / vals.size).toString() else "0"
        }
        if (f.startsWith("MAX(")) {
            val vals = collectRange(sheet, f.substring(4, f.length - 1))
            return (vals.maxOrNull() ?: 0.0).toString()
        }
        if (f.startsWith("MIN(")) {
            val vals = collectRange(sheet, f.substring(4, f.length - 1))
            return (vals.minOrNull() ?: 0.0).toString()
        }
        if (f.startsWith("COUNT(")) {
            val vals = collectRange(sheet, f.substring(6, f.length - 1))
            return vals.size.toString()
        }
        // Basic arithmetic: =A1+B2 etc - simplified
        // If not recognized, try evaluate as expression with cell refs
        return evaluateArithmetic(sheet, f)
    }

    private fun sumRange(sheet: Sheet, range: String): Double {
        return collectRange(sheet, range).sum()
    }

    private fun collectRange(sheet: Sheet, range: String): List<Double> {
        val list = mutableListOf<Double>()
        // Support A1:A5 or A1,B2 etc
        val parts = range.split(",")
        parts.forEach { part ->
            if (part.contains(":")) {
                val (start, end) = part.split(":")
                val (sr, sc) = parseCellRef(start)
                val (er, ec) = parseCellRef(end)
                for (r in minOf(sr, er)..maxOf(sr, er)) {
                    for (c in minOf(sc, ec)..maxOf(sc, ec)) {
                        sheet.getCell(r, c).displayValue.toDoubleOrNull()?.let { list.add(it) }
                    }
                }
            } else {
                val (r, c) = parseCellRef(part)
                sheet.getCell(r, c).displayValue.toDoubleOrNull()?.let { list.add(it) }
            }
        }
        return list
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colPart = ref.filter { it.isLetter() }
        val rowPart = ref.filter { it.isDigit() }
        val col = colPart.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        val row = (rowPart.toIntOrNull() ?: 1) - 1
        return row to col.coerceAtLeast(0)
    }

    private fun evaluateArithmetic(sheet: Sheet, expr: String): String {
        // Replace cell refs with values
        var e = expr
        val regex = Regex("[A-Z]+[0-9]+")
        regex.findAll(expr).forEach { m ->
            val (r, c) = parseCellRef(m.value)
            val v = sheet.getCell(r, c).displayValue.toDoubleOrNull()?.toString() ?: "0"
            e = e.replace(m.value, v)
        }
        // Simple eval with + - * / ()
        return try {
            // Use built-in evaluator (simple)
            evalSimple(e).toString()
        } catch (_: Exception) { "#VALUE!" }
    }

    private fun evalSimple(expr: String): Double {
        // Shunting-yard or JS engine fallback - simple implementation
        // For demo, handle + - * /
        return object : Any() {
            var pos = -1
            var ch = 0
            fun nextChar() { ch = if (++pos < expr.length) expr[pos].code else -1 }
            fun eat(c: Int): Boolean { while (ch == ' '.code) nextChar(); if (ch == c) { nextChar(); return true }; return false }
            fun parse(): Double { nextChar(); val x = parseExpression(); return x }
            fun parseExpression(): Double { var x = parseTerm(); while (true) { when { eat('+'.code) -> x += parseTerm(); eat('-'.code) -> x -= parseTerm(); else -> return x } } }
            fun parseTerm(): Double { var x = parseFactor(); while (true) { when { eat('*'.code) -> x *= parseFactor(); eat('/'.code) -> x /= parseFactor(); else -> return x } } }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val start = pos
                if (eat('('.code)) { x = parseExpression(); eat(')'.code) } else if (ch in '0'.code..'9'.code || ch == '.'.code) { while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar(); x = expr.substring(start, pos).toDouble() } else throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }
        }.parse()
    }
}

/** Angka gaya Indonesia: ribuan titik, desimal koma. */
private fun idNumber(num: Double, decimals: Int): String {
    val us = String.format(java.util.Locale.US, "%,.${decimals}f", num)
    return us.replace(",", "\u0001").replace(".", ",").replace("\u0001", ".")
}

/** Tampilan angka sesuai format sel (Rp, %, desimal). */
fun Cell.formattedValue(): String {
    if (style.numberFormat == NumberFormat.GENERAL || value.isBlank()) {
        return displayValue.ifEmpty { value }
    }
    val raw = value.replace("Rp", "").replace("%", "").trim()
        .replace(".", "").replace(",", ".")
    val num = raw.toDoubleOrNull() ?: return displayValue.ifEmpty { value }
    val dec = style.decimals.coerceIn(0, 6)
    return when (style.numberFormat) {
        NumberFormat.CURRENCY_IDR -> "Rp " + idNumber(num, dec)
        NumberFormat.CURRENCY_USD -> "$" + String.format(java.util.Locale.US, "%,.${dec}f", num)
        NumberFormat.PERCENT -> String.format(java.util.Locale.US, "%.${dec}f", num * 100) + "%"
        NumberFormat.NUMBER, NumberFormat.ACCOUNTING -> idNumber(num, dec)
        else -> displayValue.ifEmpty { value }
    }
}
