package com.retrobit.office.word

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.retrobit.office.R
import com.retrobit.office.core.stylus.FreeDrawView
import com.retrobit.office.core.stylus.StylusTool
import com.retrobit.office.print.PrintHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Word Editor Activity — keyboard-first.
 * Ketuk paragraf di kanvas, ketik lewat keyboard (input bar), format lewat sidebar.
 */
class WordEditorActivity : AppCompatActivity() {

    private lateinit var document: WordDocument
    private lateinit var editorView: WordEditorView
    private lateinit var freeDrawView: FreeDrawView
    private lateinit var sidebar: com.retrobit.office.ui.ToolSidebar
    private lateinit var modeToggle: RadioGroup
    private lateinit var stylusStatus: TextView
    private lateinit var etInput: EditText
    private lateinit var docScroll: androidx.core.widget.NestedScrollView

    private var currentFile: File? = null
    private var drawingMode = false
    private var suppressSync = false
    private var typingSnapshotIndex = -1

    private val undoStack = ArrayDeque<WordDocument>()
    private val redoStack = ArrayDeque<WordDocument>()

    companion object {
        private const val REQ_PICK_IMAGE = 41
        private const val REQ_OPEN_DOCX = 42
    }

    private val exporter by lazy { DocxExporter(this) }
    private val printHelper by lazy { PrintHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_editor)

        val appBar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(appBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Word - RetroBit Office"

        document = WordDocument()
        intent.getStringExtra("file_path")?.let { path -> loadDocx(File(path)) }
        if (document.paragraphs.isEmpty()) {
            document.addParagraph("Dokumen Baru", ParagraphStyle(styleName = "Title"))
            document.addParagraph("Ketuk paragraf ini lalu ketik di keyboard...", ParagraphStyle(styleName = "Normal"))
        }

        editorView = findViewById(R.id.wordEditorView)
        freeDrawView = findViewById(R.id.freeDrawView)
        sidebar = findViewById(R.id.toolSidebar)
        modeToggle = findViewById(R.id.modeToggle)
        stylusStatus = findViewById(R.id.stylusStatus)
        etInput = findViewById(R.id.etInput)
        docScroll = findViewById(R.id.docScroll)

        editorView.bindDocument(document)
        editorView.onDocumentChanged = { doc -> document = doc; updateStatus() }
        editorView.onParagraphSelected = { index -> onParagraphSelected(index, true) }

        setupInputBar()
        setupModeToggle()
        setupWordToolbar()
        setupFab()

        findViewById<View>(R.id.btnPrint)?.setOnClickListener { printDocument() }
        findViewById<View>(R.id.btnNewParagraph)?.setOnClickListener {
            pushUndo()
            editorView.insertParagraphAfter("")
            syncInputFromCursor()
        }
        findViewById<View>(R.id.btnKeyboard)?.setOnClickListener { focusInput() }

        editorView.post { onParagraphSelected(0, false) }
    }

    // ---------- Keyboard <-> paragraph ----------

