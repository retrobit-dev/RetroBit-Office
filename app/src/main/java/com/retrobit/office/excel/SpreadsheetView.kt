package com.retrobit.office.excel

import android.text.TextUtils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.retrobit.office.core.stylus.StylusInputView
import kotlin.math.ceil

/**
 * SpreadsheetView - Excel-like grid with stylus support
 * - Tap to select cell, double-tap to edit
 * - Stylus handwriting in cell -> converts to text
 * - Pinch to zoom, drag to scroll (MatePad Mini 165Hz)
 */
class SpreadsheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var model: Sheet = Sheet()
        set(value) {
            field = value
            invalidate()
        }

    var onCellSelected: ((Int, Int, Cell) -> Unit)? = null
    var onCellEdited: ((Int, Int, String) -> Unit)? = null

    private var selectedRow = 0
    private var selectedCol = 0
    private var editingRow = -1
    private var editingCol = -1

    // Layout
    private val headerHeight = 48f
    private val headerWidth = 60f
    private var cellWidth = 120f // will be scaled
    private var cellHeight = 44f
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Paints
    private val gridPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f }
    private val headerPaint = Paint().apply { color = Color.parseColor("#F1F3F4") }
    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3C4043"); textSize = 12f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val cellTextPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f * resources.displayMetrics.density }
    private val selectionPaint = Paint().apply { color = Color.parseColor("#1A73E8"); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val selectionFillPaint = Paint().apply { color = Color.parseColor("#E8F0FE") }
    private val handlePaint = Paint().apply { color = Color.parseColor("#1A73E8") }

    // Gesture
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale = (scale * detector.scaleFactor).coerceIn(0.5f, 3f)
            cellWidth = 120f * scale
            cellHeight = 44f * scale
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            handleDoubleTap(e.x, e.y)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            offsetX = (offsetX - dx).coerceIn(minOffsetX(), 0f)
            offsetY = (offsetY - dy).coerceIn(minOffsetY(), 0f)
            invalidate()
            return true
        }
    })

    private fun minOffsetX(): Float {
        val contentW = headerWidth + model.cols.size * cellWidth
        return (width - contentW).coerceAtMost(0f)
    }
    private fun minOffsetY(): Float {
        val contentH = headerHeight + visibleRows().size * cellHeight
        return (height - contentH).coerceAtMost(0f)
    }

    private fun visibleRows(): List<Int> = model.rows.indices.filter { !model.rows[it].hidden }

    private fun handleTap(x: Float, y: Float) {
        val col = ((x - headerWidth - offsetX) / cellWidth).toInt()
        val si = ((y - headerHeight - offsetY) / cellHeight).toInt()
        val vis = visibleRows()
        val row = vis.getOrElse(si) { -1 }
        if (col in 0 until model.cols.size && row in 0 until model.rows.size) {
            selectedCol = col
            selectedRow = row
            onCellSelected?.invoke(row, col, model.getCell(row, col))
            invalidate()
        } else if (y < headerHeight && x > headerWidth) {
            // Column header tap - select column
            if (col in 0 until model.cols.size) {
                selectedCol = col
                invalidate()
            }
        } else if (x < headerWidth && y > headerHeight) {
            if (row >= 0) {
                selectedRow = row
                invalidate()
            }
        }
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        handleTap(x, y)
        editingRow = selectedRow
        editingCol = selectedCol
        // Trigger edit callback - host should show keyboard/handwriting input
        onCellEdited?.invoke(editingRow, editingCol, model.getCell(editingRow, editingCol).value)
    }

    fun setCellValue(row: Int, col: Int, value: String) {
        model.setCell(row, col, value)
        invalidate()
    }

    fun setCellFormula(row: Int, col: Int, formula: String) {
        model.setFormula(row, col, formula)
        invalidate()
    }

    fun applyStyleToSelection(styleTransform: (CellStyle) -> CellStyle) {
        val cell = model.getCell(selectedRow, selectedCol)
        cell.style = styleTransform(cell.style)
        invalidate()
    }

    fun insertHandwritingText(text: String) {
        setCellValue(selectedRow, selectedCol, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Stylus check - if stylus, we capture for handwriting; else gesture
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS

        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        // Let gesture detector handle pan/tap
        gestureDetector.onTouchEvent(event)

        // For stylus drag, we could show ink, but for now just select
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        // Clip
        val contentLeft = headerWidth + offsetX
        val contentTop = headerHeight + offsetY

        // Draw grid cells
        val firstCol = maxOf(0, ((-offsetX) / cellWidth).toInt())
        val lastCol = minOf(model.cols.size, firstCol + ceil(width / cellWidth).toInt() + 2)
        val vis = visibleRows()
        val firstRow = maxOf(0, ((-offsetY) / cellHeight).toInt())
        val lastRow = minOf(vis.size, firstRow + ceil(height / cellHeight).toInt() + 2)

        for (si in firstRow until lastRow) {
            val r = vis[si]
            val y = contentTop + si * cellHeight
            for (c in firstCol until lastCol) {
                val x = contentLeft + c * cellWidth
                val cell = model.getCell(r, c)

                // Background
                if (cell.style.bgColor != Color.WHITE) {
                    val bgPaint = Paint().apply { color = cell.style.bgColor }
                    canvas.drawRect(x, y, x + cellWidth, y + cellHeight, bgPaint)
                }
                // Selection fill
                if (r == selectedRow && c == selectedCol) {
                    canvas.drawRect(x, y, x + cellWidth, y + cellHeight, selectionFillPaint)
                }

                // Text
                val display = cell.displayValue.ifEmpty { cell.value }
                if (display.isNotEmpty()) {
                    cellTextPaint.color = cell.style.fontColor
                    cellTextPaint.typeface = when {
                        cell.style.bold && cell.style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                        cell.style.bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        cell.style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        else -> Typeface.DEFAULT
                    }
                    cellTextPaint.textSize = cell.style.fontSizePt * resources.displayMetrics.density * 0.85f * scale
                    cellTextPaint.isUnderlineText = cell.style.underline

                    val align = cell.style.alignment
                    val textX = when (align) {
                        CellAlignment.CENTER -> x + cellWidth / 2
                        CellAlignment.RIGHT -> x + cellWidth - 12f
                        else -> x + 10f
                    }
                    cellTextPaint.textAlign = when (align) {
                        CellAlignment.CENTER -> Paint.Align.CENTER
                        CellAlignment.RIGHT -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT
                    }
                    // Ellipsize if overflow
                    val maxW = cellWidth - 16f
                    val ellipsized = if (cellTextPaint.measureText(display) > maxW) {
                        TextUtils.ellipsize(display, cellTextPaint, maxW, TextUtils.TruncateAt.END).toString()
                    } else display
                    canvas.drawText(ellipsized, textX, y + cellHeight / 2 + 6f * scale, cellTextPaint)
                }

                // Borders
                if (cell.style.border.top || true) canvas.drawLine(x, y, x + cellWidth, y, gridPaint)
                if (cell.style.border.left || true) canvas.drawLine(x, y, x, y + cellHeight, gridPaint)
                // Right/bottom always for grid
                canvas.drawLine(x + cellWidth, y, x + cellWidth, y + cellHeight, gridPaint)
                canvas.drawLine(x, y + cellHeight, x + cellWidth, y + cellHeight, gridPaint)

                // Formula indicator (small green triangle)
                if (cell.isFormula) {
                    val triPaint = Paint().apply { color = Color.parseColor("#34A853") }
                    val path = Path().apply {
                        moveTo(x + cellWidth - 12f, y)
                        lineTo(x + cellWidth, y)
                        lineTo(x + cellWidth, y + 12f)
                        close()
                    }
                    canvas.drawPath(path, triPaint)
                }
            }
        }

        // Selection border
        val selX = contentLeft + selectedCol * cellWidth
        val selY = contentTop + selectedRow * cellHeight
        canvas.drawRect(selX, selY, selX + cellWidth, selY + cellHeight, selectionPaint)
        // Handle (drag handle at bottom-right)
        canvas.drawCircle(selX + cellWidth, selY + cellHeight, 10f, handlePaint)
        canvas.drawCircle(selX + cellWidth, selY + cellHeight, 6f, Paint().apply { color = Color.WHITE })

        // Column headers (A, B, C...)
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerPaint)
        canvas.drawRect(0f, 0f, headerWidth, height.toFloat(), headerPaint)
        // Top-left corner
        canvas.drawRect(0f, 0f, headerWidth, headerHeight, Paint().apply { color = Color.parseColor("#E8EAED") })
        canvas.drawText("◧", headerWidth/2, headerHeight/2 + 6f, headerTextPaint.apply { textAlign = Paint.Align.CENTER })

        for (c in firstCol until lastCol) {
            val x = contentLeft + c * cellWidth
            val name = model.getColumnName(c)
            // Highlight selected column
            if (c == selectedCol) {
                canvas.drawRect(x, 0f, x + cellWidth, headerHeight, Paint().apply { color = Color.parseColor("#D2E3FC") })
            } else {
                canvas.drawRect(x, 0f, x + cellWidth, headerHeight, headerPaint)
            }
            canvas.drawLine(x, 0f, x, headerHeight, gridPaint)
            canvas.drawText(name, x + cellWidth/2, headerHeight/2 + 6f, headerTextPaint)
        }

        // Row headers (1,2,3...)
        for (si in firstRow until lastRow) {
            val r = vis[si]
            val y = contentTop + si * cellHeight
            if (r == selectedRow) {
                canvas.drawRect(0f, y, headerWidth, y + cellHeight, Paint().apply { color = Color.parseColor("#D2E3FC") })
            } else {
                canvas.drawRect(0f, y, headerWidth, y + cellHeight, headerPaint)
            }
            canvas.drawLine(0f, y, headerWidth, y, gridPaint)
            canvas.drawText("${r+1}", headerWidth/2, y + cellHeight/2 + 6f, headerTextPaint)
        }

        // Grid lines for headers
        canvas.drawLine(headerWidth, 0f, headerWidth, height.toFloat(), Paint().apply { color = Color.parseColor("#DADCE0"); strokeWidth = 2f })
        canvas.drawLine(0f, headerHeight, width.toFloat(), headerHeight, Paint().apply { color = Color.parseColor("#DADCE0"); strokeWidth = 2f })

        // Scroll indicators (subtle shadow)
        if (offsetX < 0) {
            val shadow = Paint().apply { color = Color.parseColor("#22000000") }
            canvas.drawRect(headerWidth, headerHeight, headerWidth + 12f, height.toFloat(), shadow)
        }
    }
}
