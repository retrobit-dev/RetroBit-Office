package com.retrobit.office.word

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

class DocxImporter {

    fun import(file: File): WordDocument {
        val docDoc = WordDocument(title = file.nameWithoutExtension)
        FileInputStream(file).use { fis ->
            val doc = XWPFDocument(fis)
            doc.paragraphs.forEach { p ->
                val para = Paragraph(
                    text = p.text ?: "",
                    style = ParagraphStyle(
                        alignment = when (p.alignment) {
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER -> Alignment.CENTER
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT -> Alignment.RIGHT
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH -> Alignment.JUSTIFY
                            else -> Alignment.LEFT
                        },
                        styleName = p.style ?: "Normal"
                    )
                )
                p.runs.forEach { r ->
                    para.runs.add(
                        TextRun(
                            text = r.text(0) ?: "",
                            bold = r.isBold,
                            italic = r.isItalic,
                            underline = r.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE,
                            fontName = r.fontFamily ?: "Calibri",
                            fontSizePt = r.fontSizeAsDouble?.toFloat() ?: 11f,
                            color = try {
                                android.graphics.Color.parseColor("#" + (r.color ?: "000000"))
                            } catch (_: Exception) { android.graphics.Color.BLACK }
                        )
                    )
                }
                docDoc.paragraphs.add(para)
            }
            doc.close()
        }
        if (docDoc.paragraphs.isEmpty()) {
            docDoc.addParagraph("")
        }
        return docDoc
    }
}
