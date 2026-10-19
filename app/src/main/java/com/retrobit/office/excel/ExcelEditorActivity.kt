package com.retrobit.office.excel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.print.PrintHelper
import java.io.File

class ExcelEditorActivity : AppCompatActivity() {

    private lateinit var spreadsheetView: SpreadsheetView
    private lateinit var formulaBar: EditText
    private lateinit var cellRef: TextView
    private lateinit var model: SpreadsheetModel
    private var currentFile: File? = null

    private val undoStack = ArrayDeque<Sheet>()
    private val redoStack = ArrayDeque<Sheet>()

    private val exporter = XlsxExporter()

    companion object {
        private const val REQ_OPEN_XLSX = 61
    }
    private val printHelper by lazy { PrintHelper(this) }

    private val sheet: Sheet get() = model.activeSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excel_editor)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Excel - RetroBit Office"

        model = SpreadsheetModel()
        intent.getStringExtra("file_path")?.let { path ->
            val f = File(path)
            if (f.exists()) {
                try {
                    model = exporter.import(f)
                    currentFile = f
                    supportActionBar?.title = f.name
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal buka: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (sheet.getCell(0, 0).isEmpty) seedSample()

        spreadsheetView = findViewById(R.id.spreadsheetView)
        formulaBar = findViewById(R.id.formulaBar)
        cellRef = findViewById(R.id.cellRef)

        spreadsheetView.model = sheet

        spreadsheetView.onCellSelected = { row, col, cell ->
            cellRef.text = "${sheet.getColumnName(col)}${row + 1}"
            formulaBar.setText(cell.formula ?: cell.value)
        }
        spreadsheetView.onCellEdited = { _, _, value ->
            formulaBar.requestFocus()
            formulaBar.setText(value)
            formulaBar.setSelection(formulaBar.text.length)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(formulaBar, 0)
        }

        formulaBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitFormula()
                true
            } else false
        }
        findViewById<View>(R.id.btnCommitFormula)?.setOnClickListener { commitFormula() }

        setupToolbar()
    }

    private fun seedSample(targetSheet: Sheet? = null) {
        val target = targetSheet ?: sheet
        target.setCell(0, 0, "RetroBit Office", CellStyle(bold = true, bgColor = Color.parseColor("#1B1B18"), fontColor = Color.WHITE))
        target.setCell(0, 1, "Penjualan", CellStyle(bold = true, bgColor = Color.parseColor("#1B1B18"), fontColor = Color.WHITE))
        target.setCell(1, 0, "Januari")
        target.setCell(1, 1, "1500000")
        target.setCell(2, 0, "Februari")
        target.setCell(2, 1, "2300000")
        target.setCell(3, 0, "Total")
        target.setFormula(3, 1, "=SUM(B2:B3)")
    }

    private fun commitFormula() {
        val text = formulaBar.text.toString()
        val (row, col) = selectedPosition()
        pushUndo()
        if (text.startsWith("=")) sheet.setFormula(row, col, text)
        else sheet.setCell(row, col, text)
        sheet.recalculate()
        spreadsheetView.refresh()
        val cell = sheet.getCell(row, col)
        formulaBar.setText(cell.formula ?: cell.value)
        Toast.makeText(this, "${sheet.getColumnName(col)}${row + 1} diperbarui", Toast.LENGTH_SHORT).show()
    }

    private fun selectedPosition(): Pair<Int, Int> {
        val refText = cellRef.text.toString()
        val row = refText.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0
        val colName = refText.filter { it.isLetter() }
        val col = colName.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        return row.coerceAtLeast(0) to col.coerceAtLeast(0)
    }

    // ---------- Toolbar ----------

    private fun tool(id: String, icon: Int, label: String) =
        com.retrobit.office.ui.ToolItem(id, icon, label)

    private fun cat(id: String, icon: Int, label: String, tools: List<com.retrobit.office.ui.ToolItem>) =
        com.retrobit.office.ui.ToolCategory(id, icon, label, tools)

    private fun setupToolbar() {
        val sidebar = findViewById<com.retrobit.office.ui.ToolSidebar>(R.id.toolSidebar)
        sidebar.setCategories(listOf(
            cat("file", R.drawable.ic_pi_save, "BERKAS", listOf(
                tool("new", R.drawable.ic_pi_file_text, "Baru"),
                tool("open", R.drawable.ic_pi_folder_plus, "Buka"),
                tool("save", R.drawable.ic_pi_save, "Simpan"),
                tool("print", R.drawable.ic_pi_printer, "Cetak"),
                tool("share", R.drawable.ic_pi_share, "Bagikan")
            )),
            cat("edit", R.drawable.ic_pi_pencil, "EDIT", listOf(
                tool("undo", R.drawable.ic_pi_undo, "Undo"),
                tool("redo", R.drawable.ic_pi_redo, "Redo"),
                tool("cut", R.drawable.ic_pi_cut, "Potong"),
                tool("copy", R.drawable.ic_pi_copy, "Salin"),
                tool("paste", R.drawable.ic_pi_clipboard, "Tempel"),
                tool("clear", R.drawable.ic_pi_eraser, "Kosongkan")
            )),
            cat("font", R.drawable.ic_pi_text_start_t, "FONT", listOf(
                tool("bold", R.drawable.ic_pi_bold, "Tebal"),
                tool("italic", R.drawable.ic_pi_italic, "Miring"),
                tool("underline", R.drawable.ic_pi_underline, "Garis"),
                tool("text_color", R.drawable.ic_pi_colors_swatch, "Warna"),
                tool("cell_bg", R.drawable.ic_pi_brush, "Isi"),
                tool("size_up", R.drawable.ic_pi_type_size, "A+"),
                tool("size_down", R.drawable.ic_pi_type_size, "A−"),
                tool("clear_format", R.drawable.ic_pi_magic_edit, "Reset")
            )),
            cat("align", R.drawable.ic_pi_text_align_left, "RATA", listOf(
                tool("align_l", R.drawable.ic_pi_text_align_left, "Kiri"),
                tool("align_c", R.drawable.ic_pi_text_align_center, "Tengah"),
                tool("align_r", R.drawable.ic_pi_text_align_right, "Kanan"),
                tool("wrap", R.drawable.ic_pi_text_wrap, "Lipat")
            )),
            cat("number", R.drawable.ic_pi_money, "ANGKA", listOf(
                tool("currency", R.drawable.ic_pi_money, "Rupiah"),
                tool("usd", R.drawable.ic_pi_dollar, "Dollar"),
                tool("percent", R.drawable.ic_pi_percent, "%"),
                tool("number", R.drawable.ic_pi_numbers, "Angka"),
                tool("dec_inc", R.drawable.ic_pi_plus_box, "Des +"),
                tool("dec_dec", R.drawable.ic_pi_minus_box, "Des −")
            )),
            cat("cells", R.drawable.ic_pi_grid_3x3, "SEL", listOf(
                tool("row_above", R.drawable.ic_pi_insert_row, "Baris +"),
                tool("row_delete", R.drawable.ic_pi_trash, "Baris −"),
                tool("col_left", R.drawable.ic_pi_insert_col, "Kolom +"),
                tool("col_delete", R.drawable.ic_pi_trash, "Kolom −"),
                tool("border_all", R.drawable.ic_pi_grid_3x3, "Batas"),
                tool("border_none", R.drawable.ic_pi_grid_2x2_2, "Tanpa")
            )),
            cat("data", R.drawable.ic_pi_filter, "DATA", listOf(
                tool("sum", R.drawable.ic_pi_sigma, "Jumlah"),
                tool("average", R.drawable.ic_pi_calculator, "Rata2"),
                tool("sort_asc", R.drawable.ic_pi_arrow_up_box, "Sort ↑"),
                tool("sort_desc", R.drawable.ic_pi_arrow_down_box, "Sort ↓"),
                tool("filter", R.drawable.ic_pi_filter, "Filter"),
                tool("chart", R.drawable.ic_pi_chart_bar_big, "Grafik")
            )),
            cat("view", R.drawable.ic_pi_eye, "TAMPILAN", listOf(
                tool("zoom_in", R.drawable.ic_pi_zoom_in, "Zoom +"),
                tool("zoom_out", R.drawable.ic_pi_zoom_out, "Zoom −"),
                tool("gridlines", R.drawable.ic_pi_grid_2x3, "Grid")
            ))
        ))
        sidebar.onToolSelected = { _, toolId -> handleTool(toolId) }
    }

    private fun handleTool(toolId: String) {
        when (toolId) {
            "new" -> newSpreadsheet()
            "open" -> pickXlsx()
            "save" -> saveFile()
            "print" -> printSheet()
            "share" -> shareFile()
            "undo" -> undo()
            "redo" -> redo()
            "cut" -> cutCell()
            "copy" -> copyCell()
            "paste" -> pasteCell()
            "clear" -> {
                val (r, c) = selectedPosition()
                pushUndo()
                sheet.setCell(r, c, "")
                spreadsheetView.refresh()
                refreshFormulaBar()
            }
            "bold" -> style { it.copy(bold = !it.bold) }
            "italic" -> style { it.copy(italic = !it.italic) }
            "underline" -> style { it.copy(underline = !it.underline) }
            "text_color" -> showColorPicker(false)
            "cell_bg" -> showColorPicker(true)
            "size_up" -> style { it.copy(fontSizePt = (it.fontSizePt + 1f).coerceAtMost(72f)) }
            "size_down" -> style { it.copy(fontSizePt = (it.fontSizePt - 1f).coerceAtLeast(6f)) }
            "clear_format" -> style { CellStyle() }
            "align_l" -> style { it.copy(alignment = CellAlignment.LEFT) }
            "align_c" -> style { it.copy(alignment = CellAlignment.CENTER) }
            "align_r" -> style { it.copy(alignment = CellAlignment.RIGHT) }
            "wrap" -> style { it.copy(wrapText = !it.wrapText) }
            "currency" -> style { it.copy(numberFormat = NumberFormat.CURRENCY_IDR, decimals = 0) }
            "usd" -> style { it.copy(numberFormat = NumberFormat.CURRENCY_USD, decimals = 2) }
            "percent" -> style { it.copy(numberFormat = NumberFormat.PERCENT, decimals = 0) }
            "number" -> style { it.copy(numberFormat = NumberFormat.NUMBER, decimals = 2) }
            "dec_inc" -> style {
                val fmt = if (it.numberFormat == NumberFormat.GENERAL) NumberFormat.NUMBER else it.numberFormat
                it.copy(numberFormat = fmt, decimals = (it.decimals + 1).coerceAtMost(6))
            }
            "dec_dec" -> style {
                val fmt = if (it.numberFormat == NumberFormat.GENERAL) NumberFormat.NUMBER else it.numberFormat
                it.copy(numberFormat = fmt, decimals = (it.decimals - 1).coerceAtLeast(0))
            }
            "row_above" -> insertRow()
            "row_delete" -> deleteRow()
            "col_left" -> insertColumn()
            "col_delete" -> deleteColumn()
            "border_all" -> style {
                it.copy(border = CellBorder(top = true, bottom = true, left = true, right = true))
            }
            "border_none" -> style { it.copy(border = CellBorder()) }
            "sum" -> insertAggregate("SUM")
            "average" -> insertAggregate("AVERAGE")
            "sort_asc" -> sort(true)
            "sort_desc" -> sort(false)
            "filter" -> showFilterDialog()
            "chart" -> showChartDialog()
            "zoom_in" -> spreadsheetView.setZoom(spreadsheetView.getZoom() * 1.15f)
            "zoom_out" -> spreadsheetView.setZoom(spreadsheetView.getZoom() / 1.15f)
            "gridlines" -> {
                spreadsheetView.showGridlines = !spreadsheetView.showGridlines
                spreadsheetView.invalidate()
                Toast.makeText(this, if (spreadsheetView.showGridlines) "Grid: tampil" else "Grid: sembunyi",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun style(transform: (CellStyle) -> CellStyle) {
        pushUndo()
        spreadsheetView.applyStyleToSelection(transform)
        refreshFormulaBar()
    }

    private fun refreshFormulaBar() {
        val (r, c) = selectedPosition()
        val cell = sheet.getCell(r, c)
        formulaBar.setText(cell.formula ?: cell.value)
        sheet.recalculate()
        spreadsheetView.refresh()
    }

    // ---------- Row / column ----------

    private fun insertRow() {
        val (r, _) = selectedPosition()
        pushUndo()
        sheet.insertRow(r)
        sheet.recalculate()
        spreadsheetView.refresh()
        refreshFormulaBar()
        Toast.makeText(this, "Baris disisipkan di atas baris ${r + 1}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteRow() {
        val (r, _) = selectedPosition()
        pushUndo()
        sheet.deleteRow(r)
        sheet.recalculate()
        spreadsheetView.refresh()
        spreadsheetView.selectCell(r.coerceAtMost(sheet.rows.size - 1), selectedPosition().second)
        Toast.makeText(this, "Baris ${r + 1} dihapus", Toast.LENGTH_SHORT).show()
    }

    private fun insertColumn() {
        val (_, c) = selectedPosition()
        pushUndo()
        sheet.insertColumn(c)
        sheet.recalculate()
        spreadsheetView.refresh()
        refreshFormulaBar()
        Toast.makeText(this, "Kolom disisipkan di kiri kolom ${sheet.getColumnName(c)}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteColumn() {
        val (_, c) = selectedPosition()
        pushUndo()
        sheet.deleteColumn(c)
        spreadsheetView.refresh()
        spreadsheetView.selectCell(selectedPosition().first, c.coerceAtMost(sheet.cols.size - 1))
        Toast.makeText(this, "Kolom dihapus", Toast.LENGTH_SHORT).show()
    }

    // ---------- Clipboard ----------

    private fun clipboard(): ClipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun copyCell() {
        val (r, c) = selectedPosition()
        val cell = sheet.getCell(r, c)
        clipboard().setPrimaryClip(ClipData.newPlainText("RetroBit", cell.formula ?: cell.value))
        Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
    }

    private fun cutCell() {
        copyCell()
        val (r, c) = selectedPosition()
        pushUndo()
        sheet.setCell(r, c, "")
        spreadsheetView.refresh()
        refreshFormulaBar()
    }

    private fun pasteCell() {
        val text = clipboard().primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val (r, c) = selectedPosition()
        pushUndo()
        if (text.startsWith("=")) sheet.setFormula(r, c, text) else sheet.setCell(r, c, text)
        sheet.recalculate()
        spreadsheetView.refresh()
        refreshFormulaBar()
    }

    // ---------- Data ----------

    private fun insertAggregate(fn: String) {
        val (r, c) = selectedPosition()
        fun numeric(cell: Cell): Double? {
            val raw = if (cell.formula != null) cell.displayValue else cell.value
            raw.trim().toDoubleOrNull()?.let { return it }
            return raw.replace("Rp", "").replace("%", "").trim()
                .replace(".", "").replace(",", ".").toDoubleOrNull()
        }
        var start = r - 1
        while (start >= 0) {
            val cell = sheet.getCell(start, c)
            if (numeric(cell) == null) break
            start--
        }
        start++
        if (start >= r) {
            Toast.makeText(this, "Tidak ada data di atas sel ini", Toast.LENGTH_SHORT).show()
            return
        }
        val colName = sheet.getColumnName(c)
        val formula = "=$fn($colName${start + 1}:$colName$r)"
        pushUndo()
        sheet.setFormula(r, c, formula)
        sheet.recalculate()
        spreadsheetView.refresh()
        refreshFormulaBar()
        Toast.makeText(this, formula, Toast.LENGTH_SHORT).show()
    }

    private fun sort(ascending: Boolean) {
        val (_, c) = selectedPosition()
        pushUndo()
        sheet.sortByColumn(c, ascending)
        spreadsheetView.refresh()
        spreadsheetView.selectCell(0, c)
        Toast.makeText(this, "Diurutkan ${if (ascending) "naik" else "turun"} pada kolom ${sheet.getColumnName(c)}",
            Toast.LENGTH_SHORT).show()
    }

    private fun showColorPicker(isBg: Boolean) {
        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.parseColor("#1B1B18"),
            Color.parseColor("#2E4A7A"), Color.parseColor("#8B3A2E"),
            Color.parseColor("#4F5F46"), Color.parseColor("#B7CFE8"),
            Color.parseColor("#F3E27A")
        )
        val dialog = BottomSheetDialog(this)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
        }
        colors.forEach { c ->
            val v = View(this).apply {
                setBackgroundColor(c)
                layoutParams = LinearLayout.LayoutParams(84, 84).apply { setMargins(10, 10, 10, 10) }
                setOnClickListener {
                    style { if (isBg) it.copy(bgColor = c) else it.copy(fontColor = c) }
                    dialog.dismiss()
                }
            }
            view.addView(v)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    /** Grafik batang dari kolom terpilih (nilai numerik baris 1..N). */
    private fun showChartDialog() {
        val (_, col) = selectedPosition()
        val labels = mutableListOf<String>()
        val values = mutableListOf<Float>()
        for (r in 0 until sheet.rows.size) {
            if (sheet.rows[r].hidden) continue
            val cell = sheet.getCell(r, col)
            val raw = cell.formattedValue()
            if (raw.isBlank()) continue
            val num = raw.replace("[^0-9.,-]".toRegex(), "").replace(".", "").replace(",", ".").toFloatOrNull()
            if (num != null) {
                labels += (sheet.getCell(r, 0).formattedValue().ifEmpty { "${r + 1}" }).take(8)
                values += num
            }
            if (values.size >= 12) break
        }
        if (values.isEmpty()) {
            Toast.makeText(this, "Tidak ada data numerik pada kolom ini", Toast.LENGTH_SHORT).show()
            return
        }
        val chart = ChartView(this, labels, values)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Grafik Kolom ${sheet.getColumnName(col)}")
            .setView(chart)
            .setPositiveButton("Tutup", null)
            .show()
    }

    /** Filter baris berdasarkan nilai unik kolom terpilih (menyembunyikan baris). */
    private fun showFilterDialog() {
        val (_, col) = selectedPosition()
        val unique = LinkedHashSet<String>()
        for (r in 0 until sheet.rows.size) {
            unique += sheet.getCell(r, col).formattedValue()
        }
        val items = unique.toTypedArray()
        if (items.isEmpty()) return
        val checked = BooleanArray(items.size) { true }
        val shown = items.map { v ->
            val r = (0 until sheet.rows.size).firstOrNull { sheet.getCell(it, col).formattedValue() == v } ?: 0
            !sheet.rows[r].hidden
        }.toBooleanArray()
        for (i in checked.indices) checked[i] = shown[i]
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Filter Kolom ${sheet.getColumnName(col)}")
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Terapkan") { _, _ ->
                val visible = items.filterIndexed { i, _ -> checked[i] }.toSet()
                for (r in 0 until sheet.rows.size) {
                    val v = sheet.getCell(r, col).formattedValue()
                    sheet.rows[r].hidden = v !in visible
                }
                sheet.filterEnabled = true
                spreadsheetView.refresh()
            }
            .setNeutralButton("Tampilkan semua") { _, _ ->
                sheet.rows.forEach { it.hidden = false }
                sheet.filterEnabled = false
                spreadsheetView.refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- Undo / redo ----------

    private fun pushUndo() {
        undoStack.addLast(sheet.deepCopy())
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Tidak ada yang bisa diurungkan", Toast.LENGTH_SHORT).show()
            return
        }
        redoStack.addLast(sheet.deepCopy())
        model.sheets[model.activeSheetIndex] = undoStack.removeLast()
        rebind()
    }

    private fun redo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Tidak ada yang bisa diulangi", Toast.LENGTH_SHORT).show()
            return
        }
        undoStack.addLast(sheet.deepCopy())
        model.sheets[model.activeSheetIndex] = redoStack.removeLast()
        rebind()
    }

    private fun rebind() {
        spreadsheetView.model = sheet
        sheet.recalculate()
        spreadsheetView.refresh()
        refreshFormulaBar()
    }

    // ---------- File ----------

    private fun newSpreadsheet() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Spreadsheet baru")
            .setMessage("Data saat ini akan diganti. Simpan dulu jika perlu.")
            .setPositiveButton("Buat") { _, _ ->
                val fresh = SpreadsheetModel()
                seedSample(fresh.activeSheet)
                model = fresh
                currentFile = null
                undoStack.clear()
                redoStack.clear()
                spreadsheetView.model = sheet
                spreadsheetView.selectCell(0, 0)
                refreshFormulaBar()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickXlsx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        try {
            startActivityForResult(intent, REQ_OPEN_XLSX)
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada pengelola berkas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_OPEN_XLSX || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val tmp = File(cacheDir, "import_${System.currentTimeMillis()}.xlsx")
            tmp.outputStream().use { o -> input.copyTo(o) }
            input.close()
            model = exporter.import(tmp)
            currentFile = tmp
            supportActionBar?.title = "Excel - ${tmp.name}"
            undoStack.clear()
            redoStack.clear()
            spreadsheetView.model = sheet
            spreadsheetView.selectCell(0, 0)
            refreshFormulaBar()
            Toast.makeText(this, "Dibuka: ${tmp.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal buka XLSX: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveFile() {
        val name = currentFile?.name ?: "Spreadsheet_${System.currentTimeMillis()}.xlsx"
        val out = currentFile ?: File(getExternalFilesDir(null), name)
        try {
            exporter.export(model, out)
            currentFile = out
            Snackbar.make(spreadsheetView, "Tersimpan: ${out.name}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile() {
        val file = currentFile
        if (file == null) {
            Toast.makeText(this, "Simpan dulu berkas", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Bagikan Spreadsheet"))
    }

    private fun printSheet() {
        val html = buildString {
            append("<html><head><style>table{border-collapse:collapse;width:100%}td,th{border:1px solid #999;padding:6px;font-family:monospace}th{background:#1B1B18;color:#F4F4EC}</style></head><body>")
            append("<h2>${sheet.name}</h2><table>")
            append("<tr><th></th>")
            for (c in 0 until sheet.cols.size) append("<th>${sheet.getColumnName(c)}</th>")
            append("</tr>")
            for (r in 0 until 30) {
                append("<tr><th>${r + 1}</th>")
                for (c in 0 until sheet.cols.size) {
                    val cell = sheet.getCell(r, c)
                    append("<td>${cell.formattedValue()}</td>")
                }
                append("</tr>")
            }
            append("</table></body></html>")
        }
        printHelper.printHtml("RetroBit_Excel_${sheet.name}", html)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_excel, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save -> { saveFile(); true }
            R.id.action_print -> { printSheet(); true }
            R.id.action_formula -> { formulaBar.requestFocus(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/** Bar chart sederhana untuk dialog grafik. */
private class ChartView(
    context: android.content.Context,
    private val labels: List<String>,
    private val values: List<Float>
) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B18") }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8E8E82"); textSize = 28f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#56564E"); textSize = 26f; textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (420 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 40f
        val maxV = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val bw = (width - 2 * pad) / values.size
        val base = height - 80f
        canvas.drawLine(pad, base, width - pad, base, axisPaint)
        values.forEachIndexed { i, v ->
            val h = (base - pad) * (v / maxV)
            val x = pad + i * bw + bw * 0.2f
            canvas.drawRect(x, base - h, x + bw * 0.6f, base, barPaint)
            canvas.drawText(labels.getOrNull(i) ?: "", x + bw * 0.3f, base + 36f, labelPaint)
        }
    }
}
