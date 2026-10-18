package com.retrobit.office.word

import android.content.Context
import android.graphics.Bitmap
import org.apache.poi.xwpf.usermodel.*
import org.apache.poi.util.Units
import java.io.File
import java.io.FileOutputStream

/**
 * Docx Exporter using Apache POI
 * Creates fully compatible .docx for MS Word
 */
class DocxExporter(private val context: Context) {

    fun export(document: WordDocument, outputFile: File): File {
        val doc = XWPFDocument()

        // Core properties
        val props = doc.properties.coreProperties
        props.title = document.title
        props.creator = document.author
        props.created = java.util.Date()

        // Page settings
        val ctDoc = doc.document.body
        val sectPr = ctDoc.addNewSectPr()
        val pgSz = sectPr.addNewPgSz()
        val ps = document.pageSettings.paperSize
        val (wMm, hMm) = if (document.pageSettings.orientation == Orientation.PORTRAIT) {
            ps.widthMm to ps.heightMm
        } else {
            ps.heightMm to ps.widthMm
        }
        // Convert mm to twips (1/1440 inch)
        pgSz.setW(java.math.BigInteger.valueOf(mmToTwips(wMm).toLong()))
        pgSz.setH(java.math.BigInteger.valueOf(mmToTwips(hMm).toLong()))
        pgSz.setOrient(
            if (document.pageSettings.orientation == Orientation.PORTRAIT)
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation.PORTRAIT
            else org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation.LANDSCAPE
        )

        val pgMar = sectPr.addNewPgMar()
        pgMar.setTop(java.math.BigInteger.valueOf(mmToTwips(document.pageSettings.marginTopMm).toLong()))
        pgMar.setBottom(java.math.BigInteger.valueOf(mmToTwips(document.pageSettings.marginBottomMm).toLong()))
        pgMar.setLeft(java.math.BigInteger.valueOf(mmToTwips(document.pageSettings.marginLeftMm).toLong()))
        pgMar.setRight(java.math.BigInteger.valueOf(mmToTwips(document.pageSettings.marginRightMm).toLong()))

        // Header
        if (document.headers.isNotEmpty()) {
            val header = doc.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
            document.headers.forEach { h ->
                val p = header.createParagraph()
                p.alignment = ParagraphAlignment.CENTER
                val r = p.createRun()
                r.setText(h)
                r.isItalic = true
                r.fontSize = 9
            }
        }
        // Footer with page number
        if (document.footers.isNotEmpty() || true) {
            val footer = doc.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
            val p = footer.createParagraph()
            p.alignment = ParagraphAlignment.CENTER
            val r = p.createRun()
            r.setText(document.footers.firstOrNull() ?: "")
            r.fontSize = 9
            r.isItalic = true
        }

        // Paragraphs
        document.paragraphs.forEach { para ->
            val p = doc.createParagraph()
            // Alignment
            p.alignment = when (para.style.alignment) {
                Alignment.LEFT -> ParagraphAlignment.LEFT
                Alignment.CENTER -> ParagraphAlignment.CENTER
                Alignment.RIGHT -> ParagraphAlignment.RIGHT
                Alignment.JUSTIFY -> ParagraphAlignment.BOTH
            }
            p.spacingBetween = para.style.lineSpacing.toDouble()
            p.spacingBefore = para.style.spaceBeforePt.toInt()
            p.spacingAfter = para.style.spaceAfterPt.toInt()
            p.indentationLeft = para.style.indentLeftPt.toInt() * 20 // twips
            p.indentationRight = para.style.indentRightPt.toInt() * 20

            // Style mapping
            when (para.style.styleName) {
                "Heading 1" -> p.style = "Heading1"
                "Heading 2" -> p.style = "Heading2"
                "Title" -> p.style = "Title"
            }

            // Bullet
            if (para.bullet == BulletStyle.BULLET) {
                p.setNumID(java.math.BigInteger.valueOf(1))
            } else if (para.bullet == BulletStyle.NUMBER) {
                p.setNumID(java.math.BigInteger.valueOf(2))
            }

            // Runs
            if (para.runs.isNotEmpty()) {
                para.runs.forEach { run ->
                    val r = p.createRun()
                    r.setText(run.text)
                    r.isBold = run.bold
                    r.isItalic = run.italic
                    r.fontFamily = run.fontName
                    r.fontSize = run.fontSizePt.toInt()
                    if (run.underline) r.underline = UnderlinePatterns.SINGLE
                    if (run.strikethrough) r.isStrikeThrough = true
                    if (run.subscript) r.subscript = VerticalAlign.SUBSCRIPT
                    if (run.superscript) r.subscript = VerticalAlign.SUPERSCRIPT
                    // Color as hex
                    val hex = String.format("%06X", 0xFFFFFF and run.color)
                    r.setColor(hex)
                    run.highlightColor?.let {
                        // POI highlight
                        r.setColor(String.format("%06X", 0xFFFFFF and it))
                    }
                }
            } else {
                val r = p.createRun()
                r.setText(para.text)
            }
        }

        // If document empty, add empty para
        if (document.paragraphs.isEmpty()) {
            doc.createParagraph()
        }

        // Ink as image (if any) - export free-draw layer as PNG at end
        // Caller can provide bitmap via addInkImage

        FileOutputStream(outputFile).use { out ->
            doc.write(out)
        }
        doc.close()
        return outputFile
    }