    private fun setupInputBar() {
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressSync) return
                if (typingSnapshotIndex != editorView.getCursorIndex()) {
                    pushUndo()
                    typingSnapshotIndex = editorView.getCursorIndex()
                }
                editorView.setParagraphText(s?.toString() ?: "")
                updateStatus()
            }
        })
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                pushUndo()
                val full = etInput.text.toString()
                val caret = etInput.selectionStart.coerceIn(0, full.length)
                editorView.setParagraphText(full.substring(0, caret))
                editorView.insertParagraphAfter(full.substring(caret))
                syncInputFromCursor()
                true
            } else false
        }
        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !drawingMode) {
                stylusStatus.text = "MODE: KEYBOARD | KETUK PARAGRAF UNTUK MENGEDIT"
            }
        }
    }

    private fun onParagraphSelected(index: Int, openKeyboard: Boolean) {
        syncInputFromCursor()
        if (openKeyboard) {
            etInput.requestFocus()
            etInput.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        updateStatus()
    }

    private fun syncInputFromCursor() {
        typingSnapshotIndex = -1
        suppressSync = true
        val p = editorView.currentParagraph()
        val text = p?.runs?.joinToString("") { it.text } ?: p?.text ?: ""
        etInput.setText(text)
        etInput.setSelection(etInput.text.length)
        suppressSync = false
        updateStatus()
    }

    private fun focusInput() {
        etInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateStatus() {
        if (drawingMode) return
        val idx = editorView.getCursorIndex() + 1
        val total = document.paragraphs.size
        stylusStatus.text = "PARAGRAF $idx/$total | ${document.wordCount()} KATA | KEYBOARD AKTIF"
    }

    private fun setupModeToggle() {
        modeToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.modeText -> {
                    drawingMode = false
                    freeDrawView.visibility = View.GONE
                    etInput.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnNewParagraph)?.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnKeyboard)?.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.modeHint)?.text = "KETUK PARAGRAF → KETIK DI KEYBOARD"
                    updateStatus()
                }
                R.id.modeFreeDraw -> {
                    drawingMode = true
                    freeDrawView.visibility = View.VISIBLE
                    etInput.visibility = View.GONE
                    findViewById<View>(R.id.btnNewParagraph)?.visibility = View.GONE
                    findViewById<View>(R.id.btnKeyboard)?.visibility = View.GONE
                    findViewById<TextView>(R.id.modeHint)?.text = "GAMBAR BEBAS DI ATAS DOKUMEN"
                    stylusStatus.text = "MODE: GAMBAR | M-PENCIL / SENTUH UNTUK MENCORET"
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(etInput.windowToken, 0)
                }
            }
        }
        modeToggle.check(R.id.modeText)
    }

    // ---------- Toolbar ----------

    private fun tool(id: String, icon: Int, label: String) =
        com.retrobit.office.ui.ToolItem(id, icon, label)

    private fun cat(id: String, icon: Int, label: String, tools: List<com.retrobit.office.ui.ToolItem>) =
        com.retrobit.office.ui.ToolCategory(id, icon, label, tools)

    private fun setupWordToolbar() {
        sidebar.setCategories(listOf(
            cat("file", R.drawable.ic_pi_folder, "FILE", listOf(
                tool("doc_new", R.drawable.ic_pi_file_text, "Baru"),
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
                tool("delete_para", R.drawable.ic_pi_trash, "Hapus ¶"),
                tool("duplicate", R.drawable.ic_pi_files, "Duplikat")
            )),
            cat("font", R.drawable.ic_pi_text_start_t, "TEKS", listOf(
                tool("bold", R.drawable.ic_pi_bold, "Tebal"),
                tool("italic", R.drawable.ic_pi_italic, "Miring"),
                tool("underline", R.drawable.ic_pi_underline, "Garis"),
                tool("strike", R.drawable.ic_pi_strike, "Coret"),
                tool("sub", R.drawable.ic_pi_subscript, "Sub"),
                tool("super", R.drawable.ic_pi_superscript, "Super"),
                tool("color", R.drawable.ic_pi_colors_swatch, "Warna"),
                tool("highlight", R.drawable.ic_pi_highlight, "Stabilo"),
                tool("size_up", R.drawable.ic_pi_type_size, "A+"),
                tool("size_down", R.drawable.ic_pi_type_size, "A−"),
                tool("clear_format", R.drawable.ic_pi_magic_edit, "Reset")
            )),
            cat("para", R.drawable.ic_pi_text_align_left, "PARAGRAF", listOf(
                tool("align_l", R.drawable.ic_pi_text_align_left, "Kiri"),
                tool("align_c", R.drawable.ic_pi_text_align_center, "Tengah"),
                tool("align_r", R.drawable.ic_pi_text_align_right, "Kanan"),
                tool("align_j", R.drawable.ic_pi_text_align_justify, "Rata"),
                tool("bullet", R.drawable.ic_pi_bulletlist, "Poin"),
                tool("numbers", R.drawable.ic_pi_numbers, "Nomor"),
                tool("indent_inc", R.drawable.ic_pi_indent_inc, "Inden +"),
                tool("indent_dec", R.drawable.ic_pi_indent_dec, "Inden −"),
                tool("spacing", R.drawable.ic_pi_line_spacing, "Spasi"),
                tool("h1", R.drawable.ic_pi_heading_1, "H1"),
                tool("h2", R.drawable.ic_pi_heading_2, "H2"),
                tool("normal", R.drawable.ic_pi_type_size, "Normal")
            )),
            cat("insert", R.drawable.ic_pi_plus, "SISIPKAN", listOf(
                tool("table", R.drawable.ic_pi_table, "Tabel"),
                tool("image", R.drawable.ic_pi_image, "Gambar"),
                tool("date", R.drawable.ic_pi_calendar_text, "Tanggal")
            )),
            cat("layout", R.drawable.ic_pi_layout, "TATA LETAK", listOf(
                tool("paper", R.drawable.ic_pi_page, "Kertas"),
                tool("orientation", R.drawable.ic_pi_orientation, "Orientasi"),
                tool("margins", R.drawable.ic_pi_margin, "Margin")
            )),
            cat("view", R.drawable.ic_pi_eye, "TAMPILAN", listOf(
                tool("zoom_in", R.drawable.ic_pi_zoom_in, "Zoom +"),
                tool("zoom_out", R.drawable.ic_pi_zoom_out, "Zoom −"),
                tool("find", R.drawable.ic_pi_search, "Cari"),
                tool("wordcount", R.drawable.ic_pi_book_open, "Statistik"),
                tool("prev_para", R.drawable.ic_pi_arrow_up_box, "¶ Atas"),
                tool("next_para", R.drawable.ic_pi_arrow_down_box, "¶ Bawah")
            )),
            cat("draw", R.drawable.ic_pi_brush, "CORET", listOf(
                tool("pen", R.drawable.ic_pi_pencil, "Pena"),
                tool("eraser", R.drawable.ic_pi_eraser, "Hapus"),
                tool("undo_ink", R.drawable.ic_pi_undo, "Undo"),
                tool("clear_ink", R.drawable.ic_pi_trash, "Bersih")
            ))
        ))
        sidebar.onToolSelected = { _, toolId -> handleTool(toolId) }
    }

    private fun handleTool(toolId: String) {
        when (toolId) {
            // File
            "doc_new" -> confirmNewDocument()
            "open" -> pickDocx()
            "save" -> saveDocument()
            "print" -> printDocument()
            "share" -> shareDocument()
            // Edit
            "undo" -> undo()
            "redo" -> redo()
            "cut" -> cutParagraph()
            "copy" -> copyParagraph()
            "paste" -> pasteParagraph()
            "delete_para" -> { pushUndo(); editorView.deleteCurrentParagraph(); syncInputFromCursor() }
            "duplicate" -> { pushUndo(); editorView.duplicateCurrentParagraph(); syncInputFromCursor() }
            // Font
            "bold" -> toggleBold()
            "italic" -> toggleItalic()
            "underline" -> toggleUnderline()
            "strike" -> toggleStrike()
            "sub" -> toggleSub(superscript = false)
            "super" -> toggleSub(superscript = true)
            "color" -> showFontColorDialog()
            "highlight" -> showHighlightDialog()
            "size_up" -> changeSize(1f)
            "size_down" -> changeSize(-1f)
            "clear_format" -> { pushUndo(); editorView.clearFormatting(); syncInputFromCursor() }
            // Paragraph
            "align_l" -> format { applyFormatting(alignment = Alignment.LEFT) }
            "align_c" -> format { applyFormatting(alignment = Alignment.CENTER) }
            "align_r" -> format { applyFormatting(alignment = Alignment.RIGHT) }
            "align_j" -> format { applyFormatting(alignment = Alignment.JUSTIFY) }
            "bullet" -> { pushUndo(); editorView.setBullet(BulletStyle.BULLET) }
            "numbers" -> { pushUndo(); editorView.setBullet(BulletStyle.NUMBER) }
            "indent_inc" -> { pushUndo(); editorView.setIndent(18f) }
            "indent_dec" -> { pushUndo(); editorView.setIndent(-18f) }
            "spacing" -> showSpacingDialog()
            "h1" -> { pushUndo(); editorView.setStyleName("Heading 1"); syncInputFromCursor() }
            "h2" -> { pushUndo(); editorView.setStyleName("Heading 2"); syncInputFromCursor() }
            "normal" -> { pushUndo(); editorView.setStyleName("Normal"); syncInputFromCursor() }
            // Insert
            "table" -> insertTableDialog()
            "image" -> pickImage()
            "date" -> insertDate()
            // Layout
            "paper" -> showPaperSizeDialog()
            "orientation" -> toggleOrientation()
            "margins" -> showMarginsDialog()
            // View
            "zoom_in" -> editorView.setZoom(editorView.getZoom() * 1.15f)
            "zoom_out" -> editorView.setZoom(editorView.getZoom() / 1.15f)
            "find" -> showFindDialog()
            "wordcount" -> showWordCount()
            "prev_para" -> moveSelection(-1)
            "next_para" -> moveSelection(1)
            // Ink
            "pen" -> {
                modeToggle.check(R.id.modeFreeDraw)
                freeDrawView.setTool(StylusTool.PEN)
            }
            "eraser" -> {
                modeToggle.check(R.id.modeFreeDraw)
                freeDrawView.setTool(StylusTool.ERASER)
            }
            "undo_ink" -> freeDrawView.undoLast()
            "clear_ink" -> freeDrawView.clearCanvas()
        }
    }

    private fun format(block: WordEditorView.() -> Unit) {
        pushUndo()
        editorView.block()
        syncInputFromCursor()
    }

    private fun toggleBold() {
        val on = editorView.currentRunStyle()?.bold == true
        pushUndo(); editorView.applyFormatting(bold = !on)
    }

    private fun toggleItalic() {
        val on = editorView.currentRunStyle()?.italic == true
        pushUndo(); editorView.applyFormatting(italic = !on)
    }

    private fun toggleUnderline() {
        val on = editorView.currentRunStyle()?.underline == true
        pushUndo(); editorView.applyFormatting(underline = !on)
    }

    private fun toggleStrike() {
        val on = editorView.currentRunStyle()?.strikethrough == true
        pushUndo(); editorView.applyFormatting(strikethrough = !on)
    }

    private fun toggleSub(superscript: Boolean) {
        val run = editorView.currentRunStyle()
        val active = if (superscript) run?.superscript == true else run?.subscript == true
        pushUndo()
        if (superscript) {
            editorView.applyFormatting(superscript = !active, subscript = false)
        } else {
            editorView.applyFormatting(subscript = !active, superscript = false)
        }
    }

    private fun changeSize(delta: Float) {
        val current = editorView.currentRunStyle()?.fontSizePt ?: 12f
        val next = (current + delta).coerceIn(6f, 72f)
        pushUndo()
        editorView.applyFormatting(fontSize = next)
        Toast.makeText(this, "Ukuran: ${next.toInt()} pt", Toast.LENGTH_SHORT).show()
    }

    private fun moveSelection(delta: Int) {
        val target = (editorView.getCursorIndex() + delta).coerceIn(0, document.paragraphs.size - 1)
        editorView.selectParagraph(target)
        docScroll.smoothScrollTo(0, editorView.paragraphTopOf(target))
    }

    // ---------- Clipboard ----------

    private fun clipboard(): ClipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun copyParagraph() {
        val text = editorView.currentParagraph()?.runs?.joinToString("") { it.text } ?: ""
        clipboard().setPrimaryClip(ClipData.newPlainText("RetroBit", text))
        Toast.makeText(this, "Paragraf disalin", Toast.LENGTH_SHORT).show()
    }

    private fun cutParagraph() {
        copyParagraph()
        pushUndo()
        editorView.deleteCurrentParagraph()
        syncInputFromCursor()
    }

    private fun pasteParagraph() {
        val clip = clipboard().primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (clip.isBlank()) {
            Toast.makeText(this, "Clipboard kosong", Toast.LENGTH_SHORT).show()
            return
        }
        pushUndo()
        editorView.insertParagraphAfter(clip)
        syncInputFromCursor()
    }

    // ---------- Dialogs ----------

    private fun confirmNewDocument() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Dokumen baru")
            .setMessage("Dokumen saat ini akan diganti. Simpan dulu jika perlu.")
            .setPositiveButton("Buat") { _, _ ->
                pushUndo()
                val doc = WordDocument()
                doc.addParagraph("Dokumen Baru", ParagraphStyle(styleName = "Title"))
                doc.addParagraph("", ParagraphStyle(styleName = "Normal"))
                document = doc
                editorView.bindDocument(doc)
                editorView.setCursorToParagraph(1)
                syncInputFromCursor()
                currentFile = null
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showFontColorDialog() {
        val names = arrayOf("Hitam", "Biru", "Merah Tua", "Hijau Tua", "Abu")
        val colors = intArrayOf(
            Color.parseColor("#1B1B18"), Color.parseColor("#2E4A7A"),
            Color.parseColor("#8B3A2E"), Color.parseColor("#4F5F46"),
            Color.parseColor("#56564E")
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Warna teks")
            .setItems(names) { _, which ->
                pushUndo()
                editorView.applyFormatting(color = colors[which])
            }
            .show()
    }

    private fun showHighlightDialog() {
        val names = arrayOf("Kuning", "Hijau", "Biru", "Merah", "Hapus stabilo")
        val colors = intArrayOf(
            Color.parseColor("#F3E27A"), Color.parseColor("#B9D9A8"),
            Color.parseColor("#B7CFE8"), Color.parseColor("#E8B7B0"),
            Color.TRANSPARENT
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Stabilo")
            .setItems(names) { _, which ->
                pushUndo()
                if (which == 4) editorView.applyFormatting(clearHighlight = true)
                else editorView.applyFormatting(highlight = colors[which])
            }
            .show()
    }

    private fun showSpacingDialog() {
        val names = arrayOf("Rapat (1.0)", "Normal (1.15)", "1.5", "Ganda (2.0)")
        val values = floatArrayOf(1.0f, 1.15f, 1.5f, 2.0f)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Spasi baris")
            .setItems(names) { _, which ->
                pushUndo()
                editorView.setLineSpacing(values[which])
            }
            .show()
    }

    private fun showFindDialog() {
        val input = EditText(this).apply { hint = "Kata yang dicari"; setSingleLine() }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cari teks")
            .setView(input)
            .setPositiveButton("Cari") { _, _ ->
                val q = input.text.toString()
                val idx = editorView.findNext(q, editorView.getCursorIndex() + 1)
                if (idx >= 0) {
                    syncInputFromCursor()
                    docScroll.smoothScrollTo(0, editorView.paragraphTopOf(idx))
                    Toast.makeText(this, "Ditemukan di paragraf ${idx + 1}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun insertDate() {
        val text = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date())
        pushUndo()
        editorView.insertParagraphAfter(text)
        syncInputFromCursor()
    }

    private fun insertTableDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val rowsInput = EditText(this).apply {
            hint = "Jumlah baris (mis. 3)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        val colsInput = EditText(this).apply {
            hint = "Jumlah kolom (mis. 3)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        layout.addView(rowsInput)
        layout.addView(colsInput)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sisipkan Tabel")
            .setView(layout)
            .setPositiveButton("Sisipkan") { _, _ ->
                val rows = rowsInput.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 3
                val cols = colsInput.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 3
                pushUndo()
                document.tables.add(DocTable(rows, cols))
                editorView.bindDocument(document)
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
            Toast.makeText(this, "Tidak ada aplikasi galeri", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickDocx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }
        try {
            startActivityForResult(intent, REQ_OPEN_DOCX)
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada pengelola berkas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK_IMAGE -> try {
                val input = contentResolver.openInputStream(uri) ?: return
                val raw = android.graphics.BitmapFactory.decodeStream(input)
                input.close()
                if (raw == null) return
                pushUndo()
                val scaled = if (raw.width > 1280) {
                    val h = (1280f * raw.height / raw.width).toInt()
                    android.graphics.Bitmap.createScaledBitmap(raw, 1280, h, true)
                } else raw
                document.images.add(DocImage(scaled))
                editorView.bindDocument(document)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            REQ_OPEN_DOCX -> try {
                val input = contentResolver.openInputStream(uri) ?: return
                val tmp = File(cacheDir, "import_${System.currentTimeMillis()}.docx")
                tmp.outputStream().use { out -> input.copyTo(out) }
                input.close()
                loadDocx(tmp)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal buka DOCX: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadDocx(file: File) {
        if (!file.exists()) return
        try {
            val imported = DocxImporter().import(file)
            document = imported
            currentFile = file
            supportActionBar?.title = file.name
            if (::editorView.isInitialized) {
                editorView.bindDocument(document)
                editorView.setCursorToParagraph(0)
                syncInputFromCursor()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal buka file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleOrientation() {
        pushUndo()
        val ps = document.pageSettings
        ps.orientation = if (ps.orientation == Orientation.PORTRAIT) Orientation.LANDSCAPE else Orientation.PORTRAIT
        editorView.bindDocument(document)
        Toast.makeText(this, "Orientasi: ${ps.orientation}", Toast.LENGTH_SHORT).show()
    }

    private fun showPaperSizeDialog() {
        val sizes = PaperSize.entries.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Ukuran kertas")
            .setItems(sizes) { _, which ->
                pushUndo()
                document.pageSettings.paperSize = PaperSize.entries[which]
                editorView.bindDocument(document)
            }
            .show()
    }

    private fun showMarginsDialog() {
        val opts = arrayOf("Sempit (12 mm)", "Normal (25 mm)", "Lebar (38 mm)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Margin halaman")
            .setItems(opts) { _, which ->
                pushUndo()
                val mm = when (which) { 0 -> 12f; 1 -> 25.4f; else -> 38f }
                val ps = document.pageSettings
                ps.marginTopMm = mm; ps.marginBottomMm = mm
                ps.marginLeftMm = mm; ps.marginRightMm = mm
                editorView.bindDocument(document)
            }
            .show()
    }

    private fun showWordCount() {
        val msg = "Kata: ${document.wordCount()}\nKarakter: ${document.charCount()}\n" +
            "Paragraf: ${document.paragraphs.size}\nTabel: ${document.tables.size}\nGambar: ${document.images.size}"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Statistik dokumen")
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ---------- Undo / Redo ----------

    private fun snapshot(): WordDocument = document.copy(
        paragraphs = document.paragraphs.map {
            it.copy(runs = it.runs.map { r -> r.copy() }.toMutableList(), style = it.style.copy())
        }.toMutableList(),
        pageSettings = document.pageSettings.copy(),
        tables = document.tables.map { t ->
            t.copy(cells = t.cells.map { row -> row.toMutableList() }.toMutableList())
        }.toMutableList(),
        images = document.images.toMutableList(),
        headers = document.headers.toMutableList(),
        footers = document.footers.toMutableList(),
        inkStrokes = document.inkStrokes.toMutableList()
    )

    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Tidak ada yang bisa diurungkan", Toast.LENGTH_SHORT).show()
            return
        }
        redoStack.addLast(snapshot())
        document = undoStack.removeLast()
        editorView.bindDocument(document)
        syncInputFromCursor()
        Toast.makeText(this, "Undo", Toast.LENGTH_SHORT).show()
    }

    private fun redo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Tidak ada yang bisa diulangi", Toast.LENGTH_SHORT).show()
            return
        }
        undoStack.addLast(snapshot())
        document = redoStack.removeLast()
        editorView.bindDocument(document)
        syncInputFromCursor()
        Toast.makeText(this, "Redo", Toast.LENGTH_SHORT).show()
    }

    // ---------- File ----------

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabSave)?.setOnClickListener { saveDocument() }
        findViewById<FloatingActionButton>(R.id.fabShare)?.setOnClickListener { shareDocument() }
    }

    private fun saveDocument() {
        val fileName = currentFile?.name ?: "Dokumen_${System.currentTimeMillis()}.docx"
        val outFile = currentFile ?: File(getExternalFilesDir(null), fileName)
        try {
            // Selalu ekspor DOCX native agar seluruh format teks/paragraf tersimpan.
            exporter.export(document, outFile)
            currentFile = outFile
            val msg = if (freeDrawView.getStrokeCount() > 0)
                "Tersimpan: ${outFile.name} (anotasi tinta hanya di layar)"
            else "Tersimpan: ${outFile.name}"
            Snackbar.make(editorView, msg, Snackbar.LENGTH_LONG)
                .setAction("Buka Folder") { openFileLocation(outFile) }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
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
        val html = exporter.exportHtmlPreview(document)
        printHelper.printHtml("RetroBit_Word_${document.title}", html)
    }

    private fun openFileLocation(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, file.absolutePath, Toast.LENGTH_LONG).show()
        }
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
            R.id.action_share -> { shareDocument(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
