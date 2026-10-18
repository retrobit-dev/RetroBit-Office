package com.retrobit.office.excel

import android.graphics.Color
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
        val row = cellRef.text.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0
        val colName = cellRef.text.filter { it.isLetter() }
        val col = colName.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        if (text.startsWith("=")) {
            spreadsheetView.setCellFormula(row, col, text)
        } else {
            spreadsheetView.setCellValue(row, col, text)
        }
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
        findViewById<View>(R.id.btnBold)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(bold = !it.bold) }
        }
        findViewById<View>(R.id.btnItalic)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(italic = !it.italic) }
        }
        findViewById<View>(R.id.btnBgColor)?.setOnClickListener { showColorPicker(true) }
        findViewById<View>(R.id.btnTextColor)?.setOnClickListener { showColorPicker(false) }
        findViewById<View>(R.id.btnAlignLeft)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(alignment = CellAlignment.LEFT) }
        }
        findViewById<View>(R.id.btnAlignCenter)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(alignment = CellAlignment.CENTER) }
        }
        findViewById<View>(R.id.btnBorder)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(border = CellBorder(top = true, bottom = true, left = true, right = true)) }
        }
        findViewById<View>(R.id.btnCurrency)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(numberFormat = NumberFormat.CURRENCY_IDR) }
        }
        findViewById<View>(R.id.btnPercent)?.setOnClickListener {
            spreadsheetView.applyStyleToSelection { it.copy(numberFormat = NumberFormat.PERCENT) }
        }
        findViewById<View>(R.id.btnSum)?.setOnClickListener {
            formulaBar.setText("=SUM()")
            formulaBar.setSelection(5)
            formulaBar.requestFocus()
        }
        findViewById<View>(R.id.btnChart)?.setOnClickListener {
            Toast.makeText(this, "Sisipkan Grafik - segera", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnFilter)?.setOnClickListener {
            model.activeSheet.filterEnabled = !model.activeSheet.filterEnabled
            Toast.makeText(this, if (model.activeSheet.filterEnabled) "Filter Aktif" else "Filter Mati", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnPrintExcel)?.setOnClickListener { printSheet() }
        findViewById<View>(R.id.btnSaveExcel)?.setOnClickListener { saveFile() }
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
        findViewById<View>(R.id.btnBold)?.alpha = if (style.bold) 1f else 0.5f
        findViewById<View>(R.id.btnItalic)?.alpha = if (style.italic) 1f else 0.5f
    }

    private fun saveFile() {
        val name = currentFile?.name ?: "Spreadsheet_${System.currentTimeMillis()}.xlsx"
        val out = currentFile ?: File(getExternalFilesDir(null), name)
        try {
            exporter.export(model, out)
            currentFile = out
            Snackbar.make(spreadsheetView, "✓ Disimpan: ${out.name}", Snackbar.LENGTH_LONG).show()
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
            else -> super.onOptionsItemSelected(item)
        }
    }
}
