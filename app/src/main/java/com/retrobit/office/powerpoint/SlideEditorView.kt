package com.retrobit.office.powerpoint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Slide Editor View - Single slide canvas 16:9 with drag & edit + stylus ink overlay
 */
class SlideEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var slide: Slide = Slide()
        set(value) {
            field = value
            invalidate()
        }

    var onElementSelected: ((SlideElement?) -> Unit)? = null
    var onTextEditRequested: ((SlideElement.TextBox) -> Unit)? = null

    private var selectedElement: SlideElement? = null
    private val imageCache = HashMap<String, Bitmap>()
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var isDragging = false
    private var dragMoved = false

    private val bgPaint = Paint()
    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint().apply { color = Color.parseColor("#1A73E8"); style = Paint.Style.STROKE; strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f) }
    private val handlePaint = Paint().apply { color = Color.parseColor("#1A73E8") }

    private var slideRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 9f / 16f).toInt() // 16:9
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Slide background with shadow
        val shadowPaint = Paint().apply { color = Color.parseColor("#30000000"); setShadowLayer(16f, 0f, 8f, Color.parseColor("#40000000")) }
        slideRect.set(8f, 8f, w - 8f, h - 8f)
        canvas.drawRoundRect(slideRect, 16f, 16f, Paint().apply { color = Color.WHITE; setShadowLayer(16f, 0f, 8f, Color.parseColor("#30000000")) })

        // Clip to slide
        canvas.save()
        canvas.clipRect(slideRect)

        // Background color/gradient
        bgPaint.color = slide.background.color
        canvas.drawRoundRect(slideRect, 16f, 16f, bgPaint)
        slide.background.imagePath?.let { path ->
            val bmp = loadBitmap(path)
            if (bmp != null) {
                val src = Rect(0, 0, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, src, slideRect, null)
            }
        }

        // Theme accent bar at top
        val accentPaint = Paint().apply { color = Color.parseColor("#1A73E8") }
        canvas.drawRect(slideRect.left, slideRect.top, slideRect.right, slideRect.top + 8f, accentPaint)

        // Draw elements
        slide.elements.forEach { el ->
            drawElement(canvas, el, isSelected = el == selectedElement)
        }

        canvas.restore()

        // Selection handles outside clip
        selectedElement?.let { el ->
            val rect = elementToRect(el)
            canvas.drawRect(rect, selectionPaint)
            // Handles at corners
            val handles = listOf(
                PointF(rect.left, rect.top),
                PointF(rect.right, rect.top),
                PointF(rect.left, rect.bottom),
                PointF(rect.right, rect.bottom)
            )
            handles.forEach { p ->
                canvas.drawCircle(p.x, p.y, 10f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
                canvas.drawCircle(p.x, p.y, 8f, handlePaint)
            }
        }
    }

    private fun drawElement(canvas: Canvas, el: SlideElement, isSelected: Boolean) {
        val rect = elementToRect(el)
        when (el) {
            is SlideElement.TextBox -> {
                // Text box - draw text with wrapping
                textPaint.color = el.fontColor
                textPaint.textSize = el.fontSize * resources.displayMetrics.density * 0.45f
                textPaint.typeface = when {
                    el.bold && el.italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                    el.bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    el.italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    else -> Typeface.DEFAULT
                }
                textPaint.isUnderlineText = el.underline
                textPaint.textAlign = when (el.alignment) {
                    1 -> Paint.Align.CENTER
                    2 -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }

                // Background for title
                if (el.isTitle) {
                    val titleBg = Paint().apply { color = Color.parseColor("#E8F0FE"); alpha = 0 }
                    //canvas.drawRoundRect(rect, 8f, 8f, titleBg)
                }

                val x = when (el.alignment) {
                    1 -> rect.centerX()
                    2 -> rect.right - 12f
                    else -> rect.left + 12f
                }
                val y = rect.top + 32f

                // Simple text layout with wrapping
                val maxWidth = rect.width() - 24f
                val layout = android.text.StaticLayout.Builder.obtain(el.text, 0, el.text.length, textPaint, maxWidth.toInt())
                    .setAlignment(
                        when (el.alignment) {
                            1 -> android.text.Layout.Alignment.ALIGN_CENTER
                            2 -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                            else -> android.text.Layout.Alignment.ALIGN_NORMAL
                        }
                    )
                    .build()
                canvas.save()
                canvas.translate(x, y)
                // Adjust for alignment offset already
                if (el.alignment == 0) canvas.translate(-x + rect.left + 12f, 0f)
                if (el.alignment == 2) canvas.translate(-x + rect.right - 12f - layout.width, 0f)
                if (el.alignment == 1) canvas.translate(-x + rect.centerX() - layout.width/2, 0f)
                layout.draw(canvas)
                canvas.restore()

                if (el.bullet) {
                    // Draw bullet before text
                    val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = el.fontColor; textSize = textPaint.textSize }
                    canvas.drawText("•", rect.left + 16f, y + 12f, bulletPaint)
                }
            }
            is SlideElement.Shape -> {
                val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = el.fillColor; style = Paint.Style.FILL }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = el.strokeColor; style = Paint.Style.STROKE; strokeWidth = el.strokeWidth }
                when (el.type) {
                    ShapeType.RECTANGLE -> canvas.drawRoundRect(rect, 16f, 16f, shapePaint)
                    ShapeType.OVAL -> canvas.drawOval(rect, shapePaint)
                    ShapeType.TRIANGLE -> {
                        val path = Path().apply {
                            moveTo(rect.centerX(), rect.top)
                            lineTo(rect.left, rect.bottom)
                            lineTo(rect.right, rect.bottom)
                            close()
                        }
                        canvas.drawPath(path, shapePaint)
                    }
                    ShapeType.DIAMOND -> {
                        val path = Path().apply {
                            moveTo(rect.centerX(), rect.top)
                            lineTo(rect.right, rect.centerY())
                            lineTo(rect.centerX(), rect.bottom)
                            lineTo(rect.left, rect.centerY())
                            close()
                        }
                        canvas.drawPath(path, shapePaint)
                    }
                    else -> canvas.drawRect(rect, shapePaint)
                }
                if (el.strokeWidth > 0) canvas.drawRoundRect(rect, 16f, 16f, strokePaint)
            }
            is SlideElement.Image -> {
                val bmp = loadBitmap(el.path)
                if (bmp != null) {
                    val src = Rect(0, 0, bmp.width, bmp.height)
                    canvas.drawBitmap(bmp, src, rect, null)
                    return
                }
                val imgPaint = Paint().apply { color = Color.parseColor("#E0E0E0") }
                canvas.drawRoundRect(rect, 12f, 12f, imgPaint)
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 40f; textAlign = Paint.Align.CENTER }
                // Ikon gambar pixel-style (garis diagonal + lingkaran matahari)
            val ix = rect.centerX(); val iy = rect.centerY()
            val isz = minOf(rect.width(), rect.height()) * 0.22f
            val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA0A6"); style = Paint.Style.STROKE; strokeWidth = isz * 0.18f }
            canvas.drawRect(ix - isz, iy - isz * 0.75f, ix + isz, iy + isz * 0.75f, iconStroke)
            canvas.drawCircle(ix - isz * 0.45f, iy - isz * 0.3f, isz * 0.22f, iconStroke)
            canvas.drawLine(ix - isz, iy + isz * 0.6f, ix - isz * 0.15f, iy - isz * 0.15f, iconStroke)
            canvas.drawLine(ix - isz * 0.15f, iy - isz * 0.15f, ix + isz, iy + isz * 0.6f, iconStroke)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 12f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
                canvas.drawText("Gambar", rect.centerX(), rect.centerY() + 32f, labelPaint)
            }
            is SlideElement.Table -> {
                drawTable(canvas, rect, el)
            }
            is SlideElement.Chart -> {
                drawChart(canvas, rect, el)
            }
        }
    }

    private fun drawTable(canvas: Canvas, rect: RectF, table: SlideElement.Table) {
        val cellW = rect.width() / table.cols
        val cellH = rect.height() / table.rows
        val gridPaint = Paint().apply { color = Color.parseColor("#BDBDBD"); style = Paint.Style.STROKE; strokeWidth = 2f }
        val headerPaint = Paint().apply { color = Color.parseColor("#1A73E8") }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }

        for (r in 0 until table.rows) {
            for (c in 0 until table.cols) {
                val cellRect = RectF(rect.left + c * cellW, rect.top + r * cellH, rect.left + (c+1)*cellW, rect.top + (r+1)*cellH)
                if (r == 0) canvas.drawRect(cellRect, headerPaint)
                canvas.drawRect(cellRect, gridPaint)
                val txt = table.data.getOrNull(r)?.getOrNull(c) ?: ""
                textPaint.color = if (r == 0) Color.WHITE else Color.BLACK
                canvas.drawText(txt, cellRect.centerX(), cellRect.centerY() + 6f, textPaint)
            }
        }
    }

    private fun drawChart(canvas: Canvas, rect: RectF, chart: SlideElement.Chart) {
        val maxVal = chart.data.maxOrNull() ?: 1f
        val barW = rect.width() / chart.data.size * 0.6f
        chart.data.forEachIndexed { i, v ->
            val left = rect.left + i * (rect.width() / chart.data.size) + (rect.width()/chart.data.size - barW)/2
            val top = rect.bottom - (v / maxVal) * rect.height() * 0.8f
            val barRect = RectF(left, top, left + barW, rect.bottom - 20f)
            val barPaint = Paint().apply { color = listOf(Color.parseColor("#1A73E8"), Color.parseColor("#34A853"), Color.parseColor("#FBBC04"), Color.parseColor("#EA4335"))[i % 4] }
            canvas.drawRoundRect(barRect, 8f, 8f, barPaint)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER }
            canvas.drawText(chart.labels.getOrNull(i) ?: "", barRect.centerX(), rect.bottom - 4f, labelPaint)
            canvas.drawText(v.toInt().toString(), barRect.centerX(), top - 8f, labelPaint)
        }
    }

    private fun elementToRect(el: SlideElement): RectF {
        val w = slideRect.width()
        val h = slideRect.height()
        return RectF(
            slideRect.left + el.x * w,
            slideRect.top + el.y * h,
            slideRect.left + (el.x + el.width) * w,
            slideRect.top + (el.y + el.height) * h
        )
    }

    private fun hitTest(x: Float, y: Float): SlideElement? {
        // Reverse order (topmost first)
        for (i in slide.elements.indices.reversed()) {
            val el = slide.elements[i]
            val rect = elementToRect(el)
            if (rect.contains(x, y)) return el
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If stylus in free draw mode, parent will handle ink overlay separately
        // Here handle drag for elements

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                if (hit != null) {
                    selectedElement = hit
                    onElementSelected?.invoke(hit)
                    dragStartX = event.x
                    dragStartY = event.y
                    isDragging = true
                    dragMoved = false
                } else {
                    selectedElement = null
                    onElementSelected?.invoke(null)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedElement != null) {
                    val dx = (event.x - dragStartX) / slideRect.width()
                    val dy = (event.y - dragStartY) / slideRect.height()
                    if (kotlin.math.abs(event.x - dragStartX) > 4f ||
                        kotlin.math.abs(event.y - dragStartY) > 4f) dragMoved = true
                    selectedElement!!.x += dx
                    selectedElement!!.y += dy
                    // Clamp 0..1
                    selectedElement!!.x = selectedElement!!.x.coerceIn(0f, 1f - selectedElement!!.width)
                    selectedElement!!.y = selectedElement!!.y.coerceIn(0f, 1f - selectedElement!!.height)
                    dragStartX = event.x
                    dragStartY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging && !dragMoved) {
                    // Tap: if text box, request edit lewat keyboard
                    (selectedElement as? SlideElement.TextBox)?.let { onTextEditRequested?.invoke(it) }
                }
                isDragging = false
                dragMoved = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun addTextBox(text: String = "Teks Baru") {
        slide.elements.add(SlideElement.TextBox(text = text, x = 0.1f, y = 0.3f))
        invalidate()
    }

    fun addShape(type: ShapeType) {
        slide.elements.add(SlideElement.Shape(type = type))
        invalidate()
    }

    fun getSelectedElement(): SlideElement? = selectedElement

    fun selectElement(el: SlideElement?) {
        selectedElement = el
        invalidate()
        onElementSelected?.invoke(el)
    }

    fun removeSelected(): Boolean {
        val el = selectedElement ?: return false
        slide.elements.remove(el)
        selectedElement = null
        invalidate()
        onElementSelected?.invoke(null)
        return true
    }

    fun duplicateSelected(): Boolean {
        val el = selectedElement ?: return false
        val copy = when (el) {
            is SlideElement.TextBox -> el.copy(x = (el.x + 0.03f).coerceAtMost(1f - el.width))
            is SlideElement.Image -> el.copy(x = (el.x + 0.03f).coerceAtMost(1f - el.width))
            is SlideElement.Shape -> el.copy(x = (el.x + 0.03f).coerceAtMost(1f - el.width))
            is SlideElement.Table -> el.copy(x = (el.x + 0.03f).coerceAtMost(1f - el.width))
            is SlideElement.Chart -> el.copy(x = (el.x + 0.03f).coerceAtMost(1f - el.width))
        }
        slide.elements.add(copy)
        selectedElement = copy
        invalidate()
        onElementSelected?.invoke(copy)
        return true
    }

    fun bringElementToFront(): Boolean {
        val el = selectedElement ?: return false
        slide.elements.remove(el)
        slide.elements.add(el)
        invalidate()
        return true
    }

    fun sendElementToBack(): Boolean {
        val el = selectedElement ?: return false
        slide.elements.remove(el)
        slide.elements.add(0, el)
        invalidate()
        return true
    }

    fun applyToSelectedText(transform: (SlideElement.TextBox) -> Unit): Boolean {
        val tb = selectedElement as? SlideElement.TextBox ?: return false
        transform(tb)
        invalidate()
        return true
    }

    fun insertImage(path: String) {
        slide.elements.add(SlideElement.Image(path = path, x = 0.15f, y = 0.25f, width = 0.7f, height = 0.45f))
        invalidate()
    }

    fun notifyChanged() = invalidate()

    private fun loadBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        imageCache[path]?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(path, opts)
            if (bmp != null) imageCache[path] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun getBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)
        draw(c)
        return bmp
    }
}
