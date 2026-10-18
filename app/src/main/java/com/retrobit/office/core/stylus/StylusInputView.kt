package com.retrobit.office.core.stylus

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import kotlin.math.abs

/**
 * Base Stylus Input View - Handle Huawei M-Pencil input
 * Features:
 * - Palm rejection (ignore finger when stylus is active)
 * - Pressure sensitivity (4096 levels)
 * - Tilt support
 * - Hover preview (on MatePad Mini)
 * - Double-tap to switch mode
 */
abstract class StylusInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var config = StylusConfig()
    var listener: StylusListener? = null

    protected var currentStroke: InkStroke? = null
    protected val strokes = mutableListOf<InkStroke>()
    protected var isStylusActive = false
    protected var lastToolType = MotionEvent.TOOL_TYPE_UNKNOWN

    // Palm rejection: if stylus detected, ignore touch for 500ms
    private var stylusDetectedTime = 0L
    private val palmRejectionWindow = 500L

    // Paints
    protected val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = config.color
        strokeWidth = config.strokeWidth
    }

    protected val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private var hoverPoint: PointF? = null

    interface StylusListener {
        fun onStrokeStarted(stroke: InkStroke)
        fun onStrokeUpdated(stroke: InkStroke)
        fun onStrokeEnded(stroke: InkStroke, allStrokes: List<InkStroke>)
        fun onStylusStateChanged(isStylus: Boolean)
        fun onModeChanged(mode: InputMode)
        // For handwriting mode
        fun onHandwritingResult(text: String, candidates: List<String>) {}
        // For double tap
        fun onStylusDoubleTap() {}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER

        // Hover detection (ACTION_HOVER_MOVE)
        // Track hover for preview

        // Palm rejection logic
        if (config.palmRejectionEnabled) {
            if (isStylus) {
                stylusDetectedTime = System.currentTimeMillis()
                if (!isStylusActive) {
                    isStylusActive = true
                    lastToolType = toolType
                    listener?.onStylusStateChanged(true)
                }
            } else if (isFinger && isStylusActive) {
                val elapsed = System.currentTimeMillis() - stylusDetectedTime
                if (elapsed < palmRejectionWindow) {
                    // Ignore finger touch while stylus active
                    return true
                } else {
                    isStylusActive = false
                    listener?.onStylusStateChanged(false)
                }
            }
            // If stylus not active recently, allow finger
            if (!isStylus && System.currentTimeMillis() - stylusDetectedTime > palmRejectionWindow * 2) {
                isStylusActive = false
            }
        }

        // Only process stylus in stylus modes, or allow finger if config permits
        val shouldProcess = when {
            isStylus -> true
            config.palmRejectionEnabled && isStylusActive -> false // reject finger
            !config.palmRejectionEnabled -> true
            else -> config.mode == InputMode.TOUCH || !isStylusActive
        }

        if (!shouldProcess) return true

        val point = StylusPoint(
            x = event.x,
            y = event.y,
            pressure = event.pressure.coerceIn(0f, 1f),
            tiltX = try { event.getAxisValue(MotionEvent.AXIS_TILT) } catch (_: Exception) { 0f },
            tiltY = 0f,
            timestamp = event.eventTime,
            toolType = toolType
        )

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleStrokeStart(point, toolType)
                // Double tap detection would be handled via gesture detector
            }
            MotionEvent.ACTION_MOVE -> {
                // Handle batched historical points for smoothness (important for stylus)
                for (i in 0 until event.historySize) {
                    val hp = StylusPoint(
                        x = event.getHistoricalX(i),
                        y = event.getHistoricalY(i),
                        pressure = event.getHistoricalPressure(i),
                        timestamp = event.getHistoricalEventTime(i),
                        toolType = toolType
                    )
                    handleStrokeMove(hp)
                }
                handleStrokeMove(point)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleStrokeEnd(point)
                if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                    // Keep stylus active briefly for palm rejection
                }
            }
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverPoint = PointF(event.x, event.y)
                if (config.hoverPreviewEnabled) invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverPoint = null
                invalidate()
            }
        }
        return true
    }

    private fun handleStrokeStart(point: StylusPoint, toolType: Int) {
        currentStroke = InkStroke(config = config.copy()).apply {
            addPoint(point)
        }
        // Adjust for eraser
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
            currentStroke?.config?.tool = StylusTool.ERASER
        }
        currentStroke?.let { listener?.onStrokeStarted(it) }
        onStylusStrokeStart(point)
        ViewCompat.performHapticFeedback(this, androidx.core.view.HapticFeedbackConstantsCompat.KEYBOARD_TAP)
        invalidate()
    }

    private fun handleStrokeMove(point: StylusPoint) {
        currentStroke?.addPoint(point)
        currentStroke?.let { listener?.onStrokeUpdated(it) }
        onStylusStrokeMove(point)
        invalidate()
    }

    private fun handleStrokeEnd(point: StylusPoint) {
        currentStroke?.addPoint(point)
        currentStroke?.let { stroke ->
            strokes.add(stroke)
            listener?.onStrokeEnded(stroke, strokes.toList())
            onStylusStrokeEnd(stroke)
        }
        currentStroke = null
        invalidate()
    }

    // To be implemented by subclasses
    protected open fun onStylusStrokeStart(point: StylusPoint) {}
    protected open fun onStylusStrokeMove(point: StylusPoint) {}
    protected open fun onStylusStrokeEnd(stroke: InkStroke) {}

    fun clearStrokes() {
        strokes.clear()
        currentStroke = null
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
            invalidate()
        }
    }

    fun setInputMode(mode: InputMode) {
        config.mode = mode
        listener?.onModeChanged(mode)
        invalidate()
    }

    fun setTool(tool: StylusTool) {
        config.tool = tool
        updatePaintForTool(tool)
        invalidate()
    }

    fun setColor(color: Int) {
        config.color = color
        inkPaint.color = color
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        config.strokeWidth = width
        inkPaint.strokeWidth = width
        invalidate()
    }

    private fun updatePaintForTool(tool: StylusTool) {
        when (tool) {
            StylusTool.PEN -> {
                inkPaint.strokeWidth = config.strokeWidth
                inkPaint.alpha = 255
                inkPaint.maskFilter = null
            }
            StylusTool.MARKER -> {
                inkPaint.strokeWidth = config.strokeWidth * 1.5f
                inkPaint.alpha = 255
            }
            StylusTool.HIGHLIGHTER -> {
                inkPaint.strokeWidth = config.strokeWidth * 3f
                inkPaint.alpha = 80
            }
            StylusTool.PENCIL -> {
                inkPaint.strokeWidth = config.strokeWidth * 0.8f
                inkPaint.alpha = 200
                inkPaint.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
            }
            StylusTool.ERASER -> {
                inkPaint.strokeWidth = config.strokeWidth * 4f
                inkPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {}
        }
    }

    protected fun drawHoverPreview(canvas: Canvas) {
        hoverPoint?.let { p ->
            if (config.hoverPreviewEnabled) {
                canvas.drawCircle(p.x, p.y, config.strokeWidth / 2 + 6f, hoverPaint)
                // Crosshair for precision
                canvas.drawLine(p.x - 10, p.y, p.x + 10, p.y, hoverPaint)
                canvas.drawLine(p.x, p.y - 10, p.x, p.y + 10, hoverPaint)
            }
        }
    }

    // Accessibility
    override fun isHapticFeedbackEnabled(): Boolean = true
}