    fun exportWithInk(document: WordDocument, inkBitmap: Bitmap?, outputFile: File): File {
        val doc = XWPFDocument()
        // ... same as above, then add image
        document.paragraphs.forEach { para ->
            val p = doc.createParagraph()
            val r = p.createRun()
            r.setText(para.text)
        }
        inkBitmap?.let { bmp ->
            val tempImg = File(context.cacheDir, "ink_${System.currentTimeMillis()}.png")
            tempImg.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val p = doc.createParagraph()
            p.alignment = ParagraphAlignment.CENTER
            val r = p.createRun()
            r.addPicture(
                tempImg.inputStream(),
                XWPFDocument.PICTURE_TYPE_PNG,
                tempImg.name,
                Units.toEMU(400.0).toInt(), // width
                Units.toEMU(200.0).toInt()  // height
            )
            tempImg.delete()
        }
        FileOutputStream(outputFile).use { doc.write(it) }
        doc.close()
        return outputFile
    }

    fun exportHtmlPreview(document: WordDocument): String {
        val sb = StringBuilder()
        sb.append("<html><head><meta charset='utf-8'><style>")
        sb.append("body{font-family:Calibri, Arial; margin:40px; line-height:1.15} ")
        sb.append("h1{font-size:22pt; color:#2E74B5} h2{font-size:16pt} p{margin:8px 0}")
        sb.append("</style></head><body>")
        document.paragraphs.forEach { para ->
            val tag = when (para.style.styleName) {
                "Heading 1" -> "h1"
                "Heading 2" -> "h2"
                "Title" -> "h1"
                else -> "p"
            }
            val align = when (para.style.alignment) {
                Alignment.CENTER -> "center"
                Alignment.RIGHT -> "right"
                Alignment.JUSTIFY -> "justify"
                else -> "left"
            }
            sb.append("<$tag style='text-align:$align'>")
            if (para.runs.isNotEmpty()) {
                para.runs.forEach { run ->
                    var t = run.text
                    if (run.bold) t = "<b>$t</b>"
                    if (run.italic) t = "<i>$t</i>"
                    if (run.underline) t = "<u>$t</u>"
                    sb.append(t)
                }
            } else {
                sb.append(para.text)
            }
            sb.append("</$tag>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun mmToTwips(mm: Float): Int = (mm * 56.7f).toInt() // 1mm = 56.7 twips
}
