package com.retrobit.office.word

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.core.stylus.*
import com.retrobit.office.print.PrintHelper
import java.io.File

/**
 * Word Editor Activity - Huawei MatePad Mini optimized
 * Two input modes:
 * 1. Tulisan ke Teks (Handwriting-to-Text) via ML Kit
 * 2. Tulisan Bebas (Free Ink) as annotation layer
 */
class WordEditorActivity : AppCompatActivity() {

    private lateinit var document: WordDocument
    private lateinit var editorView: WordEditorView
    private lateinit var handwritingView: HandwritingView
    private lateinit var freeDrawView: FreeDrawView
    private lateinit var toolbar: WordToolbar
    private lateinit var modeToggle: RadioGroup
    private lateinit var stylusStatus: TextView

    private var currentMode: InputMode = InputMode.HANDWRITING_TO_TEXT
    private var currentFile: File? = null

    private val exporter by lazy { DocxExporter(this) }
    private val printHelper by lazy { PrintHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_editor)

        // Toolbar
        val appBar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(appBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Word - RetroBit Office"

        document = WordDocument()
        intent.getStringExtra("file_path")?.let { path ->
            val f = File(path)
            if (f.exists()) {
                try {
                    document = DocxImporter().import(f)
                    currentFile = f
                    supportActionBar?.title = f.name
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal buka file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (document.paragraphs.isEmpty()) {
            document.addParagraph("Dokumen Baru", ParagraphStyle(styleName = "Title"))
            document.addParagraph("Mulai menulis dengan keyboard atau M-Pencil Huawei...", ParagraphStyle(styleName = "Normal"))
        }

        editorView = findViewById(R.id.wordEditorView)
        handwritingView = findViewById(R.id.handwritingView)
        freeDrawView = findViewById(R.id.freeDrawView)
        toolbar = findViewById(R.id.wordToolbar)
        modeToggle = findViewById(R.id.modeToggle)
        stylusStatus = findViewById(R.id.stylusStatus)

        editorView.setDocument(document)
        editorView.onDocumentChanged = { doc -> document = doc }

        setupModeToggle()
        setupHandwriting()
        setupFreeDraw()
        setupWordToolbar()
        setupFab()

        // Print button in toolbar
        findViewById<View>(R.id.btnPrint)?.setOnClickListener { printDocument() }

        // Stylus status
        handwritingView.listener = object : StylusInputView.StylusListener {
            override fun onStrokeStarted(stroke: InkStroke) {}
            override fun onStrokeUpdated(stroke: InkStroke) {}
            override fun onStrokeEnded(stroke: InkStroke, allStrokes: List<InkStroke>) {}
            override fun onStylusStateChanged(isStylus: Boolean) {
                stylusStatus.text = if (isStylus) "✏️ M-Pencil Aktif" else "👆 Sentuh"
                stylusStatus.setTextColor(if (isStylus) Color.parseColor("#137333") else Color.GRAY)
            }
            override fun onModeChanged(mode: InputMode) {}
            override fun onHandwritingResult(text: String, candidates: List<String>) {
                // Handled via separate callback
            }
        }
    }

    private fun setupModeToggle() {
        modeToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.modeHandwriting -> {
                    currentMode = InputMode.HANDWRITING_TO_TEXT
                    handwritingView.visibility = View.VISIBLE
                    freeDrawView.visibility = View.GONE
                    editorView.alpha = 1f
                    stylusStatus.text = "Mode: Tulisan → Teks"
                }
                R.id.modeFreeDraw -> {
                    currentMode = InputMode.FREE_DRAW
                    handwritingView.visibility = View.GONE
                    freeDrawView.visibility = View.VISIBLE
                    stylusStatus.text = "Mode: Tulisan Bebas (Ink)"
                }
            }
        }
        // Default
        modeToggle.check(R.id.modeHandwriting)
    }

    private fun setupHandwriting() {
        handwritingView.setOnTextRecognizedListener { text, candidates ->
            runOnUiThread {
                editorView.insertHandwritingResult(text)
                showCandidateBar(text, candidates)
                Snackbar.make(editorView, "✍️ \"$text\"", Snackbar.LENGTH_SHORT).show()
            }
        }
        // Language selector
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguagePicker()
        }
        findViewById<View>(R.id.btnClearInk)?.setOnClickListener {
            handwritingView.clearAll()
        }
        findViewById<View>(R.id.btnConvertNow)?.setOnClickListener {
            handwritingView.forceRecognizeNow()
        }
    }

