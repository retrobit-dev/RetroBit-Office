package com.retrobit.office.excel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.core.stylus.HandwritingView
import com.retrobit.office.core.stylus.InputMode
import com.retrobit.office.print.PrintHelper
import java.io.File

class ExcelEditorActivity : AppCompatActivity() {

    private lateinit var spreadsheetView: SpreadsheetView
    private lateinit var handwritingView: HandwritingView
    private lateinit var formulaBar: EditText
    private lateinit var cellRef: TextView
    private lateinit var model: SpreadsheetModel
    private var currentFile: File? = null

    private val exporter = XlsxExporter()
    private val printHelper by lazy { PrintHelper(this) }

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
                } catch (e: Exception) { Toast.makeText(this, "Gagal buka: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        // Sample data
        if (model.activeSheet.getCell(0,0).isEmpty) {
            model.activeSheet.setCell(0, 0, "RetroBit Office", CellStyle(bold = true, bgColor = Color.parseColor("#1A73E8"), fontColor = Color.WHITE))
            model.activeSheet.setCell(0, 1, "Penjualan", CellStyle(bold = true, bgColor = Color.parseColor("#1A73E8"), fontColor = Color.WHITE))
            model.activeSheet.setCell(1, 0, "Januari")
            model.activeSheet.setCell(1, 1, "1500000")
            model.activeSheet.setCell(2, 0, "Februari")
            model.activeSheet.setCell(2, 1, "2300000")
            model.activeSheet.setCell(3, 0, "Total")
            model.activeSheet.setFormula(3, 1, "=SUM(B2:B3)")
        }

        spreadsheetView = findViewById(R.id.spreadsheetView)
        handwritingView = findViewById(R.id.handwritingOverlay)
        formulaBar = findViewById(R.id.formulaBar)
        cellRef = findViewById(R.id.cellRef)

        spreadsheetView.model = model.activeSheet

        spreadsheetView.onCellSelected = { row, col, cell ->
            cellRef.text = "${model.activeSheet.getColumnName(col)}${row+1}"
            formulaBar.setText(cell.formula ?: cell.value)
            updateStyleIndicators(cell.style)
        }
        spreadsheetView.onCellEdited = { row, col, value ->
            // Show edit dialog or focus formula bar
            formulaBar.requestFocus()
            formulaBar.setText(value)
            formulaBar.setSelection(formulaBar.text.length)
            // Show keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(formulaBar, 0)
        }

        formulaBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commitFormula()
                true
            } else false
        }
        findViewById<View>(R.id.btnCommitFormula)?.setOnClickListener { commitFormula() }

        // Handwriting overlay for cell input
        handwritingView.setOnTextRecognizedListener { text, _ ->
            runOnUiThread {
                spreadsheetView.insertHandwritingText(text)
                Snackbar.make(spreadsheetView, "Sel ${cellRef.text} = \"$text\"", Snackbar.LENGTH_SHORT).show()
            }
        }

        setupToolbar()
        setupModeToggle()
    }

    private fun commitFormula() {
        val text = formulaBar.text.toString()
        val refText = cellRef.text.toString()
        val row = refText.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0
        val colName = refText.filter { it.isLetter() }
        val col = colName.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        if (text.startsWith("=")) {
            spreadsheetView.setCellFormula(row, col, text)
        } else {
            spreadsheetView.setCellValue(row, col, text)
        }
    }

    private fun selectedPosition(): Pair<Int, Int> {
        val refText = cellRef.text.toString()
        val row = refText.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0
        val colName = refText.filter { it.isLetter() }
        val col = colName.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        return row to col
    }

    /** Grafik batang dari kolom terpilih (nilai numerik baris 1..N). */
    private fun showChartDialog() {
        val (_, col) = selectedPosition()
        val sheet = model.activeSheet
        val labels = mutableListOf<String>()
        val values = mutableListOf<Float>()
        for (r in 0 until sheet.rows.size) {
            if (sheet.rows[r].hidden) continue
            val raw = sheet.getCell(r, col).displayValue.ifEmpty { sheet.getCell(r, col).value }
            if (raw.isBlank()) continue
            val num = raw.replace("[^0-9.,-]".toRegex(), "").replace(",", ".").toFloatOrNull()
            if (num != null) {
                labels += (sheet.getCell(r, 0).value.ifEmpty { "${r + 1}" }).take(8)
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

    /** Filter baris berdasarkan nilai unik kolom terpilih (nyembunyikan baris). */
    private fun showFilterDialog() {
        val (_, col) = selectedPosition()
        val sheet = model.activeSheet
        val unique = LinkedHashSet<String>()
        for (r in 0 until sheet.rows.size) {
            unique += sheet.getCell(r, col).displayValue.ifEmpty { sheet.getCell(r, col).value }
        }
        val items = unique.toTypedArray()
        if (items.isEmpty()) return
        val checked = BooleanArray(items.size) { true }
        val shown = items.map { v ->
            val r = (0 until sheet.rows.size).firstOrNull { sheet.getCell(it, col).displayValue.ifEmpty { sheet.getCell(it, col).value } == v } ?: 0
            !sheet.rows[r].hidden
        }.toBooleanArray()
        for (i in checked.indices) checked[i] = shown[i]
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Filter Kolom ${sheet.getColumnName(col)}")
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Terapkan") { _, _ ->
                val visible = items.filterIndexed { i, _ -> checked[i] }.toSet()
                for (r in 0 until sheet.rows.size) {
                    val v = sheet.getCell(r, col).displayValue.ifEmpty { sheet.getCell(r, col).value }
                    sheet.rows[r].hidden = v !in visible
                }
                sheet.filterEnabled = true
                spreadsheetView.invalidate()
            }
            .setNeutralButton("Tampilkan semua") { _, _ ->
                sheet.rows.forEach { it.hidden = false }
                spreadsheetView.invalidate()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupModeToggle() {
        findViewById<RadioGroup>(R.id.modeToggleExcel)?.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.modeTouch -> {
                    handwritingView.visibility = View.GONE
                    Toast.makeText(this, "Mode Sentuh", Toast.LENGTH_SHORT).show()
                }
                R.id.modeStylusText -> {
                    handwritingView.visibility = View.VISIBLE
                    handwritingView.config.mode = InputMode.HANDWRITING_TO_TEXT
                }
                R.id.modeStylusDraw -> {
                    handwritingView.visibility = View.VISIBLE
                    handwritingView.config.mode = InputMode.FREE_DRAW
                }
            }
        }
    }

    private fun setupToolbar() {
        val sidebar = findViewById<com.retrobit.office.ui.ToolSidebar>(R.id.toolSidebar)
        sidebar.setCategories(listOf(
            com.retrobit.office.ui.ToolCategory("cell", R.drawable.ic_pi_frame, "SEL", listOf(
                com.retrobit.office.ui.ToolItem("bold", R.drawable.ic_pi_bold, "Tebal"),
                com.retrobit.office.ui.ToolItem("italic", R.drawable.ic_pi_italic, "Miring"),
                com.retrobit.office.ui.ToolItem("text_color", R.drawable.ic_pi_colors_swatch, "Warna"),
                com.retrobit.office.ui.ToolItem("cell_bg", R.drawable.ic_pi_image, "IsiSel"),
                com.retrobit.office.ui.ToolItem("align_l", R.drawable.ic_pi_text_align_left, "Kiri"),
                com.retrobit.office.ui.ToolItem("align_c", R.drawable.ic_pi_text_align_center, "Tengah"),
                com.retrobit.office.ui.ToolItem("border", R.drawable.ic_pi_table, "Batas")
            )),
            com.retrobit.office.ui.ToolCategory("number", R.drawable.ic_pi_money, "ANGKA", listOf(
                com.retrobit.office.ui.ToolItem("currency", R.drawable.ic_pi_money, "Rp"),
                com.retrobit.office.ui.ToolItem("percent", R.drawable.ic_pi_percent, "%"),
                com.retrobit.office.ui.ToolItem("sum", R.drawable.ic_pi_sigma, "Jumlah")
            )),
            com.retrobit.office.ui.ToolCategory("data", R.drawable.ic_pi_filter, "DATA", listOf(
                com.retrobit.office.ui.ToolItem("chart", R.drawable.ic_pi_chart_bar_big, "Grafik"),
                com.retrobit.office.ui.ToolItem("filter", R.drawable.ic_pi_filter, "Filter")
            )),
            com.retrobit.office.ui.ToolCategory("file", R.drawable.ic_pi_save, "BERKAS", listOf(
                com.retrobit.office.ui.ToolItem("print", R.drawable.ic_pi_printer, "Cetak"),
                com.retrobit.office.ui.ToolItem("save", R.drawable.ic_pi_save, "Simpan")
            ))
        ))
        sidebar.onToolSelected = { _, toolId -> handleTool(toolId) }
    }

    private fun handleTool(toolId: String) {
        when (toolId) {
            "bold" -> spreadsheetView.applyStyleToSelection { it.copy(bold = !it.bold) }
            "italic" -> spreadsheetView.applyStyleToSelection { it.copy(italic = !it.italic) }
            "text_color" -> showColorPicker(false)
            "cell_bg" -> showColorPicker(true)
            "align_l" -> spreadsheetView.applyStyleToSelection { it.copy(alignment = CellAlignment.LEFT) }
            "align_c" -> spreadsheetView.applyStyleToSelection { it.copy(alignment = CellAlignment.CENTER) }
            "border" -> spreadsheetView.applyStyleToSelection { it.copy(border = CellBorder(top = true, bottom = true, left = true, right = true)) }
            "currency" -> spreadsheetView.applyStyleToSelection { it.copy(numberFormat = NumberFormat.CURRENCY_IDR) }
            "percent" -> spreadsheetView.applyStyleToSelection { it.copy(numberFormat = NumberFormat.PERCENT) }
            "sum" -> {
                formulaBar.setText("=SUM()")
                formulaBar.setSelection(5)
                formulaBar.requestFocus()
            }
            "chart" -> showChartDialog()
            "filter" -> showFilterDialog()
            "print" -> printSheet()
            "save" -> saveFile()
        }
    }

    private fun showColorPicker(isBg: Boolean) {
        val colors = listOf(Color.WHITE, Color.BLACK, Color.parseColor("#1A73E8"), Color.parseColor("#34A853"), Color.parseColor("#FBBC04"), Color.parseColor("#EA4335"))
        val dialog = BottomSheetDialog(this)
        val view = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32,32,32,32) }
        colors.forEach { c ->
            val v = View(this).apply {
                setBackgroundColor(c)
                layoutParams = LinearLayout.LayoutParams(90,90).apply { setMargins(12,12,12,12) }
                setOnClickListener {
                    spreadsheetView.applyStyleToSelection { if (isBg) it.copy(bgColor = c) else it.copy(fontColor = c) }
                    dialog.dismiss()
                }
            }
            view.addView(v)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun updateStyleIndicators(style: CellStyle) {
        // indikator gaya ditangani sidebar (state tombol kategori)
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

    private fun printSheet() {
        // Generate HTML table for printing
        val html = buildString {
            append("<html><head><style>table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:8px}th{background:#1A73E8;color:white}</style></head><body>")
            append("<h2>${model.activeSheet.name}</h2><table>")
            // Header row
            append("<tr><th></th>")
            for (c in 0 until model.activeSheet.cols.size) append("<th>${model.activeSheet.getColumnName(c)}</th>")
            append("</tr>")
            for (r in 0 until 20) {
                append("<tr><th>${r+1}</th>")
                for (c in 0 until model.activeSheet.cols.size) {
                    val cell = model.activeSheet.getCell(r,c)
                    append("<td>${cell.displayValue.ifEmpty { cell.value }}</td>")
                }
                append("</tr>")
            }
            append("</table></body></html>")
        }
        printHelper.printHtml("RetroBit_Excel_${model.activeSheet.name}", html)
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

/** Custom bar chart sederhana untuk dialog grafik. */
private class ChartView(
    context: android.content.Context,
    private val labels: List<String>,
    private val values: List<Float>
) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A73E8") }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA0A6"); textSize = 28f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5F6368"); textSize = 26f; textAlign = Paint.Align.CENTER }

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

