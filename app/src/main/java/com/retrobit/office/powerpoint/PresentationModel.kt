package com.retrobit.office.powerpoint

import android.graphics.Color

/**
 * PowerPoint Presentation Model
 */
data class Presentation(
    var title: String = "Presentasi Tanpa Judul",
    var author: String = "RetroBit Office",
    var slides: MutableList<Slide> = mutableListOf(Slide()),
    var currentIndex: Int = 0,
    var theme: Theme = Theme.MODERN
) {
    val currentSlide: Slide get() = slides[currentIndex]
    fun addSlide(layout: SlideLayout = SlideLayout.TITLE_AND_CONTENT): Slide {
        val s = Slide(layout = layout, index = slides.size)
        slides.add(s)
        return s
    }
    fun duplicateSlide(index: Int) {
        if (index in slides.indices) {
            val copy = slides[index].copy(index = slides.size, id = java.util.UUID.randomUUID().toString())
            slides.add(index + 1, copy)
        }
    }
}

data class Slide(
    var id: String = java.util.UUID.randomUUID().toString(),
    var index: Int = 0,
    var layout: SlideLayout = SlideLayout.TITLE_AND_CONTENT,
    var background: SlideBackground = SlideBackground(),
    var elements: MutableList<SlideElement> = mutableListOf(
        SlideElement.TextBox(text = "Klik untuk menambahkan judul", isTitle = true),
        SlideElement.TextBox(text = "Klik untuk menambahkan isi", isTitle = false)
    ),
    var transition: Transition = Transition.NONE,
    var notes: String = "",
    // Ink overlay for this slide
    var inkStrokes: MutableList<com.retrobit.office.core.stylus.InkStroke> = mutableListOf()
)

enum class SlideLayout {
    TITLE_ONLY,
    TITLE_AND_CONTENT,
    SECTION_HEADER,
    TWO_CONTENT,
    COMPARISON,
    TITLE_AND_TWO_CONTENT,
    BLANK,
    CONTENT_WITH_CAPTION,
    PICTURE_WITH_CAPTION
}

enum class Theme(val primary: Int, val accent: Int, val bg: Int) {
    MODERN(Color.parseColor("#1A73E8"), Color.parseColor("#FBBC04"), Color.WHITE),
    DARK(Color.parseColor("#202124"), Color.parseColor("#8AB4F8"), Color.parseColor("#202124")),
    WARM(Color.parseColor("#E8710A"), Color.parseColor("#F6BF26"), Color.parseColor("#FEF7E0")),
    NATURE(Color.parseColor("#137333"), Color.parseColor("#81C995"), Color.WHITE),
    RETRO(Color.parseColor("#7B1FA2"), Color.parseColor("#FF6D00"), Color.parseColor("#FFF3E0"))
}

data class SlideBackground(
    var color: Int = Color.WHITE,
    var imagePath: String? = null,
    var gradient: Boolean = false
)

enum class Transition { NONE, FADE, PUSH, WIPE, ZOOM, MORPH }

sealed class SlideElement {
    abstract var x: Float
    abstract var y: Float
    abstract var width: Float
    abstract var height: Float
    abstract var rotation: Float

    data class TextBox(
        override var x: Float = 0.05f, // relative 0..1
        override var y: Float = 0.1f,
        override var width: Float = 0.9f,
        override var height: Float = 0.2f,
        override var rotation: Float = 0f,
        var text: String = "",
        var isTitle: Boolean = false,
        var fontSize: Float = if (isTitle) 28f else 16f,
        var fontColor: Int = Color.BLACK,
        var bold: Boolean = isTitle,
        var alignment: Int = 0, // 0 left, 1 center, 2 right
        var bullet: Boolean = false
    ) : SlideElement()

    data class Image(
        override var x: Float = 0.1f,
        override var y: Float = 0.3f,
        override var width: Float = 0.8f,
        override var height: Float = 0.5f,
        override var rotation: Float = 0f,
        var path: String = "",
        var cornerRadius: Float = 0f
    ) : SlideElement()

    data class Shape(
        override var x: Float = 0.2f,
        override var y: Float = 0.2f,
        override var width: Float = 0.3f,
        override var height: Float = 0.3f,
        override var rotation: Float = 0f,
        var type: ShapeType = ShapeType.RECTANGLE,
        var fillColor: Int = Color.parseColor("#1A73E8"),
        var strokeColor: Int = Color.TRANSPARENT,
        var strokeWidth: Float = 0f
    ) : SlideElement()

    data class Table(
        override var x: Float = 0.05f,
        override var y: Float = 0.3f,
        override var width: Float = 0.9f,
        override var height: Float = 0.5f,
        override var rotation: Float = 0f,
        var rows: Int = 3,
        var cols: Int = 3,
        var data: List<List<String>> = List(rows) { List(cols) { "" } }
    ) : SlideElement()

    data class Chart(
        override var x: Float = 0.1f,
        override var y: Float = 0.3f,
        override var width: Float = 0.8f,
        override var height: Float = 0.5f,
        override var rotation: Float = 0f,
        var chartType: ChartType = ChartType.BAR,
        var data: List<Float> = listOf(30f, 50f, 20f),
        var labels: List<String> = listOf("A", "B", "C")
    ) : SlideElement()
}

enum class ShapeType { RECTANGLE, OVAL, TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON }
enum class ChartType { BAR, PIE, LINE, COLUMN }