    private fun setupFreeDraw() {
        // Color palette
        val colors = listOf(Color.BLACK, Color.BLUE, Color.RED, Color.parseColor("#137333"), Color.parseColor("#F6BF26"))
        val palette = findViewById<LinearLayout>(R.id.colorPalette)
        palette?.let { container ->
            colors.forEach { col ->
                val btn = View(this).apply {
                    setBackgroundColor(col)
                    layoutParams = LinearLayout.LayoutParams(72, 72).apply { setMargins(8,8,8,8) }
                    setOnClickListener {
                        freeDrawView.setColor(col)
                        handwritingView.setColor(col)
                    }
                }
                container.addView(btn)
            }
        }
        findViewById<View>(R.id.btnUndoInk)?.setOnClickListener { freeDrawView.undoLast() }
        findViewById<View>(R.id.btnClearFree)?.setOnClickListener { freeDrawView.clearCanvas() }
        findViewById<View>(R.id.btnEraser)?.setOnClickListener {
            freeDrawView.setTool(StylusTool.ERASER)
        }
        findViewById<View>(R.id.btnPen)?.setOnClickListener {
            freeDrawView.setTool(StylusTool.PEN)
        }
        // Stroke width
        findViewById<SeekBar>(R.id.seekStrokeWidth)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                val w = (v / 10f + 2f)
                freeDrawView.setStrokeWidth(w)
                handwritingView.setStrokeWidth(w)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupWordToolbar() {
        toolbar.onFormattingChanged = { action ->
            when (action) {
                is WordToolbar.WordToolbarAction.Bold -> editorView.applyFormatting(bold = action.enabled)
                is WordToolbar.WordToolbarAction.Italic -> editorView.applyFormatting(italic = action.enabled)
                is WordToolbar.WordToolbarAction.Underline -> editorView.applyFormatting(underline = action.enabled)
                is WordToolbar.WordToolbarAction.Align -> editorView.applyFormatting(alignment = action.align)
                is WordToolbar.WordToolbarAction.FontColor -> editorView.applyFormatting(color = action.color)
                is WordToolbar.WordToolbarAction.FontSize -> editorView.applyFormatting(fontSize = action.sizePt)
                is WordToolbar.WordToolbarAction.Bullet -> {
                    val para = document.paragraphs.getOrNull(0)
                    para?.bullet = action.style
                    editorView.invalidate()
                }
                is WordToolbar.WordToolbarAction.Print -> printDocument()
                is WordToolbar.WordToolbarAction.Save -> saveDocument()
                is WordToolbar.WordToolbarAction.InsertImage -> pickImage()
                else -> {}
            }
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabSave)?.setOnClickListener { saveDocument() }
        findViewById<FloatingActionButton>(R.id.fabShare)?.setOnClickListener { shareDocument() }
    }

    private fun showCandidateBar(text: String, candidates: List<String>) {
        val bar = findViewById<LinearLayout>(R.id.candidateBar) ?: return
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        candidates.take(4).forEach { cand ->
            val chip = TextView(this).apply {
                this.text = cand
                setPadding(32, 16, 32, 16)
                setBackgroundResource(R.drawable.bg_chip)
                setOnClickListener {
                    // Replace last inserted with candidate
                    if (document.paragraphs.isNotEmpty()) {
                        val last = document.paragraphs.last()
                        // Simple replace last word
                        last.text = last.text.substringBeforeLast(text) + cand
                        last.runs.lastOrNull()?.text = cand
                        editorView.invalidate()
                    }
                    bar.visibility = View.GONE
                }
            }
            bar.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8,0,8,0) })
        }
        // Auto hide after 4s
        bar.postDelayed({ bar.visibility = View.GONE }, 4000)
    }

    private fun showLanguagePicker() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_language_picker, null)
        view.findViewById<View>(R.id.langId)?.setOnClickListener {
            handwritingView.setLanguage("id")
            stylusStatus.text = "Bahasa: Indonesia"
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.langEn)?.setOnClickListener {
            handwritingView.setLanguage("en")
            stylusStatus.text = "Language: English"
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveDocument() {
        val fileName = currentFile?.name ?: "Dokumen_${System.currentTimeMillis()}.docx"
        val outFile = currentFile ?: File(getExternalFilesDir(null), fileName)

        try {
            // Merge ink if free draw has content
            val inkBmp = if (freeDrawView.getStrokeCount() > 0) freeDrawView.exportAsBitmap() else null
            if (inkBmp != null) {
                exporter.exportWithInk(document, inkBmp, outFile)
            } else {
                exporter.export(document, outFile)
            }
            currentFile = outFile
            Snackbar.make(editorView, "✓ Disimpan: ${outFile.name}", Snackbar.LENGTH_LONG)
                .setAction("Buka Folder") { openFileLocation(outFile) }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun shareDocument() {
        currentFile?.let { file ->
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan Dokumen"))
        } ?: Toast.makeText(this, "Simpan dulu dokumen", Toast.LENGTH_SHORT).show()
    }

    private fun printDocument() {
        // Generate PDF via print helper or HTML
        val html = exporter.exportHtmlPreview(document)
        printHelper.printHtml("RetroBit_Word_${document.title}", html)
    }

    private fun openFileLocation(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, file.absolutePath, Toast.LENGTH_LONG).show() }
    }

    private fun pickImage() {
        // TODO: Image picker
        Toast.makeText(this, "Pilih gambar: fitur segera hadir", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_word, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save -> { saveDocument(); true }
            R.id.action_print -> { printDocument(); true }
            R.id.action_export_pdf -> { printDocument(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
