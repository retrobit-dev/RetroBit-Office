package com.retrobit.office.core.stylus

import android.graphics.Color

/**
 * Stylus states for Huawei M-Pencil (M-Pencil Pro 3rd Gen)
 * Supports: 4096 pressure levels, tilt ±60°, hover detection
 */
enum class InputMode {
    /** Tulis tangan -> otomatis konversi ke teks (Handwriting to Text) */
    HANDWRITING_TO_TEXT,

    /** Tulisan/gambar bebas - tetap sebagai ink (Free Ink) */
    FREE_DRAW,

    /** Mode hapus / eraser (tombol stylus atau gesture) */
    ERASER,

    /** Mode seleksi / select untuk drag, copy */
    SELECT,

    /** Nonaktif stylus, gunakan touch biasa */
    TOUCH
}

enum class StylusTool {
    PEN,
    MARKER,
    HIGHLIGHTER,
    PENCIL,
    ERASER,
    LASER_POINTER
}

data class StylusConfig(
    var mode: InputMode = InputMode.HANDWRITING_TO_TEXT,
    var tool: StylusTool = StylusTool.PEN,
    var color: Int = Color.BLACK,
    var strokeWidth: Float = 4f, // dp
    var pressureSensitive: Boolean = true,
    var palmRejectionEnabled: Boolean = true,
    var hoverPreviewEnabled: Boolean = true,
    var doubleTapToSwitch: Boolean = true, // Huawei pencil double-tap
    var autoConvertDelayMs: Long = 800L // delay before converting handwriting to text
)

data class StylusPoint(
    val x: Float,
    val y: Float,
    val pressure: Float, // 0..1, M-Pencil 4096 levels normalized
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val toolType: Int = 2 // TOOL_TYPE_STYLUS = 2
) {
    fun isStylus(): Boolean = toolType == 2
    fun isEraser(): Boolean = toolType == 4 // TOOL_TYPE_ERASER
}

data class InkStroke(
    val points: MutableList<StylusPoint> = mutableListOf(),
    val config: StylusConfig = StylusConfig(),
    var boundingBox: android.graphics.RectF = android.graphics.RectF()
) {
    fun addPoint(p: StylusPoint) {
        points.add(p)
        if (points.size == 1) {
            boundingBox.set(p.x, p.y, p.x, p.y)
        } else {
            boundingBox.union(p.x, p.y)
        }
    }
    fun isEmpty() = points.isEmpty()
    fun length(): Float {
        if (points.size < 2) return 0f
        var len = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i-1].x
            val dy = points[i].y - points[i-1].y
            len += kotlin.math.sqrt(dx*dx + dy*dy)
        }
        return len
    }
}
