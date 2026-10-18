package com.retrobit.office.word

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.retrobit.office.core.stylus.FreeDrawView
import com.retrobit.office.core.stylus.HandwritingView
import com.retrobit.office.core.stylus.InputMode

/**
 * Word Editor View - Hybrid:
 * - Text layout (EditText-like) for typed/handwritten text
 * - FreeDraw overlay for annotations (tulisan bebas)
 * Huawei MatePad Mini optimized: 165Hz rendering, stylus-first
 */
class WordEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var document = WordDocument()
    var onDocumentChanged: ((WordDocument) -> Unit)? = null

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 16 * resources.displayMetrics.density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val pagePaint = Paint().apply {
        color = Color.WHITE
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }

    private val marginPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 2f
    }

    private var cursorPosition = 0 // index of paragraph
    private var isInkMode = false // toggle between text ink and free draw

    // Page dimensions in px (A4 at 96dpi approx)
    private val pagePadding = 32f
    private var pageRect = RectF()

    fun setDocument(doc: WordDocument) {
        document = doc
        invalidate()
        requestLayout()
    }

    fun setInkMode(enabled: Boolean) {
        isInkMode = enabled
        invalidate()
    }

    fun insertTextAtCursor(text: String) {
        if (document.paragraphs.isEmpty()) {
            document.addParagraph(text)
        } else {
            val para = document.paragraphs[cursorPosition.coerceIn(document.paragraphs.indices)]
            para.text += if (para.text.isEmpty()) text else " $text"
            // Add as run for formatting preservation
            para.runs.add(TextRun(text))
        }
        onDocumentChanged?.invoke(document)
        invalidate()
    }

    fun insertHandwritingResult(text: String) {
        insertTextAtCursor(text)
    }

    fun applyFormatting(bold: Boolean? = null, italic: Boolean? = null, underline: Boolean? = null, alignment: Alignment? = null, color: Int? = null, fontSize: Float? = null) {
        if (document.paragraphs.isEmpty()) return
        val para = document.paragraphs[cursorPosition.coerceIn(document.paragraphs.indices)]
        if (para.runs.isEmpty()) {
            para.runs.add(TextRun(para.text))
        }
        para.runs.forEach { run ->
            bold?.let { run.bold = it }
            italic?.let { run.italic = it }
            underline?.let { run.underline = it }
            color?.let { run.color = it }
            fontSize?.let { run.fontSizePt = it }
        }
        alignment?.let { para.style.alignment = it }
        onDocumentChanged?.invoke(document)
        invalidate()
    }

    fun setCursorToParagraph(index: Int) {
        cursorPosition = index.coerceIn(0, (document.paragraphs.size - 1).coerceAtLeast(0))
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Height based on content (pagination)
        val contentHeight = calculateContentHeight(w)
        val h = contentHeight + paddingTop + paddingBottom + 100
        setMeasuredDimension(w, h)
    }

    private fun calculateContentHeight(width: Int): Int {
        if (document.paragraphs.isEmpty()) return (400 * resources.displayMetrics.density).toInt()
        var height = 100
        document.paragraphs.forEach { para ->
            height += (60 * resources.displayMetrics.density).toInt() // approx per para
        }
        // Simulate pages
        val pageHeightPx = (297 * 3.78f) // A4 mm to px ~ 1123
        val pages = (height / pageHeightPx).toInt() + 1
        return (pages * pageHeightPx).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val pageLeft = pagePadding
        val pageTop = pagePadding
        val pageRight = w - pagePadding
        val availableWidth = pageRight - pageLeft - 40f

        pageRect.set(pageLeft, pageTop, pageRight, height - pagePadding)

        // Draw page background with shadow
        canvas.drawRoundRect(pageRect, 12f, 12f, pagePaint)

        // Clip to page
        canvas.save()
        canvas.clipRect(pageRect)

        var y = pageTop + 40f
        val marginLeft = 48f
        val marginRight = 48f

        // Draw paragraphs
        document.paragraphs.forEachIndexed { index, para ->
            val isSelected = index == cursorPosition
            if (isSelected) {
                // Highlight selected paragraph
                val hlPaint = Paint().apply { color = Color.parseColor("#E3F2FD") }
                canvas.drawRect(pageLeft + 12f, y - 8f, pageRight - 12f, y + 50f, hlPaint)
                // Cursor
                canvas.drawLine(pageLeft + marginLeft, y - 6f, pageLeft + marginLeft, y + 38f, cursorPaint)
            }

            // Build styled text - simplified
            val displayText = if (para.runs.isNotEmpty()) {
                para.runs.joinToString("") { it.text }
            } else para.text.ifEmpty { "Ketik atau tulis dengan stylus..." }

            // Choose typeface based on first run
            val firstRun = para.runs.firstOrNull()
            textPaint.typeface = Typeface.create(
                firstRun?.fontName ?: "sans-serif",
                when {
                    firstRun?.bold == true && firstRun.italic -> Typeface.BOLD_ITALIC
                    firstRun?.bold == true -> Typeface.BOLD
                    firstRun?.italic == true -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
            )
            textPaint.textSize = (firstRun?.fontSizePt ?: 11f) * resources.displayMetrics.density * 0.9f
            textPaint.color = firstRun?.color ?: Color.BLACK
            textPaint.isUnderlineText = firstRun?.underline == true
            textPaint.isStrikeThruText = firstRun?.strikethrough == true

            val alignment = when (para.style.alignment) {
                Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                Alignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                Alignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL // Simplified
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, (availableWidth - marginLeft - marginRight).toInt())
                .setAlignment(alignment)
                .setLineSpacing(0f, para.style.lineSpacing)
                .setIncludePad(true)
                .build()

            canvas.save()
            // X depends on alignment
            val x = when (para.style.alignment) {
                Alignment.CENTER -> pageLeft + availableWidth / 2 - layout.width / 2
                Alignment.RIGHT -> pageRight - marginRight - layout.width
                else -> pageLeft + marginLeft
            }
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()

            y += layout.height + para.style.spaceAfterPt * 2f + 12f

            // Bullet
            para.bullet?.let { bullet ->
                val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f }
                val bulletText = if (bullet == BulletStyle.BULLET) "•" else "${index + 1}."
                canvas.drawText(bulletText, pageLeft + marginLeft - 20f, y - layout.height + 14f, bulletPaint)
            }

            // Draw page break hint every ~1000px
            if (y > pageTop + 1000 && (y - pageTop) % 1000 < 60) {
                val breakPaint = Paint().apply { color = Color.parseColor("#BDBDBD"); strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(10f,10f),0f) }
                canvas.drawLine(pageLeft, y, pageRight, y, breakPaint)
                val labelPaint = Paint().apply { color = Color.GRAY; textSize = 10f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
                canvas.drawText("— Halaman ${((y - pageTop)/1000).toInt() + 1} —", w/2, y - 8f, labelPaint)
            }
        }

        // Empty state
        if (document.paragraphs.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 14f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER; isFakeBoldText = false }
            canvas.drawText("Ketuk untuk mengetik", w/2, pageTop + 120f, hintPaint)
            canvas.drawText("atau tulis dengan Huawei M-Pencil", w/2, pageTop + 150f, hintPaint)
            // Show stylus icon hint
        }

        canvas.restore()

        // Draw page info footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 11f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
        canvas.drawText("Halaman 1  •  ${document.wordCount()} kata  •  ${document.charCount()} karakter", w/2, height - 20f, footerPaint)
    }

    fun getBitmapForExport(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F5F5F5"))
        draw(c)
        return bmp
    }
}
