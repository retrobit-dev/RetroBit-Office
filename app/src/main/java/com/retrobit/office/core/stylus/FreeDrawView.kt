package com.retrobit.office.core.stylus

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import kotlin.math.abs

/**
 * FreeDrawView - Tulisan bebas & gambar
 * Strokes persist as vector ink on canvas (not converted)
 * Supports layers, pressure, eraser, highlighter
 */
class FreeDrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : StylusInputView(context, attrs) {

    // Use bitmap for persistent drawing for performance
    private var drawBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private var backgroundBitmap: Bitmap? = null

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)
    private val currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    // Undo stack stores bitmap snapshots or stroke list
    private val undoStack = ArrayDeque<Bitmap>(10)

    // Layer support
    data class InkLayer(
        val name: String,
        val strokes: MutableList<InkStroke> = mutableListOf(),
        var visible: Boolean = true,
        var locked: Boolean = false
    )
    private val layers = mutableListOf(InkLayer("Layer 1"))
    private var currentLayerIndex = 0

    init {
        config.mode = InputMode.FREE_DRAW
        setLayerType(LAYER_TYPE_HARDWARE, null) // Better performance
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (drawBitmap == null || drawBitmap!!.width != w || drawBitmap!!.height != h) {
                drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                drawCanvas = Canvas(drawBitmap!!)
                drawCanvas?.drawColor(Color.TRANSPARENT)
                if (layers.any { it.strokes.isNotEmpty() }) redrawAll()
            }
        }
    }

    fun setBackgroundColorInt(color: Int) {
        backgroundBitmap = null
        setBackgroundColor(color)
        invalidate()
    }

    fun setBackgroundImage(bitmap: Bitmap) {
        backgroundBitmap = bitmap
        invalidate()
    }

    override fun onStylusStrokeStart(point: StylusPoint) {
        saveForUndo()
        currentPath.reset()
        currentPath.moveTo(point.x, point.y)
        lastX = point.x
        lastY = point.y
    }

    override fun onStylusStrokeMove(point: StylusPoint) {
        // Bezier smoothing
        val dx = abs(point.x - lastX)
        val dy = abs(point.y - lastY)
        if (dx >= 3 || dy >= 3) {
            currentPath.quadTo(lastX, lastY, (point.x + lastX) / 2, (point.y + lastY) / 2)
            lastX = point.x
            lastY = point.y
        }
        // Live preview already via onDraw
    }

    override fun onStylusStrokeEnd(stroke: InkStroke) {
        currentPath.lineTo(stroke.points.last().x, stroke.points.last().y)
        // Commit to bitmap
        drawCanvas?.let { canvas ->
            val paint = createPaintForStroke(stroke)
            // Draw with pressure variation if enabled
            if (stroke.points.size > 2 && config.pressureSensitive) {
                drawPressureStroke(canvas, stroke, paint)
            } else {
                canvas.drawPath(currentPath, paint)
            }
        }
        layers[currentLayerIndex].strokes.add(stroke)
        currentPath.reset()
    }

    private fun createPaintForStroke(stroke: InkStroke): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.config.color
            strokeWidth = stroke.config.strokeWidth
            alpha = when (stroke.config.tool) {
                StylusTool.HIGHLIGHTER -> 90
                StylusTool.MARKER -> 220
                else -> 255
            }
            if (stroke.config.tool == StylusTool.ERASER) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                strokeWidth = stroke.config.strokeWidth * 4f
            }
            if (stroke.config.tool == StylusTool.PENCIL) {
                maskFilter = BlurMaskFilter(1.5f, BlurMaskFilter.Blur.NORMAL)
                alpha = 180
            }
        }
    }

    private fun drawPressureStroke(canvas: Canvas, stroke: InkStroke, basePaint: Paint) {
        // Draw segment by segment with variable width based on pressure
        val pts = stroke.points
        for (i in 1 until pts.size) {
            val p0 = pts[i-1]
            val p1 = pts[i]
            val width = stroke.config.strokeWidth * (0.5f + p1.pressure)
            basePaint.strokeWidth = width
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, basePaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        backgroundBitmap?.let {
            canvas.drawBitmap(it, null, Rect(0, 0, width, height), bitmapPaint)
        }

        // Persistent bitmap
        drawBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, bitmapPaint)
        }

        // Current live path
        if (!currentPath.isEmpty) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = config.color
                strokeWidth = config.strokeWidth
                if (config.tool == StylusTool.HIGHLIGHTER) {
                    alpha = 90
                    strokeWidth = config.strokeWidth * 3f
                }
            }
            canvas.drawPath(currentPath, paint)
        }

        drawHoverPreview(canvas)
    }

    private fun saveForUndo() {
        drawBitmap?.let { bmp ->
            if (undoStack.size >= 10) undoStack.removeFirst()
            undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
        }
    }

    fun undoLast() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeLast()
            drawBitmap?.recycle()
            drawBitmap = prev
            drawCanvas = Canvas(drawBitmap!!)
            if (layers[currentLayerIndex].strokes.isNotEmpty()) {
                layers[currentLayerIndex].strokes.removeAt(layers[currentLayerIndex].strokes.lastIndex)
            }
            invalidate()
        } else {
            // fallback: clear last stroke from layer and redraw
            clearLastStrokeManually()
        }
    }

    private fun clearLastStrokeManually() {
        val layer = layers[currentLayerIndex]
        if (layer.strokes.isNotEmpty()) {
            layer.strokes.removeAt(layer.strokes.lastIndex)
            redrawAll()
        }
    }

    fun redo() {
        // TODO: redo stack
    }

    private fun redrawAll() {
        drawBitmap?.eraseColor(Color.TRANSPARENT)
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        layers.forEach { layer ->
            if (layer.visible) {
                layer.strokes.forEach { stroke ->
                    val paint = createPaintForStroke(stroke)
                    val path = Path()
                    if (stroke.points.isNotEmpty()) {
                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            path.lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                        drawCanvas?.drawPath(path, paint)
                    }
                }
            }
        }
        invalidate()
    }

    fun clearCanvas() {
        saveForUndo()
        drawBitmap?.eraseColor(Color.TRANSPARENT)
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        layers.forEach { it.strokes.clear() }
        currentPath.reset()
        strokes.clear()
        invalidate()
    }

    /** Daftar stroke tinta (vektor) — hemat memori untuk simpan per-slide. */
    fun exportInkStrokes(): List<InkStroke> =
        layers.filter { it.visible }.flatMap { it.strokes.toList() }

    /** Pulihkan stroke tinta ke kanvas (mis. pindah slide). */
    fun setInkStrokes(strokes: List<InkStroke>) {
        layers.forEach { it.strokes.clear() }
        layers[0].strokes.addAll(strokes)
        undoStack.clear()
        redrawAll()
    }

    /** Render stroke ke bitmap ukuran slide dengan crop area slide. */
    fun renderInkBitmap(
        strokes: List<InkStroke>,
        outW: Int, outH: Int,
        srcLeft: Int, srcTop: Int, srcW: Int, srcH: Int
    ): Bitmap? {
        if (strokes.isEmpty() || outW <= 0 || outH <= 0 || srcW <= 0 || srcH <= 0) return null
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.save()
        c.scale(outW / srcW.toFloat(), outH / srcH.toFloat())
        c.translate(-srcLeft.toFloat(), -srcTop.toFloat())
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = createPaintForStroke(stroke)
            if (stroke.points.size > 2 && stroke.config.pressureSensitive) {
                drawPressureStroke(c, stroke, paint)
            } else {
                val path = Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) path.lineTo(stroke.points[i].x, stroke.points[i].y)
                c.drawPath(path, paint)
            }
        }
        c.restore()
        return bmp
    }

    fun exportAsBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        backgroundBitmap?.let { c.drawBitmap(it, null, Rect(0, 0, width, height), null) }
        drawBitmap?.let { c.drawBitmap(it, 0f, 0f, null) }
        return bmp
    }

    fun addLayer(name: String) {
        layers.add(InkLayer(name))
        currentLayerIndex = layers.lastIndex
        invalidate()
    }

    fun getStrokeCount(): Int = layers.sumOf { it.strokes.size }
}
