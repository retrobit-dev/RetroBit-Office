package com.retrobit.office.powerpoint

import android.content.Context
import android.graphics.Bitmap
import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.xslf.usermodel.*
import java.awt.Rectangle
import java.io.File
import java.io.FileOutputStream

class PptxExporter(private val context: Context) {

    fun export(presentation: Presentation, file: File, slideBitmaps: List<Bitmap>? = null): File {
        val ppt = XMLSlideShow()

        // Slide size 13.33 x 7.5 inch (16:9) - modern
        ppt.pageSize = java.awt.Dimension(1280, 720)

        presentation.slides.forEachIndexed { idx, slideModel ->
            val slide = ppt.createSlide()

            // Background
            val bg = slide.background
            bg.fillColor = java.awt.Color(slideModel.background.color)

            // If we have rendered bitmap for this slide (with ink), use as image
            slideBitmaps?.getOrNull(idx)?.let { bmp ->
                val temp = File(context.cacheDir, "slide_${idx}.png")
                temp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                val picData = ppt.addPicture(temp.inputStream(), PictureData.PictureType.PNG)
                // Optionally add as background
                // For now, we still create native elements; bitmap is fallback
                temp.delete()
            }

            // Elements
            slideModel.elements.forEach { el ->
                when (el) {
                    is SlideElement.TextBox -> {
                        val shape = slide.createTextBox()
                        val anchor = Rectangle(
                            (el.x * 1280).toInt(),
                            (el.y * 720).toInt(),
                            (el.width * 1280).toInt(),
                            (el.height * 720).toInt()
                        )
                        shape.anchor = anchor
                        val para = shape.textParagraphs[0]
                        para.textAlign = when (el.alignment) {
                            1 -> org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER
                            2 -> org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT
                            else -> org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT
                        }
                        val run = para.addNewTextRun()
                        run.setText(el.text)
                        run.fontSize = el.fontSize.toDouble()
                        run.isBold = el.bold
                        run.fontColor = java.awt.Color(el.fontColor)
                        if (el.bullet) para.isBullet = true
                        if (el.isTitle) {
                            run.fontSize = 32.0
                            run.isBold = true
                        }
                    }
                    is SlideElement.Image -> {
                        try {
                            val imgFile = File(el.path)
                            if (imgFile.exists()) {
                                val data = ppt.addPicture(imgFile.inputStream(), PictureData.PictureType.PNG)
                                val pic = slide.createPicture(data)
                                pic.anchor = Rectangle(
                                    (el.x * 1280).toInt(), (el.y * 720).toInt(),
                                    (el.width * 1280).toInt(), (el.height * 720).toInt()
                                )
                            }
                        } catch (_: Exception) {}
                    }
                    is SlideElement.Shape -> {
                        val shape = slide.createAutoShape()
                        shape.shapeType = when (el.type) {
                            ShapeType.RECTANGLE -> org.apache.poi.sl.usermodel.AutoShape.ShapeType.RECT
                            ShapeType.OVAL -> org.apache.poi.sl.usermodel.AutoShape.ShapeType.ELLIPSE
                            ShapeType.TRIANGLE -> org.apache.poi.sl.usermodel.AutoShape.ShapeType.TRIANGLE
                            else -> org.apache.poi.sl.usermodel.AutoShape.ShapeType.RECT
                        }
                        shape.anchor = Rectangle(
                            (el.x * 1280).toInt(), (el.y * 720).toInt(),
                            (el.width * 1280).toInt(), (el.height * 720).toInt()
                        )
                        shape.fillColor = java.awt.Color(el.fillColor)
                    }
                    else -> {}
                }
            }

            // Slide number
            if (presentation.slides.size > 1) {
                val numShape = slide.createTextBox()
                numShape.anchor = Rectangle(1200, 680, 60, 20)
                val p = numShape.textParagraphs[0]
                p.textAlign = org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT
                val r = p.addNewTextRun()
                r.setText("${idx + 1}")
                r.fontSize = 10.0
                r.fontColor = java.awt.Color.GRAY
            }
        }

        FileOutputStream(file).use { ppt.write(it) }
        ppt.close()
        return file
    }

    fun import(file: File): Presentation {
        val pres = Presentation(title = file.nameWithoutExtension)
        try {
            val ppt = XMLSlideShow(file.inputStream())
            pres.slides.clear()
            ppt.slides.forEachIndexed { idx, slide ->
                val s = Slide(index = idx)
                s.elements.clear()
                slide.shapes.forEach { shape ->
                    if (shape is XSLFTextShape) {
                        s.elements.add(
                            SlideElement.TextBox(
                                text = shape.text ?: "",
                                isTitle = shape.textType?.name?.contains("TITLE") == true
                            )
                        )
                    }
                }
                if (s.elements.isEmpty()) {
                    s.elements.add(SlideElement.TextBox(text = "Slide ${idx+1}"))
                }
                pres.slides.add(s)
            }
            ppt.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (pres.slides.isEmpty()) pres.slides.add(Slide())
        return pres
    }
}
