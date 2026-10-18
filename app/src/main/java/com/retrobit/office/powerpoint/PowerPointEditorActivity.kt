package com.retrobit.office.powerpoint

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.core.stylus.FreeDrawView
import com.retrobit.office.core.stylus.HandwritingView
import com.retrobit.office.core.stylus.InputMode
import com.retrobit.office.print.PrintHelper
import java.io.File

class PowerPointEditorActivity : AppCompatActivity() {

    private lateinit var presentation: Presentation
    private lateinit var slideView: SlideEditorView
    private lateinit var freeDrawView: FreeDrawView
    private lateinit var handwritingView: HandwritingView
    private lateinit var slideThumbnailAdapter: SlideThumbAdapter

    private var currentFile: File? = null
    private val exporter by lazy { PptxExporter(this) }
    private val printHelper by lazy { PrintHelper(this) }

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
                } catch (e: Exception) { Toast.makeText(this, "Gagal buka: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        // Demo slides
        if (presentation.slides.size == 1 && presentation.slides[0].elements.size == 2) {
            presentation.slides[0].elements[0] = SlideElement.TextBox(text = "RetroBit Office", isTitle = true, fontSize = 32f, bold = true)
            presentation.slides[0].elements[1] = SlideElement.TextBox(text = "Presentasi profesional di Huawei MatePad Mini\nDukung M-Pencil • Tulisan ke Teks • Tulisan Bebas", isTitle = false, fontSize = 16f, y = 0.35f)
            val s2 = presentation.addSlide(SlideLayout.TITLE_AND_CONTENT)
            s2.elements[0] = SlideElement.TextBox(text = "Fitur Unggulan", isTitle = true, y = 0.05f)
            s2.elements[1] = SlideElement.TextBox(text = "• Word: .docx dengan toolbar lengkap\n• Excel: Rumus SUM, AVERAGE, grafik\n• PowerPoint: Transisi & tema\n• Print langsung dari aplikasi", isTitle = false, y = 0.25f, bullet = true)
            s2.elements.add(SlideElement.Chart(data = listOf(40f, 30f, 30f), labels = listOf("Word","Excel","PowerPoint")))
        }

        slideView = findViewById(R.id.slideEditorView)
        freeDrawView = findViewById(R.id.freeDrawOverlay)
        handwritingView = findViewById(R.id.handwritingOverlayPpt)

        showSlide(presentation.currentIndex)
        setupThumbnails()
        setupToolbar()
        setupInkModes()
        setupFab()
    }

    private fun showSlide(index: Int) {
        if (index !in presentation.slides.indices) return
        presentation.currentIndex = index
        slideView.slide = presentation.slides[index]
        findViewById<TextView>(R.id.slideIndicator)?.text = "${index + 1} / ${presentation.slides.size}"
        slideThumbnailAdapter.notifyDataSetChanged()
    }

    private fun setupThumbnails() {
        val rv = findViewById<RecyclerView>(R.id.slideThumbnails)
        slideThumbnailAdapter = SlideThumbAdapter(presentation.slides) { pos -> showSlide(pos) }
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = slideThumbnailAdapter
    }

    private fun setupToolbar() {
        findViewById<View>(R.id.btnAddSlide)?.setOnClickListener {
            presentation.addSlide()
            slideThumbnailAdapter.notifyItemInserted(presentation.slides.size - 1)
            showSlide(presentation.slides.size - 1)
        }
        findViewById<View>(R.id.btnDuplicate)?.setOnClickListener {
            presentation.duplicateSlide(presentation.currentIndex)
            slideThumbnailAdapter.notifyDataSetChanged()
            showSlide(presentation.currentIndex + 1)
        }
        findViewById<View>(R.id.btnDeleteSlide)?.setOnClickListener {
            if (presentation.slides.size > 1) {
                presentation.slides.removeAt(presentation.currentIndex)
                showSlide((presentation.currentIndex - 1).coerceAtLeast(0))
                slideThumbnailAdapter.notifyDataSetChanged()
            } else Toast.makeText(this, "Minimal 1 slide", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnAddText)?.setOnClickListener { slideView.addTextBox() }
        findViewById<View>(R.id.btnAddShape)?.setOnClickListener { showShapePicker() }
        findViewById<View>(R.id.btnAddTable)?.setOnClickListener {
            slideView.slide.elements.add(SlideElement.Table())
            slideView.invalidate()
        }
        findViewById<View>(R.id.btnAddChart)?.setOnClickListener {
            slideView.slide.elements.add(SlideElement.Chart())
            slideView.invalidate()
        }
        findViewById<View>(R.id.btnTheme)?.setOnClickListener { showThemePicker() }

        // Text editing
        slideView.onTextEditRequested = { textBox ->
            showTextEditDialog(textBox)
        }
        slideView.onElementSelected = { el ->
            findViewById<TextView>(R.id.selectionInfo)?.text = when (el) {
                is SlideElement.TextBox -> "Teks: ${el.text.take(20)}"
                is SlideElement.Shape -> "Bentuk: ${el.type}"
                else -> if (el == null) "Tidak ada seleksi" else el::class.simpleName
            }
        }

        findViewById<View>(R.id.btnPrev)?.setOnClickListener { showSlide(presentation.currentIndex - 1) }
        findViewById<View>(R.id.btnNext)?.setOnClickListener { showSlide(presentation.currentIndex + 1) }
    }

    private fun setupInkModes() {
        val modeGroup = findViewById<RadioGroup>(R.id.modeTogglePpt)
        modeGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.modeSelect -> {
                    freeDrawView.visibility = View.GONE
                    handwritingView.visibility = View.GONE
                    slideView.visibility = View.VISIBLE
                }
                R.id.modeDraw -> {
                    freeDrawView.visibility = View.VISIBLE
                    handwritingView.visibility = View.GONE
                    slideView.visibility = View.VISIBLE
                    Toast.makeText(this, "Mode Gambar Bebas - coret di atas slide", Toast.LENGTH_SHORT).show()
                }
                R.id.modeInkText -> {
                    handwritingView.visibility = View.VISIBLE
                    freeDrawView.visibility = View.GONE
                }
            }
        }
        handwritingView.setOnTextRecognizedListener { text, _ ->
            slideView.addTextBox(text)
            Toast.makeText(this, "Teks: $text", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnPenPpt)?.setOnClickListener { freeDrawView.setTool(com.retrobit.office.core.stylus.StylusTool.PEN) }
        findViewById<View>(R.id.btnEraserPpt)?.setOnClickListener { freeDrawView.setTool(com.retrobit.office.core.stylus.StylusTool.ERASER) }
        findViewById<View>(R.id.btnUndoPpt)?.setOnClickListener { freeDrawView.undoLast() }
        findViewById<View>(R.id.btnClearPpt)?.setOnClickListener { freeDrawView.clearCanvas() }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabPlay)?.setOnClickListener { startSlideshow() }
        findViewById<FloatingActionButton>(R.id.fabSavePpt)?.setOnClickListener { savePresentation() }
    }

    private fun showShapePicker() {
        val shapes = ShapeType.values()
        val dialog = BottomSheetDialog(this)
        val grid = GridLayout(this).apply { columnCount = 3; setPadding(32,32,32,32) }
        shapes.forEach { type ->
            val btn = Button(this).apply {
                text = type.name
                textSize = 10f
                setOnClickListener { slideView.addShape(type); dialog.dismiss() }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(8,8,8,8) })
        }
        dialog.setContentView(grid)
        dialog.show()
    }

    private fun showThemePicker() {
        val dialog = BottomSheetDialog(this)
        val view = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(32,32,32,32) }
        Theme.values().forEach { theme ->
            val card = View(this).apply {
                setBackgroundColor(theme.primary)
                layoutParams = LinearLayout.LayoutParams(140, 90).apply { setMargins(12,12,12,12) }
                setOnClickListener {
                    presentation.theme = theme
                    presentation.slides.forEach { it.background = SlideBackground(color = theme.bg) }
                    slideView.invalidate()
                    dialog.dismiss()
                }
            }
            view.addView(card)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showTextEditDialog(textBox: SlideElement.TextBox) {
        val edit = EditText(this).apply { setText(textBox.text); setPadding(32,32,32,32) }
        AlertDialog.Builder(this)
            .setTitle("Edit Teks")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                textBox.text = edit.text.toString()
                slideView.invalidate()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startSlideshow() {
        // Simple slideshow dialog
        val dialog = AlertDialog.Builder(this).create()
        val container = FrameLayout(this)
        val imageView = ImageView(this).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#AA000000")); setPadding(24,24,24,24) }
        val btnPrev = Button(this).apply { text = "◀" }
        val indicator = TextView(this).apply { setTextColor(Color.WHITE); text = "1 / ${presentation.slides.size}" }
        val btnNext = Button(this).apply { text = "▶" }
        val btnClose = Button(this).apply { text = "✕ Tutup" }
        controls.addView(btnPrev); controls.addView(indicator); controls.addView(btnNext); controls.addView(btnClose)
        container.addView(imageView)
        container.addView(controls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.BOTTOM })
        dialog.setView(container)
        var idx = presentation.currentIndex
        fun update() {
            // Render current slide to bitmap would require view rendering; simplified: just show slide number
            indicator.text = "${idx + 1} / ${presentation.slides.size}"
            // Create simple bitmap representation
            val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(presentation.slides[idx].background.color)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            c.drawText(presentation.slides[idx].elements.filterIsInstance<SlideElement.TextBox>().firstOrNull()?.text ?: "Slide ${idx+1}", 100f, 360f, p)
            imageView.setImageBitmap(bmp)
        }
        btnPrev.setOnClickListener { if (idx > 0) { idx--; update() } }
        btnNext.setOnClickListener { if (idx < presentation.slides.size - 1) { idx++; update() } }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        update()
        dialog.window?.setLayout(1200, 800)
    }

    private fun savePresentation() {
        val name = currentFile?.name ?: "Presentasi_${System.currentTimeMillis()}.pptx"
        val out = currentFile ?: File(getExternalFilesDir(null), name)
        try {
            // Collect bitmaps for slides that have ink
            val bitmaps = presentation.slides.map { slideView.getBitmap() }
            exporter.export(presentation, out, bitmaps)
            currentFile = out
            Snackbar.make(slideView, "✓ Disimpan: ${out.name}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun printPresentation() {
        val html = buildString {
            append("<html><head><style>.slide{page-break-after:always;border:1px solid #ccc;padding:40px;margin:20px;text-align:center}h1{color:#1A73E8}</style></head><body>")
            presentation.slides.forEach { slide ->
                append("<div class='slide'>")
                slide.elements.filterIsInstance<SlideElement.TextBox>().forEach { tb ->
                    if (tb.isTitle) append("<h1>${tb.text}</h1>") else append("<p>${tb.text}</p>")
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
        else -> super.onOptionsItemSelected(item)
    }

    // Thumbnail adapter
    class SlideThumbAdapter(private val slides: List<Slide>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<SlideThumbAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12,12,12,12)
                layoutParams = RecyclerView.LayoutParams(240, 160).apply { setMargins(8,8,8,8) }
                setBackgroundResource(R.drawable.bg_thumb)
            }
            val num = TextView(parent.context).apply { textSize = 10f; setTextColor(Color.GRAY) }
            val title = TextView(parent.context).apply { textSize = 12f; maxLines = 2; setTypeface(typeface, Typeface.BOLD) }
            card.addView(num); card.addView(title)
            return VH(card)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val slide = slides[position]
            val card = holder.view as LinearLayout
            (card.getChildAt(0) as TextView).text = "${position+1}"
            (card.getChildAt(1) as TextView).text = slide.elements.filterIsInstance<SlideElement.TextBox>().firstOrNull()?.text ?: "Slide ${position+1}"
            card.setOnClickListener { onClick(position) }
        }
        override fun getItemCount() = slides.size
    }
}
