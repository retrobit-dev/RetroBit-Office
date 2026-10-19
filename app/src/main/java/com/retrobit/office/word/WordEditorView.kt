package com.retrobit.office.word

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Word Editor View — editor teks keyboard-first.
 * - Ketuk paragraf untuk memilih (keyboard muncul lewat input bar di Activity)
 * - Render kaya: bold/italic/underline/strike, sub/superscript, warna, stabilo
 * - Lapisan tinta bebas ditangani FreeDrawView terpisah.
 */
class WordEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var document = WordDocument()
    var onDocumentChanged: ((WordDocument) -> Unit)? = null
    var onParagraphSelected: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 12f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val pagePaint = Paint().apply {
        color = Color.WHITE
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }

    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#1B1B18")
        strokeWidth = 2f * density
    }

    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#E7E7DC")
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A8E")
        textSize = 12f * density
    }

    private var cursorPosition = 0
    private var zoom = 1f
    private var isInkMode = false

    private val pagePadding = 20f * density
    private val marginSide = 26f * density

    private val paragraphTops = mutableListOf<Float>()
    private val paragraphHeights = mutableListOf<Float>()

    // ---------- Public API ----------

    fun setZoom(z: Float) {
        zoom = z.coerceIn(0.5f, 2.5f)
        requestLayout()
        invalidate()
    }

    fun getZoom(): Float = zoom

    fun bindDocument(doc: WordDocument) {
        document = doc
        cursorPosition = cursorPosition.coerceIn(0, (doc.paragraphs.size - 1).coerceAtLeast(0))
        requestLayout()
        invalidate()
    }

    fun setInkMode(enabled: Boolean) {
        isInkMode = enabled
        invalidate()
    }

    fun getCursorIndex(): Int = cursorPosition

    fun currentParagraph(): Paragraph? = document.paragraphs.getOrNull(cursorPosition)

    /** Gaya run pertama paragraf aktif — dipakai untuk toggle toolbar. */
    fun currentRunStyle(): TextRun? {
        val p = currentParagraph() ?: return null
        return p.runs.firstOrNull() ?: TextRun(p.text)
    }

    fun setCursorToParagraph(index: Int) {
        cursorPosition = index.coerceIn(0, (document.paragraphs.size - 1).coerceAtLeast(0))
        invalidate()
    }

    fun selectParagraph(index: Int) {
        if (index in document.paragraphs.indices && index != cursorPosition) {
            cursorPosition = index
            invalidate()
            onParagraphSelected?.invoke(index)
        } else if (index in document.paragraphs.indices) {
            onParagraphSelected?.invoke(index)
        }
    }

    /** Ganti isi paragraf aktif tanpa menghapus gaya run pertama. */
    fun setParagraphText(text: String) {
        val para = currentParagraph() ?: return
        val style = para.runs.firstOrNull()
        if (para.runs.isEmpty()) {
            para.runs.add(TextRun(text))
        } else if (para.runs.size == 1) {
            para.runs[0].text = text
        } else {
            val first = para.runs[0]
            first.text = text
            while (para.runs.size > 1) para.runs.removeAt(para.runs.size - 1)
        }
        para.text = text
        if (style == null && text.isNotEmpty()) para.runs.first().text = text
        requestLayout()
        invalidate()
    }

    fun insertTextAtCursor(text: String) {
        val para = currentParagraph() ?: run {
            document.addParagraph(text)
            cursorPosition = document.paragraphs.lastIndex
            notifyChanged()
            return
        }
        para.text += text
        if (para.runs.isEmpty()) para.runs.add(TextRun(text)) else para.runs.last().text += text
        notifyChanged()
    }

    fun insertParagraphAfter(text: String = ""): Int {
        val style = currentParagraph()?.style?.copy() ?: ParagraphStyle()
        val template = currentParagraph()?.runs?.firstOrNull()
        val baseSize = when (style.styleName) {
            "Title" -> 28f
            "Heading 1" -> 22f
            "Heading 2" -> 17f
            else -> 12f
        }
        val run = if (template != null) template.copy(text = text, bold = false, italic = false,
            underline = false, strikethrough = false, subscript = false, superscript = false,
            highlightColor = null, fontSizePt = baseSize) else TextRun(text, fontSizePt = baseSize)
        val para = Paragraph(text = text, style = style, runs = mutableListOf(run))
        val at = (cursorPosition + 1).coerceIn(0, document.paragraphs.size)
        document.paragraphs.add(at, para)
        cursorPosition = at
        notifyChanged()
        onParagraphSelected?.invoke(at)
        return at
    }

    fun deleteCurrentParagraph(): Boolean {
        if (document.paragraphs.size <= 1) {
            setParagraphText("")
            return false
        }
        val at = cursorPosition.coerceIn(document.paragraphs.indices)
        document.paragraphs.removeAt(at)
        cursorPosition = (at - 1).coerceAtLeast(0)
        notifyChanged()
        onParagraphSelected?.invoke(cursorPosition)
        return true
    }

    fun duplicateCurrentParagraph(): Boolean {
        val para = currentParagraph() ?: return false
        val copy = para.copy(
            style = para.style.copy(),
            runs = para.runs.map { it.copy() }.toMutableList(),
            bullet = para.bullet
        )
        val at = cursorPosition + 1
        document.paragraphs.add(at, copy)
        cursorPosition = at
        notifyChanged()
        return true
    }

    fun moveCurrentParagraph(delta: Int): Boolean {
        val at = cursorPosition
        val to = at + delta
        if (at !in document.paragraphs.indices || to !in document.paragraphs.indices) return false
        val p = document.paragraphs.removeAt(at)
        document.paragraphs.add(to, p)
        cursorPosition = to
        notifyChanged()
        return true
    }

    fun applyFormatting(
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        strikethrough: Boolean? = null,
        subscript: Boolean? = null,
        superscript: Boolean? = null,
        alignment: Alignment? = null,
        color: Int? = null,
        highlight: Int? = null,
        clearHighlight: Boolean = false,
        fontSize: Float? = null,
        fontName: String? = null
    ) {
        val para = currentParagraph() ?: return
        if (para.runs.isEmpty()) para.runs.add(TextRun(para.text))
        para.runs.forEach { run ->
            bold?.let { run.bold = it }
            italic?.let { run.italic = it }
            underline?.let { run.underline = it }
            strikethrough?.let { run.strikethrough = it }
            subscript?.let { run.subscript = it; if (it) run.superscript = false }
            superscript?.let { run.superscript = it; if (it) run.subscript = false }
            color?.let { run.color = it }
            highlight?.let { run.highlightColor = it }
            if (clearHighlight) run.highlightColor = null
            fontSize?.let { run.fontSizePt = it }
            fontName?.let { run.fontName = it }
        }
        alignment?.let { para.style.alignment = it }
        notifyChanged()
    }

    fun setBullet(style: BulletStyle?) {
        val para = currentParagraph() ?: return
        para.bullet = if (para.bullet == style) null else style
        notifyChanged()
    }

    fun setIndent(deltaPt: Float) {
        val para = currentParagraph() ?: return
        para.style.indentLeftPt = (para.style.indentLeftPt + deltaPt).coerceIn(0f, 144f)
        notifyChanged()
    }

    fun setLineSpacing(value: Float) {
        val para = currentParagraph() ?: return
        para.style.lineSpacing = value.coerceIn(0.8f, 3f)
        notifyChanged()
    }

    fun setStyleName(name: String) {
        val para = currentParagraph() ?: return
        para.style.styleName = name
        when (name) {
            "Heading 1" -> para.runs.forEach { it.fontSizePt = 22f; it.bold = true }
            "Heading 2" -> para.runs.forEach { it.fontSizePt = 17f; it.bold = true }
            "Title" -> para.runs.forEach { it.fontSizePt = 28f; it.bold = true }
            else -> para.runs.forEach { if (it.fontSizePt > 14f) it.fontSizePt = 12f; it.bold = false }
        }
        notifyChanged()
    }

    fun clearFormatting() {
        val para = currentParagraph() ?: return
        val text = para.runs.joinToString("") { it.text }
        para.runs.clear()
        para.runs.add(TextRun(text))
        para.style = ParagraphStyle(styleName = para.style.styleName)
        notifyChanged()
    }

    fun findNext(query: String, from: Int = 0): Int {
        if (query.isBlank()) return -1
        val list = document.paragraphs
        for (offset in 0 until list.size) {
            val idx = (from + offset) % list.size
            val text = list[idx].runs.joinToString("") { it.text }.ifEmpty { list[idx].text }
            if (text.contains(query, ignoreCase = true)) {
                selectParagraph(idx)
                return idx
            }
        }
        return -1
    }

    fun paragraphTopOf(index: Int): Int =
        ((paragraphTops.getOrNull(index) ?: 0f) * zoom + paddingTop).toInt()

    fun getBitmapForExport(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F5F5F5"))
        draw(c)
        return bmp
    }

    private fun notifyChanged() {
        onDocumentChanged?.invoke(document)
        requestLayout()
        invalidate()
    }

    // ---------- Measurement ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val layouts = buildLayouts(w.toFloat())
        var content = 0f
        layouts.forEach { content += it.layout.height + it.para.style.spaceAfterPt * density + 10f * density }
        content += document.tables.size * 120f * density + document.images.size * 160f * density
        val h = (content * zoom + paddingTop + paddingBottom + 80f * density).toInt()
        setMeasuredDimension(w, h.coerceAtLeast((480 * density).toInt()))
    }

    private data class ParaLayout(
        val index: Int,
        val para: Paragraph,
        val layout: StaticLayout,
        val x: Float,
        val y: Float
    )

    private fun contentWidth(viewWidth: Float): Float =
        (viewWidth - pagePadding * 2 - marginSide * 2).coerceAtLeast(80f * density)

    private fun buildLayouts(viewWidth: Float): List<ParaLayout> {
        val out = mutableListOf<ParaLayout>()
        val avail = contentWidth(viewWidth)
        var y = pagePadding + 26f * density
        document.paragraphs.forEachIndexed { index, para ->
            val spanned = buildSpanned(para)
            val alignment = when (para.style.alignment) {
                Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                Alignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val indent = para.style.indentLeftPt * density
            val layout = StaticLayout.Builder
                .obtain(spanned, 0, spanned.length, textPaint, (avail - indent).toInt().coerceAtLeast(40))
                .setAlignment(alignment)
                .setLineSpacing(0f, para.style.lineSpacing)
                .setIncludePad(true)
                .build()
            val x = pagePadding + marginSide + indent
            out.add(ParaLayout(index, para, layout, x, y))
            y += layout.height + para.style.spaceAfterPt * density + 10f * density
        }
        return out
    }

    private fun buildSpanned(para: Paragraph): CharSequence {
        if (para.runs.isEmpty()) return para.text
        val sb = SpannableStringBuilder()
        para.runs.forEach { run ->
            val start = sb.length
            sb.append(run.text)
            val end = sb.length
            if (end <= start) return@forEach
            if (run.bold && run.italic) sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, 0)
            else if (run.bold) sb.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
            else if (run.italic) sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
            if (run.underline) sb.setSpan(UnderlineSpan(), start, end, 0)
            if (run.strikethrough) sb.setSpan(StrikethroughSpan(), start, end, 0)
            if (run.color != Color.BLACK) sb.setSpan(ForegroundColorSpan(run.color), start, end, 0)
            run.highlightColor?.let { sb.setSpan(BackgroundColorSpan(it), start, end, 0) }
            if (run.superscript) sb.setSpan(SuperscriptSpan(), start, end, 0)
            if (run.subscript) sb.setSpan(SubscriptSpan(), start, end, 0)
            val ratio = (run.fontSizePt / 12f).coerceIn(0.5f, 3f)
            if (ratio != 1f) sb.setSpan(RelativeSizeSpan(ratio), start, end, 0)
        }
        return sb
    }

    // ---------- Touch: pilih paragraf ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInkMode) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val y = (event.y - paddingTop) / zoom
            if (document.paragraphs.isEmpty()) {
                selectParagraph(0)
                performClick()
                return true
            }
            var best = 0
            var bestDist = Float.MAX_VALUE
            paragraphTops.forEachIndexed { i, top ->
                val h = paragraphHeights.getOrNull(i) ?: 40f * density
                val center = top + h / 2f
                val dist = kotlin.math.abs(center - y)
                if (dist < bestDist) { bestDist = dist; best = i }
            }
            selectParagraph(best)
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------- Draw ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(zoom, zoom)

        val w = width / zoom
        val pageRect = RectF(pagePadding, pagePadding,
            w - pagePadding, height / zoom - pagePadding)

        canvas.drawRoundRect(pageRect, 8f, 8f, pagePaint)
        canvas.save()
        canvas.clipRect(pageRect)

        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.isUnderlineText = false
        textPaint.isStrikeThruText = false

        val layouts = buildLayouts(w)
        paragraphTops.clear()
        paragraphHeights.clear()
        layouts.forEach {
            paragraphTops.add(it.y)
            paragraphHeights.add(it.layout.height.toFloat())
        }

        // Sorotan paragraf aktif
        layouts.getOrNull(cursorPosition)?.let { pl ->
            canvas.drawRect(pagePadding + 6f, pl.y - 4f * density,
                w - pagePadding - 6f, pl.y + pl.layout.height + 2f * density, selectionPaint)
            canvas.drawRect(pl.x - 6f * density, pl.y,
                pl.x - 4f * density, pl.y + pl.layout.height, cursorPaint)
        }

        layouts.forEach { pl ->
            val para = pl.para
            canvas.save()
            canvas.translate(pl.x, pl.y)
            pl.layout.draw(canvas)
            canvas.restore()

            para.bullet?.let { bullet ->
                val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1B1B18")
                    textSize = 12f * density
                }
                val bulletText = if (bullet == BulletStyle.BULLET) "•" else "${pl.index + 1}."
                canvas.drawText(bulletText, pagePadding + marginSide - 16f * density,
                    pl.y + 12f * density, bulletPaint)
            }

            if (para.runs.joinToString("") { it.text }.isEmpty() && pl.index == cursorPosition) {
                canvas.drawText("Ketik di keyboard…", pl.x, pl.y + 13f * density, placeholderPaint)
            }
        }

        var y = (layouts.lastOrNull()?.let { it.y + it.layout.height } ?: (pagePadding + 40f)) + 24f * density

        // Tabel
        document.tables.forEach { tbl ->
            val colW = contentWidth(w) / tbl.cols.coerceAtLeast(1)
            val rowH = 30f * density
            val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f * density; color = Color.BLACK
            }
            val bPaint = Paint().apply {
                color = Color.parseColor("#BFBFB2"); style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
            }
            for (r in 0 until tbl.rows) {
                for (c in 0 until tbl.cols) {
                    val cx = pagePadding + marginSide + c * colW
                    val cy = y + r * rowH
                    canvas.drawRect(cx, cy, cx + colW, cy + rowH, bPaint)
                    val txt = tbl.cells.getOrNull(r)?.getOrNull(c) ?: ""
                    if (txt.isNotEmpty()) canvas.drawText(txt.take(26), cx + 6f * density,
                        cy + rowH / 2f + 4f * density, tPaint)
                }
            }
            y += tbl.rows * rowH + 14f * density
        }

        // Gambar
        document.images.forEach { img ->
            val iw = contentWidth(w) * img.widthRatio
            val scale = iw / img.bitmap.width
            val ih = img.bitmap.height * scale
            val dst = RectF(pagePadding + marginSide, y, pagePadding + marginSide + iw, y + ih)
            canvas.drawBitmap(img.bitmap, null, dst, null)
            y += ih + 14f * density
        }

        if (document.paragraphs.isEmpty()) {
            canvas.drawText("Ketuk untuk mulai mengetik", pagePadding + marginSide,
                pagePadding + 80f * density, placeholderPaint)
        }

        canvas.restore()
        canvas.restore()

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E8E82")
            textSize = 9f * density
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${document.wordCount()} kata  •  ${document.charCount()} karakter  •  ${document.paragraphs.size} paragraf",
            width / 2f, height - 8f * density, footerPaint)
    }
}
