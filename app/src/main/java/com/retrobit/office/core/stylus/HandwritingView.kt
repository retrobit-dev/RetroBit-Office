package com.retrobit.office.core.stylus

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import kotlinx.coroutines.*

/**
 * Handwriting-to-Text View
 * User writes with M-Pencil, engine converts to text after pause
 * Visual: ink fades and text appears
 */
class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : StylusInputView(context, attrs) {

    private val engine = InkToTextEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var convertRunnable: Runnable? = null

    private val recognizedTexts = mutableListOf<Pair<RectF, String>>()
    private val committedStrokes = mutableListOf<InkStroke>()

    // Ink trails before conversion
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
        strokeWidth = 5f
    }

    private var onTextRecognized: ((String, List<String>) -> Unit)? = null

    init {
        config.mode = InputMode.HANDWRITING_TO_TEXT
        scope.launch {
            engine.init("id") // Bahasa Indonesia
        }
    }

    fun setOnTextRecognizedListener(l: (String, List<String>) -> Unit) {
        onTextRecognized = l
    }

    fun setLanguage(langTag: String) {
        scope.launch { engine.init(langTag) }
    }

    override fun onStylusStrokeStart(point: StylusPoint) {
        // Cancel pending conversion
        convertRunnable?.let { handler.removeCallbacks(it) }
        engine.startStroke(point)
    }

    override fun onStylusStrokeMove(point: StylusPoint) {
        engine.addPoint(point)
    }

    override fun onStylusStrokeEnd(stroke: InkStroke) {
        engine.endStroke()
        // Schedule conversion after delay (user paused writing)
        scheduleConversion()
    }

    private fun scheduleConversion() {
        convertRunnable?.let { handler.removeCallbacks(it) }
        convertRunnable = Runnable {
            scope.launch {
                val result = engine.recognize()
                if (result != null && result.text.isNotBlank()) {
                    // Commit strokes to text
                    val bounds = calculateBoundsForPendingStrokes()
                    recognizedTexts.add(bounds to result.text)
                    committedStrokes.addAll(strokes.toList())

                    // Notify
                    listener?.onHandwritingResult(result.text, result.candidates)
                    onTextRecognized?.invoke(result.text, result.candidates)

                    // Clear pending strokes after successful recognition
                    strokes.clear()
                    engine.clear()
                    invalidate()
                }
            }
        }
        handler.postDelayed(convertRunnable!!, config.autoConvertDelayMs)
    }

    private fun calculateBoundsForPendingStrokes(): RectF {
        val rect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
        strokes.forEach { stroke ->
            rect.union(stroke.boundingBox)
        }
        if (rect.left == Float.MAX_VALUE) rect.set(0f, 0f, width.toFloat(), 50f)
        return rect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw committed recognized texts as faint background
        recognizedTexts.forEach { (rect, text) ->
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2E7D32")
                textSize = 32f
            }
            canvas.drawText(text, rect.left, rect.top + 30, textPaint)
        }

        // Draw pending strokes as ink
        pathPaint.color = config.color
        strokes.forEach { stroke ->
            drawStroke(canvas, stroke, pathPaint)
        }
        currentStroke?.let { drawStroke(canvas, it, pathPaint) }

        drawHoverPreview(canvas)

        // Draw baseline guide for handwriting (like lined paper)
        drawGuidelines(canvas)
    }

    private fun drawGuidelines(canvas: Canvas) {
        if (config.mode != InputMode.HANDWRITING_TO_TEXT) return
        val guidePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        // Horizontal lines every 80dp
        val lineSpacing = 80 * resources.displayMetrics.density
        var y = lineSpacing
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, guidePaint)
            y += lineSpacing
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke, paint: Paint) {
        if (stroke.points.size < 2) {
            if (stroke.points.isNotEmpty()) {
                val p = stroke.points[0]
                paint.strokeWidth = if (config.pressureSensitive) stroke.config.strokeWidth * (0.5f + p.pressure) else stroke.config.strokeWidth
                canvas.drawPoint(p.x, p.y, paint)
            }
            return
        }
        val path = Path()
        val pts = stroke.points
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val prev = pts[i-1]
            val cur = pts[i]
            // Quadratic bezier for smoothness
            val midX = (prev.x + cur.x) / 2
            val midY = (prev.y + cur.y) / 2
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        // Pressure variation (approximate by average pressure)
        val avgPressure = pts.map { it.pressure }.average().toFloat()
        paint.strokeWidth = if (config.pressureSensitive) {
            stroke.config.strokeWidth * (0.6f + avgPressure * 0.8f)
        } else {
            stroke.config.strokeWidth
        }
        canvas.drawPath(path, paint)
    }

    fun forceRecognizeNow() {
        convertRunnable?.let { handler.removeCallbacks(it) }
        scope.launch {
            val result = engine.recognize()
            if (result != null && result.text.isNotBlank()) {
                onTextRecognized?.invoke(result.text, result.candidates)
                recognizedTexts.add(calculateBoundsForPendingStrokes() to result.text)
                strokes.clear()
                engine.clear()
                invalidate()
            }
        }
    }

    fun clearAll() {
        strokes.clear()
        recognizedTexts.clear()
        committedStrokes.clear()
        engine.clear()
        convertRunnable?.let { handler.removeCallbacks(it) }
        invalidate()
    }

    fun getRecognizedText(separator: String = " "): String {
        return recognizedTexts.joinToString(separator) { it.second }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        engine.release()
        handler.removeCallbacksAndMessages(null)
    }
}
