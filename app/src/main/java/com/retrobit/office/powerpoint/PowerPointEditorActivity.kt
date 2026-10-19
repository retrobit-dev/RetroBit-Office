package com.retrobit.office.powerpoint

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.core.stylus.FreeDrawView
import com.retrobit.office.core.stylus.StylusTool
import com.retrobit.office.print.PrintHelper
import java.io.File

class PowerPointEditorActivity : AppCompatActivity() {

    private lateinit var presentation: Presentation
    private lateinit var slideView: SlideEditorView
    private lateinit var freeDrawView: FreeDrawView
    private lateinit var slideThumbnailAdapter: SlideThumbAdapter
    private lateinit var selectionInfo: TextView

    private var currentFile: File? = null
    private val slideInk = HashMap<String, List<com.retrobit.office.core.stylus.InkStroke>>()
    private var inkSlideId: String? = null
    private val exporter by lazy { PptxExporter(this) }
    private val printHelper by lazy { PrintHelper(this) }

    companion object {
        private const val REQ_PICK_IMAGE = 51
        private const val REQ_OPEN_PPTX = 52
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_powerpoint_editor)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "PowerPoint - RetroBit Office"

        presentation = Presentation()
        intent.getStringExtra("file_path")?.let { path ->
            val f = File(path)
            if (f.exists()) {
                try {
                    presentation = exporter.import(f)
                    currentFile = f
                    supportActionBar?.title = f.name
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal buka: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        seedDemoIfNeeded()

        slideView = findViewById(R.id.slideEditorView)
        freeDrawView = findViewById(R.id.freeDrawOverlay)
        selectionInfo = findViewById(R.id.selectionInfo)

        setupThumbnails()
        showSlide(presentation.currentIndex)
        setupToolbar()
        setupInkModes()
        setupFab()
    }

    private fun seedDemoIfNeeded() {
        if (presentation.slides.size == 1 && presentation.slides[0].elements.size == 2) {
            presentation.slides[0].elements[0] = SlideElement.TextBox(text = "RetroBit Office", isTitle = true, fontSize = 32f, bold = true)
            presentation.slides[0].elements[1] = SlideElement.TextBox(
                text = "Presentasi profesional di Huawei MatePad Mini\nKeyboard • M-Pencil • Word/Excel/PowerPoint",
                isTitle = false, fontSize = 16f, y = 0.35f
            )
            val s2 = presentation.addSlide(SlideLayout.TITLE_AND_CONTENT)
            s2.elements[0] = SlideElement.TextBox(text = "Fitur Unggulan", isTitle = true, y = 0.05f)
            s2.elements[1] = SlideElement.TextBox(
                text = "• Word: .docx, format lengkap, keyboard\n• Excel: formula SUM/AVERAGE, grafik, sort\n• PowerPoint: transisi, tema, tabel, catatan\n• Print & ekspor dari tablet",
                isTitle = false, y = 0.25f, bullet = true
            )
            s2.elements.add(SlideElement.Chart(data = listOf(40f, 30f, 30f), labels = listOf("Word", "Excel", "PPT")))
        }
    }

    private fun showSlide(index: Int) {
        if (index !in presentation.slides.indices) return
        commitInk()
        presentation.currentIndex = index
        slideView.selectElement(null)
        slideView.slide = presentation.slides[index]
        loadInk(presentation.slides[index])
        findViewById<TextView>(R.id.slideIndicator)?.text = "${index + 1} / ${presentation.slides.size}"
        if (::slideThumbnailAdapter.isInitialized) slideThumbnailAdapter.notifyDataSetChanged()
        selectionInfo.text = "TIDAK ADA SELEKSI"
    }

    /** Simpan tinta overlay ke slide yang sedang aktif. */
    private fun commitInk() {
        val id = inkSlideId ?: return
        slideInk[id] = freeDrawView.exportInkStrokes()
    }

    /** Muat tinta slide yang dipilih ke overlay. */
    private fun loadInk(slide: Slide) {
        freeDrawView.setInkStrokes(slideInk[slide.id] ?: emptyList())
        inkSlideId = slide.id
    }

    /** Tempel tinta (terpotong ke area slide) di atas bitmap slide. */
    private fun composeInk(base: Bitmap, strokes: List<com.retrobit.office.core.stylus.InkStroke>?): Bitmap {
        if (strokes.isNullOrEmpty()) return base
        val left = (slideView.left - freeDrawView.left).coerceAtLeast(0)
        val top = (slideView.top - freeDrawView.top).coerceAtLeast(0)
        val ink = freeDrawView.renderInkBitmap(
            strokes, base.width, base.height,
            left, top, slideView.width, slideView.height
        ) ?: return base
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(base, null, Rect(0, 0, base.width, base.height), null)
        c.drawBitmap(ink, 0f, 0f, null)
        return out
    }

    private fun setupThumbnails() {
        val rv = findViewById<RecyclerView>(R.id.slideThumbnails)
        slideThumbnailAdapter = SlideThumbAdapter(presentation.slides) { pos -> showSlide(pos) }
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = slideThumbnailAdapter
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
                tool("share", R.drawable.ic_pi_share, "Bagikan"),
                tool("play", R.drawable.ic_pi_play, "Putar")
            )),
            cat("slide", R.drawable.ic_pi_presentation, "SLIDE", listOf(
                tool("add_slide", R.drawable.ic_pi_plus, "Baru"),
                tool("duplicate", R.drawable.ic_pi_copy, "Duplikat"),
                tool("delete_slide", R.drawable.ic_pi_trash, "Hapus"),
                tool("move_up", R.drawable.ic_pi_arrow_up_box, "Naik"),
                tool("move_down", R.drawable.ic_pi_arrow_down_box, "Turun"),
                tool("theme", R.drawable.ic_pi_colors_swatch, "Tema"),
                tool("transition", R.drawable.ic_pi_refresh, "Transisi"),
                tool("notes", R.drawable.ic_pi_note, "Catatan")
            )),
            cat("insert", R.drawable.ic_pi_plus, "SISIPKAN", listOf(
                tool("text", R.drawable.ic_pi_text_cursor_input, "Teks"),
                tool("shape", R.drawable.ic_pi_shapes, "Bentuk"),
                tool("table", R.drawable.ic_pi_table, "Tabel"),
                tool("chart", R.drawable.ic_pi_chart_bar_big, "Grafik"),
                tool("image", R.drawable.ic_pi_image_new, "Gambar")
            )),
            cat("format", R.drawable.ic_pi_text_start_t, "FORMAT", listOf(
                tool("bold", R.drawable.ic_pi_bold, "Tebal"),
                tool("italic", R.drawable.ic_pi_italic, "Miring"),
                tool("underline", R.drawable.ic_pi_underline, "Garis"),
                tool("size_up", R.drawable.ic_pi_type_size, "A+"),
                tool("size_down", R.drawable.ic_pi_type_size, "A−"),
                tool("color", R.drawable.ic_pi_colors_swatch, "Warna"),
                tool("align_l", R.drawable.ic_pi_text_align_left, "Kiri"),
                tool("align_c", R.drawable.ic_pi_text_align_center, "Tengah"),
                tool("align_r", R.drawable.ic_pi_text_align_right, "Kanan"),
                tool("front", R.drawable.ic_pi_arrow_up_box, "Ke Depan"),
                tool("back", R.drawable.ic_pi_arrow_down_box, "Ke Belakang"),
                tool("dup_element", R.drawable.ic_pi_files, "Duplikat"),
                tool("del_element", R.drawable.ic_pi_delete, "Hapus")
            )),
            cat("draw", R.drawable.ic_pi_brush, "CORET", listOf(
                tool("pen", R.drawable.ic_pi_pencil, "Pena"),
                tool("eraser", R.drawable.ic_pi_eraser, "Hapus"),
                tool("undo_ink", R.drawable.ic_pi_undo, "Undo"),
                tool("clear_ink", R.drawable.ic_pi_trash, "Bersih")
            ))
        ))
        sidebar.onToolSelected = { _, toolId -> handleTool(toolId) }

        slideView.onTextEditRequested = { textBox -> showTextEditDialog(textBox) }
        slideView.onElementSelected = { el ->
            selectionInfo.text = when (el) {
                is SlideElement.TextBox -> "TEKS: ${el.text.take(22).uppercase()}"
                is SlideElement.Shape -> "BENTUK: ${el.type}"
                is SlideElement.Table -> "TABEL ${el.rows}×${el.cols}"
                is SlideElement.Chart -> "GRAFIK: ${el.chartType}"
                is SlideElement.Image -> "GAMBAR"
                null -> "TIDAK ADA SELEKSI"
            }
        }

        findViewById<View>(R.id.btnPrev)?.setOnClickListener { showSlide(presentation.currentIndex - 1) }
        findViewById<View>(R.id.btnNext)?.setOnClickListener { showSlide(presentation.currentIndex + 1) }
    }

    private fun handleTool(toolId: String) {
        when (toolId) {
            "new" -> newPresentation()
            "open" -> pickPptx()
            "save" -> savePresentation()
            "print" -> printPresentation()
            "share" -> sharePresentation()
            "play" -> startSlideshow()
            "add_slide" -> {
                presentation.addSlide()
                slideThumbnailAdapter.notifyItemInserted(presentation.slides.size - 1)
                showSlide(presentation.slides.size - 1)
            }
            "duplicate" -> {
                commitInk()
                val srcId = presentation.slides[presentation.currentIndex].id
                presentation.duplicateSlide(presentation.currentIndex)
                val newId = presentation.slides[presentation.currentIndex + 1].id
                slideInk[srcId]?.let { slideInk[newId] = it }
                slideThumbnailAdapter.notifyDataSetChanged()
                showSlide(presentation.currentIndex + 1)
            }
            "delete_slide" -> {
                if (presentation.slides.size > 1) {
                    slideInk.remove(presentation.slides[presentation.currentIndex].id)
                    presentation.slides.removeAt(presentation.currentIndex)
                    inkSlideId = null
                    showSlide((presentation.currentIndex - 1).coerceAtLeast(0))
                    slideThumbnailAdapter.notifyDataSetChanged()
                } else Toast.makeText(this, "Minimal 1 slide", Toast.LENGTH_SHORT).show()
            }
            "move_up" -> moveSlide(-1)
            "move_down" -> moveSlide(1)
            "theme" -> showThemePicker()
            "transition" -> showTransitionPicker()
            "notes" -> showNotesDialog()
            "text" -> {
                slideView.addTextBox("Teks baru")
                slideView.selectElement(slideView.slide.elements.last())
                showTextEditDialog(slideView.slide.elements.last() as SlideElement.TextBox)
            }
            "shape" -> showShapePicker()
            "table" -> showTableDialog()
            "chart" -> showChartDialog()
            "image" -> pickImage()
            "bold" -> editText { it.bold = !it.bold }
            "italic" -> editText { it.italic = !it.italic }
            "underline" -> editText { it.underline = !it.underline }
            "size_up" -> editText { it.fontSize = (it.fontSize + 2f).coerceAtMost(96f) }
            "size_down" -> editText { it.fontSize = (it.fontSize - 2f).coerceAtLeast(8f) }
            "color" -> showElementColorPicker()
            "align_l" -> editText { it.alignment = 0 }
            "align_c" -> editText { it.alignment = 1 }
            "align_r" -> editText { it.alignment = 2 }
            "front" -> if (!slideView.bringElementToFront()) needSelection()
            "back" -> if (!slideView.sendElementToBack()) needSelection()
            "dup_element" -> if (!slideView.duplicateSelected()) needSelection()
            "del_element" -> if (!slideView.removeSelected()) needSelection()
            "pen" -> {
                findViewById<RadioButton>(R.id.modeDraw).isChecked = true
                freeDrawView.setTool(StylusTool.PEN)
            }
            "eraser" -> {
                findViewById<RadioButton>(R.id.modeDraw).isChecked = true
                freeDrawView.setTool(StylusTool.ERASER)
            }
            "undo_ink" -> freeDrawView.undoLast()
            "clear_ink" -> freeDrawView.clearCanvas()
        }
    }

    private fun needSelection() {
        Toast.makeText(this, "Ketuk elemen di slide dulu", Toast.LENGTH_SHORT).show()
    }

    private fun editText(transform: (SlideElement.TextBox) -> Unit) {
        if (!slideView.applyToSelectedText(transform)) needSelection()
    }

    private fun moveSlide(delta: Int) {
        val from = presentation.currentIndex
        val to = from + delta
        if (to !in presentation.slides.indices) return
        val s = presentation.slides.removeAt(from)
        presentation.slides.add(to, s)
        slideThumbnailAdapter.notifyDataSetChanged()
        showSlide(to)
    }

    private fun setupInkModes() {
        val modeGroup = findViewById<RadioGroup>(R.id.modeTogglePpt)
        modeGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.modeSelect -> {
                    freeDrawView.visibility = View.GONE
                    slideView.visibility = View.VISIBLE
                    slideView.selectElement(null)
                }
                R.id.modeDraw -> {
                    freeDrawView.visibility = View.VISIBLE
                    slideView.visibility = View.VISIBLE
                    Toast.makeText(this, "Mode gambar — coret di atas slide", Toast.LENGTH_SHORT).show()
                }
            }
        }
        modeGroup.check(R.id.modeSelect)
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabPlay)?.setOnClickListener { startSlideshow() }
        findViewById<FloatingActionButton>(R.id.fabSavePpt)?.setOnClickListener { savePresentation() }
    }

    // ---------- Insert ----------

    private fun showShapePicker() {
        val shapes = ShapeType.values()
        val dialog = BottomSheetDialog(this)
        val grid = GridLayout(this).apply { columnCount = 3; setPadding(32, 32, 32, 32) }
        shapes.forEach { type ->
            val btn = Button(this).apply {
                text = type.name
                textSize = 10f
                setOnClickListener {
                    slideView.addShape(type)
                    dialog.dismiss()
                }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            })
        }
        dialog.setContentView(grid)
        dialog.show()
    }

    private fun showTableDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val rows = EditText(this).apply {
            hint = "Baris"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        val cols = EditText(this).apply {
            hint = "Kolom"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        layout.addView(rows)
        layout.addView(cols)
        AlertDialog.Builder(this)
            .setTitle("Sisipkan tabel")
            .setView(layout)
            .setPositiveButton("Sisipkan") { _, _ ->
                val r = rows.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 3
                val c = cols.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 3
                slideView.slide.elements.add(
                    SlideElement.Table(rows = r, cols = c, data = List(r) { List(c) { "" } })
                )
                slideView.notifyChanged()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showChartDialog() {
        val input = EditText(this).apply {
            hint = "Nilai dipisah koma, mis. 40,30,30"
            setText("40,30,30")
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Grafik batang")
            .setView(input)
            .setPositiveButton("Sisipkan") { _, _ ->
                val values = input.text.toString().split(",")
                    .mapNotNull { it.trim().toFloatOrNull() }
                    .ifEmpty { listOf(30f, 50f, 20f) }
                val labels = values.indices.map { ('A'.code + it).toChar().toString() }
                slideView.slide.elements.add(SlideElement.Chart(data = values, labels = labels))
                slideView.notifyChanged()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQ_PICK_IMAGE)
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada galeri", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OPEN_PPTX && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val input = contentResolver.openInputStream(uri) ?: return
                val tmp = File(cacheDir, "import_${System.currentTimeMillis()}.pptx")
                tmp.outputStream().use { o -> input.copyTo(o) }
                input.close()
                presentation = exporter.import(tmp)
                currentFile = tmp
                supportActionBar?.title = "PowerPoint - ${tmp.name}"
                slideInk.clear()
                inkSlideId = null
                setupThumbnails()
                showSlide(0)
                Toast.makeText(this, "Dibuka: ${tmp.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal buka PPTX: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val input = contentResolver.openInputStream(uri) ?: return
                val dir = File(filesDir, "ppt_images").apply { mkdirs() }
                val out = File(dir, "img_${System.currentTimeMillis()}.png")
                out.outputStream().use { o -> input.copyTo(o) }
                input.close()
                slideView.insertImage(out.absolutePath)
                Toast.makeText(this, "Gambar disisipkan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- Pickers ----------

    private fun newPresentation() {
        AlertDialog.Builder(this)
            .setTitle("Presentasi baru")
            .setMessage("Presentasi saat ini akan diganti. Simpan dulu jika perlu.")
            .setPositiveButton("Buat") { _, _ ->
                presentation = Presentation()
                currentFile = null
                slideInk.clear()
                inkSlideId = null
                setupThumbnails()
                showSlide(0)
                supportActionBar?.title = "PowerPoint - RetroBit Office"
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickPptx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        }
        try {
            startActivityForResult(intent, REQ_OPEN_PPTX)
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada pengelola berkas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showThemePicker() {
        val dialog = BottomSheetDialog(this)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
        }
        Theme.values().forEach { theme ->
            val card = View(this).apply {
                setBackgroundColor(theme.primary)
                layoutParams = LinearLayout.LayoutParams(140, 90).apply { setMargins(12, 12, 12, 12) }
                setOnClickListener {
                    presentation.theme = theme
                    presentation.slides.forEach { it.background = SlideBackground(color = theme.bg) }
                    slideView.notifyChanged()
                    dialog.dismiss()
                }
            }
            view.addView(card)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showTextEditDialog(textBox: SlideElement.TextBox) {
        val edit = EditText(this).apply {
            setText(textBox.text)
            setPadding(32, 24, 32, 24)
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit teks (keyboard)")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                textBox.text = edit.text.toString()
                slideView.notifyChanged()
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            edit.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun showNotesDialog() {
        val slide = presentation.slides[presentation.currentIndex]
        val edit = EditText(this).apply {
            setText(slide.notes)
            setPadding(32, 24, 32, 24)
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Catatan slide ${presentation.currentIndex + 1}")
            .setView(edit)
            .setPositiveButton("Simpan") { _, _ ->
                slide.notes = edit.text.toString()
                Toast.makeText(this, "Catatan disimpan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            edit.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun showElementColorPicker() {
        val el = slideView.getSelectedElement()
        if (el == null) {
            needSelection()
            return
        }
        val colors = listOf(
            Color.parseColor("#1B1B18"), Color.parseColor("#2E4A7A"),
            Color.parseColor("#8B3A2E"), Color.parseColor("#4F5F46"),
            Color.parseColor("#FFFFFF"), Color.parseColor("#F3E27A")
        )
        val dialog = BottomSheetDialog(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
        }
        colors.forEach { c ->
            val v = View(this).apply {
                setBackgroundColor(c)
                layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(10, 10, 10, 10) }
                setOnClickListener {
                    when (el) {
                        is SlideElement.TextBox -> el.fontColor = c
                        is SlideElement.Shape -> el.fillColor = c
                        else -> {}
                    }
                    slideView.notifyChanged()
                    dialog.dismiss()
                }
            }
            row.addView(v)
        }
        dialog.setContentView(row)
        dialog.show()
    }

    private fun showTransitionPicker() {
        val items = Transition.entries.map {
            when (it) {
                Transition.NONE -> "Tanpa transisi"
                Transition.FADE -> "Fade"
                Transition.PUSH -> "Push"
                Transition.WIPE -> "Wipe"
                Transition.ZOOM -> "Zoom"
                Transition.MORPH -> "Morph"
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Transisi slide")
            .setItems(items) { _, which ->
                presentation.slides[presentation.currentIndex].transition = Transition.entries[which]
                Toast.makeText(this, "Transisi: ${items[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- Slideshow ----------

    private fun renderSlideBitmap(index: Int): Bitmap {
        val prev = slideView.slide
        val prevSelection = slideView.getSelectedElement()
        slideView.selectElement(null)
        slideView.slide = presentation.slides[index]
        val bmp = slideView.getBitmap()
        slideView.slide = prev
        slideView.selectElement(prevSelection)
        return bmp
    }

    private fun startSlideshow() {
        val dialog = AlertDialog.Builder(this).create()
        val container = FrameLayout(this)
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(24, 18, 24, 18)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val btnPrev = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pi_chevron_left)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val indicator = TextView(this).apply {
            setTextColor(Color.WHITE)
            setPadding(24, 0, 24, 0)
        }
        val btnNext = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pi_chevron_right)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val btnClose = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pi_close)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val spacer = View(this)
        controls.addView(btnPrev)
        controls.addView(indicator)
        controls.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(btnNext)
        controls.addView(btnClose, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 24 })
        container.addView(imageView)
        container.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.BOTTOM })
        dialog.setView(container)

        var idx = presentation.currentIndex
        var previousBmp: Bitmap? = null

        fun update(animate: Boolean) {
            val bmp = renderSlideBitmap(idx)
            indicator.text = "${idx + 1} / ${presentation.slides.size}"
            val trans = presentation.slides[idx].transition
            if (animate && previousBmp != null && trans != Transition.NONE) {
                val prev = previousBmp!!
                val layers = android.graphics.drawable.TransitionDrawable(
                    arrayOf(
                        android.graphics.drawable.BitmapDrawable(resources, prev),
                        android.graphics.drawable.BitmapDrawable(resources, bmp)
                    )
                )
                imageView.setImageDrawable(layers)
                layers.startTransition(450)
                when (trans) {
                    Transition.PUSH -> imageView.animate().translationX(300f).translationX(0f).setDuration(450).start()
                    Transition.ZOOM -> imageView.animate().scaleX(1.15f).scaleX(1f).scaleY(1.15f).scaleY(1f).setDuration(450).start()
                    else -> {}
                }
            } else {
                imageView.setImageBitmap(bmp)
            }
            previousBmp = bmp
        }

        btnPrev.setOnClickListener { if (idx > 0) { idx--; update(true) } }
        btnNext.setOnClickListener { if (idx < presentation.slides.size - 1) { idx++; update(true) } }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.88).toInt()
        )
        update(false)
    }

    // ---------- File ----------

    private fun savePresentation() {
        val name = currentFile?.name ?: "Presentasi_${System.currentTimeMillis()}.pptx"
        val out = currentFile ?: File(getExternalFilesDir(null), name)
        try {
            commitInk()
            val bitmaps = presentation.slides.mapIndexed { i, slide ->
                composeInk(renderSlideBitmap(i), slideInk[slide.id])
            }
            exporter.export(presentation, out, bitmaps)
            currentFile = out
            Snackbar.make(slideView, "Tersimpan: ${out.name}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePresentation() {
        val file = currentFile
        if (file == null) {
            Toast.makeText(this, "Simpan dulu presentasi", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Bagikan Presentasi"))
    }

    private fun printPresentation() {
        val html = buildString {
            append("<html><head><style>.slide{page-break-after:always;border:1px solid #999;padding:40px;margin:20px}h1{font-family:monospace}</style></head><body>")
            presentation.slides.forEach { slide ->
                append("<div class='slide'>")
                slide.elements.filterIsInstance<SlideElement.TextBox>().forEach { tb ->
                    if (tb.isTitle) append("<h1>${tb.text}</h1>") else append("<p>${tb.text.replace("\n", "<br>")}</p>")
                }
                append("</div>")
            }
            append("</body></html>")
        }
        printHelper.printHtml("RetroBit_PPT_${presentation.title}", html)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ppt, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_save -> { savePresentation(); true }
        R.id.action_print -> { printPresentation(); true }
        R.id.action_slideshow -> { startSlideshow(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // Thumbnail adapter
    class SlideThumbAdapter(private val slides: List<Slide>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SlideThumbAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                layoutParams = RecyclerView.LayoutParams(240, 160).apply { setMargins(8, 8, 8, 8) }
                setBackgroundResource(R.drawable.bg_thumb)
            }
            val num = TextView(parent.context).apply {
                textSize = 10f
                setTextColor(Color.GRAY)
            }
            val title = TextView(parent.context).apply {
                textSize = 12f
                maxLines = 2
                setTypeface(typeface, Typeface.BOLD)
            }
            card.addView(num)
            card.addView(title)
            return VH(card)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val slide = slides[position]
            val card = holder.view as LinearLayout
            (card.getChildAt(0) as TextView).text = "${position + 1}"
            (card.getChildAt(1) as TextView).text =
                slide.elements.filterIsInstance<SlideElement.TextBox>().firstOrNull()?.text ?: "Slide ${position + 1}"
            card.setOnClickListener { onClick(position) }
        }
        override fun getItemCount() = slides.size
    }
}
