package com.retrobit.office.powerpoint

import android.content.Context
import android.graphics.Bitmap
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PptxExporter - Export/Import .pptx TANPA java.awt (murni OOXML zip writer)
 * Kompatibel MS PowerPoint / WPS. Slide 16:9 (12192000 x 6858000 EMU).
 *
 * - slideBitmaps != null -> tiap slide = 1 gambar full-bleed (WYSIWYG, termasuk ink)
 * - slideBitmaps == null -> elemen native (text box, shape, tabel, chart sbg shape)
 */
class PptxExporter(private val context: Context) {

    companion object {
        private const val EMU_W = 12192000L // 13.333 in
        private const val EMU_H = 6858000L  // 7.5 in
        private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    }

    private val chartColors = listOf(
        "1A73E8", "FBBC04", "34A853", "EA4335", "8AB4F8", "FF6D00", "7B1FA2", "00ACC1"
    )

    fun export(presentation: Presentation, file: File, slideBitmaps: List<Bitmap>? = null): File {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            fun putBytes(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }

            val n = presentation.slides.size
            // ---- [Content_Types].xml ----
            val ct = StringBuilder()
            ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            ct.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            ct.append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
            ct.append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
            ct.append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
            ct.append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
            ct.append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
            for (i in 1..n) ct.append("<Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
            ct.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            ct.append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())

            // ---- _rels/.rels ----
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
                "</Relationships>")

            // ---- docProps ----
            put("docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "<dc:title>${esc(presentation.title)}</dc:title>" +
                "<dc:creator>${esc(presentation.author)}</dc:creator>" +
                "</cp:coreProperties>")
            put("docProps/app.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
                "<Application>RetroBit Office</Application><Slides>$n</Slides></Properties>")

            // ---- presentation.xml ----
            val pres = StringBuilder()
            pres.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            pres.append("<p:presentation xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\">")
            pres.append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>")
            pres.append("<p:sldIdLst>")
            for (i in 1..n) pres.append("<p:sldId id=\"${255 + i}\" r:id=\"rId${i + 1}\"/>")
            pres.append("</p:sldIdLst>")
            pres.append("<p:sldSz cx=\"$EMU_W\" cy=\"$EMU_H\"/>")
            pres.append("<p:notesSz cx=\"6858000\" cy=\"9144000\"/>")
            pres.append("</p:presentation>")
            put("ppt/presentation.xml", pres.toString())

            // ---- presentation.xml.rels ----
            val presRels = StringBuilder()
            presRels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            presRels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            presRels.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
            for (i in 1..n) presRels.append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$i.xml\"/>")
            presRels.append("</Relationships>")
            put("ppt/_rels/presentation.xml.rels", presRels.toString())

            // ---- theme1.xml ----
            put("ppt/theme/theme1.xml", themeXml(presentation.theme))

            // ---- slideMaster1.xml ----
            put("ppt/slideMasters/slideMaster1.xml", slideMasterXml())
            put("ppt/slideMasters/_rels/slideMaster1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>" +
                "</Relationships>")

            // ---- slideLayout1.xml ----
            put("ppt/slideLayouts/slideLayout1.xml", slideLayoutXml())
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>" +
                "</Relationships>")

            // ---- slides ----
            presentation.slides.forEachIndexed { idx, slide ->
                val num = idx + 1
                val bitmap = slideBitmaps?.getOrNull(idx)
                val rels = StringBuilder()
                rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                rels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                rels.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>")

                val body = StringBuilder()
                body.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                body.append("<p:sld xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\">")
                body.append("<p:cSld>")
                // background
                val bgHex = hex(slide.background.color)
                body.append("<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"$bgHex\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
                body.append("<p:spTree>")
                body.append("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>")
                body.append("<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>")

                var shapeId = 2
                if (bitmap != null) {
                    // Full-bleed picture (WYSIWYG incl. ink)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    putBytes("ppt/media/image$num.png", baos.toByteArray())
                    rels.append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image$num.png\"/>")
                    body.append("<p:pic><p:nvPicPr><p:cNvPr id=\"${shapeId++}\" name=\"Slide Image\"/>" +
                        "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                        "<p:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                        "<p:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$EMU_W\" cy=\"$EMU_H\"/></a:xfrm>" +
                        "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>")
                } else {
                    slide.elements.forEach { el ->
                        body.append(elementXml(el, shapeId++, presentation.theme))
                    }
                }
                body.append("</p:spTree></p:cSld>")
                body.append("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>")
                val trans = when (slide.transition) {
                    Transition.FADE -> "<p:transition spd=\"med\"><p:fade/></p:transition>"
                    Transition.PUSH -> "<p:transition spd=\"med\"><p:push dir=\"l\"/></p:transition>"
                    Transition.WIPE -> "<p:transition spd=\"med\"><p:wipe dir=\"r\"/></p:transition>"
                    Transition.ZOOM -> "<p:transition spd=\"med\"><p:zoom dir=\"in\"/></p:transition>"
                    Transition.MORPH -> "<p:transition spd=\"slow\"><p:fade/></p:transition>"
                    else -> ""
                }
                body.append(trans)
                body.append("</p:sld>")
                put("ppt/slides/slide$num.xml", body.toString())
                rels.append("</Relationships>")
                put("ppt/slides/_rels/slide$num.xml.rels", rels.toString())
            }
        }
        return file
    }

    // ---------- Element -> OOXML ----------

    private fun elementXml(el: SlideElement, id: Int, theme: Theme): String {
        val off = "<a:off x=\"${(el.x * EMU_W).toLong()}\" y=\"${(el.y * EMU_H).toLong()}\"/>" +
            "<a:ext cx=\"${(el.width * EMU_W).toLong()}\" cy=\"${(el.height * EMU_H).toLong()}\"/>"
        val rot = if (el.rotation != 0f) " rot=\"${(el.rotation * 60000).toLong()}\"" else ""

        return when (el) {
            is SlideElement.TextBox -> {
                val sz = (el.fontSize * 100).toInt()
                val b = if (el.bold) "1" else "0"
                val i = if (el.italic) " i=\"1\"" else ""
                val u = if (el.underline) " u=\"sng\"" else ""
                val algn = when (el.alignment) { 1 -> "ctr"; 2 -> "r"; else -> "l" }
                val para = el.text.split("\n").joinToString("") { line ->
                    val bu = if (el.bullet) "<a:buFont typeface=\"Arial\"/><a:buChar char=\"&#8226;\"/>" else "<a:buNone/>"
                    val marL = if (el.bullet) " marL=\"285750\" indent=\"-285750\"" else ""
                    "<a:p><a:pPr algn=\"$algn\"$marL>$bu</a:pPr>" +
                        "<a:r><a:rPr lang=\"id-ID\" sz=\"$sz\" b=\"$b\"$i$u dirty=\"0\">" +
                        "<a:solidFill><a:srgbClr val=\"${hex(el.fontColor)}\"/></a:solidFill></a:rPr>" +
                        "<a:t>${esc(line)}</a:t></a:r></a:p>"
                }
                "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"TextBox$id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
                    "<p:spPr><a:xfrm$rot>$off</a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr>" +
                    "<p:txBody><a:bodyPr wrap=\"square\"><a:noAutofit/></a:bodyPr><a:lstStyle/>$para</p:txBody></p:sp>"
            }
            is SlideElement.Shape -> {
                val prst = when (el.type) {
                    ShapeType.RECTANGLE -> "rect"
                    ShapeType.OVAL -> "ellipse"
                    ShapeType.TRIANGLE -> "triangle"
                    ShapeType.DIAMOND -> "diamond"
                    ShapeType.ARROW -> "rightArrow"
                    ShapeType.STAR -> "star5"
                    ShapeType.PENTAGON -> "pentagon"
                }
                val fill = "<a:solidFill><a:srgbClr val=\"${hex(el.fillColor)}\"/></a:solidFill>"
                val ln = if (el.strokeWidth > 0f && el.strokeColor != android.graphics.Color.TRANSPARENT) {
                    "<a:ln w=\"${(el.strokeWidth * 12700).toInt()}\"><a:solidFill><a:srgbClr val=\"${hex(el.strokeColor)}\"/></a:solidFill></a:ln>"
                } else "<a:ln><a:noFill/></a:ln>"
                "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Shape$id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                    "<p:spPr><a:xfrm$rot>$off</a:xfrm><a:prstGeom prst=\"$prst\"><a:avLst/></a:prstGeom>" +
                    "$fill$ln</p:spPr>" +
                    "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang=\"id-ID\"/></a:p></p:txBody></p:sp>"
            }
            is SlideElement.Table -> {
                val colW = (el.width * EMU_W).toLong() / maxOf(1, el.cols)
                val rowH = (el.height * EMU_H).toLong() / maxOf(1, el.rows)
                val grid = (1..el.cols).joinToString("") { "<a:gridCol w=\"$colW\"/>" }
                val rowsXml = el.data.take(el.rows).joinToString("") { row ->
                    val cells = (0 until el.cols).joinToString("") { c ->
                        val txt = row.getOrNull(c) ?: ""
                        "<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"id-ID\" sz=\"1200\"/>" +
                            "<a:t>${esc(txt)}</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc>"
                    }
                    "<a:tr h=\"$rowH\">$cells</a:tr>"
                }
                "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"$id\" name=\"Table$id\"/>" +
                    "<p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>" +
                    "<p:xfrm><a:off x=\"${(el.x * EMU_W).toLong()}\" y=\"${(el.y * EMU_H).toLong()}\"/>" +
                    "<a:ext cx=\"${(el.width * EMU_W).toLong()}\" cy=\"${(el.height * EMU_H).toLong()}\"/></p:xfrm>" +
                    "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\">" +
                    "<a:tbl><a:tblPr firstRow=\"1\" bandRow=\"1\"/><a:tblGrid>$grid</a:tblGrid>$rowsXml</a:tbl>" +
                    "</a:graphicData></a:graphic></p:graphicFrame>"
            }
            is SlideElement.Chart -> chartShapesXml(el, id)
            is SlideElement.Image -> {
                // Path gambar lokal: tempel sbg picture jika file ada
                val f = File(el.path)
                if (el.path.isNotBlank() && f.exists()) {
                    val imgId = "img$id"
                    // ditulis caller-side tidak memungkinkan; fallback: shape abu-abu
                    "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Picture$id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                        "<p:spPr><a:xfrm$rot>$off</a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" +
                        "<a:solidFill><a:srgbClr val=\"D9D9D9\"/></a:solidFill></p:spPr>" +
                        "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"id-ID\" sz=\"1200\"/>" +
                        "<a:t>${esc(f.name)}</a:t></a:r></a:p></p:txBody></p:sp>"
                } else {
                    "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Picture$id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                        "<p:spPr><a:xfrm$rot>$off</a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" +
                        "<a:solidFill><a:srgbClr val=\"D9D9D9\"/></a:solidFill></p:spPr>" +
                        "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"id-ID\" sz=\"1200\"/>" +
                        "<a:t>[Gambar]</a:t></a:r></a:p></p:txBody></p:sp>"
                }
            }
        }
    }

    /** Chart digambar sbg shape native (bars / pie wedges) - 100% valid pptx */
    private fun chartShapesXml(el: SlideElement.Chart, startId: Int): String {
        val sb = StringBuilder()
        var id = startId
        val data = if (el.data.isEmpty()) listOf(1f) else el.data
        val maxV = (data.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val total = data.sum().coerceAtLeast(0.001f)

        fun sp(x: Float, y: Float, w: Float, h: Float, color: String, prst: String = "rect", adj: String = ""): String {
            return "<p:sp><p:nvSpPr><p:cNvPr id=\"${id++}\" name=\"ChartPart\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"${(x * EMU_W).toLong()}\" y=\"${(y * EMU_H).toLong()}\"/>" +
                "<a:ext cx=\"${(w * EMU_W).toLong()}\" cy=\"${(h * EMU_H).toLong()}\"/></a:xfrm>" +
                "<a:prstGeom prst=\"$prst\"><a:avLst>$adj</a:avLst></a:prstGeom>" +
                "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr>" +
                "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang=\"id-ID\"/></a:p></p:txBody></p:sp>"
        }

        when (el.chartType) {
            ChartType.BAR -> {
                val n = data.size
                val barH = el.height / (n * 1.6f)
                data.forEachIndexed { i, v ->
                    val w = el.width * 0.7f * (v / maxV)
                    val y = el.y + i * el.height / n + barH * 0.3f
                    sb.append(sp(el.x, y, w, barH, chartColors[i % chartColors.size]))
                }
            }
            ChartType.PIE -> {
                var start = -90f
                data.forEachIndexed { i, v ->
                    val sweep = 360f * v / total
                    val adj = "<a:gd name=\"adj1\" fmla=\"val ${(start * 60000).toLong()}\"/>" +
                        "<a:gd name=\"adj2\" fmla=\"val ${((start + sweep) * 60000).toLong()}\"/>"
                    sb.append(sp(el.x, el.y, minOf(el.width, el.height), minOf(el.width, el.height), chartColors[i % chartColors.size], "pie", adj))
                    start += sweep
                }
            }
            else -> { // LINE & COLUMN: kolom
                val n = data.size
                val barW = el.width / (n * 1.6f)
                data.forEachIndexed { i, v ->
                    val h = el.height * 0.8f * (v / maxV)
                    val x = el.x + i * el.width / n + barW * 0.3f
                    sb.append(sp(x, el.y + el.height - h, barW, h, chartColors[i % chartColors.size]))
                }
            }
        }
        // label
        sb.append("<p:sp><p:nvSpPr><p:cNvPr id=\"${id++}\" name=\"ChartLabel\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>")
        sb.append("<p:spPr><a:xfrm><a:off x=\"${(el.x * EMU_W).toLong()}\" y=\"${((el.y + el.height) * EMU_H).toLong()}\"/>")
        sb.append("<a:ext cx=\"${(el.width * EMU_W).toLong()}\" cy=\"${(0.06f * EMU_H).toLong()}\"/></a:xfrm>")
        sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p>")
        sb.append("<a:r><a:rPr lang=\"id-ID\" sz=\"1000\"/><a:t>${esc(el.labels.joinToString(" | "))}</a:t></a:r></a:p></p:txBody></p:sp>")
        return sb.toString()
    }

    // ---------- Static parts ----------

    private fun themeXml(theme: Theme): String {
        val accent = hex(theme.accent)
        val primary = hex(theme.primary)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<a:theme xmlns:a=\"$NS_A\" name=\"RetroBit\"><a:themeElements>" +
            "<a:clrScheme name=\"RetroBit\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>" +
            "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>" +
            "<a:dk2><a:srgbClr val=\"44546A\"/></a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>" +
            "<a:accent1><a:srgbClr val=\"$primary\"/></a:accent1><a:accent2><a:srgbClr val=\"$accent\"/></a:accent2>" +
            "<a:accent3><a:srgbClr val=\"34A853\"/></a:accent3><a:accent4><a:srgbClr val=\"EA4335\"/></a:accent4>" +
            "<a:accent5><a:srgbClr val=\"8AB4F8\"/></a:accent5><a:accent6><a:srgbClr val=\"FF6D00\"/></a:accent6>" +
            "<a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme>" +
            "<a:fontScheme name=\"RetroBit\"><a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
            "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
            "<a:fmtScheme name=\"RetroBit\">" +
            "<a:fillStyleLst>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "</a:fillStyleLst>" +
            "<a:lnStyleLst>" +
            "<a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
            "<a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
            "<a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
            "</a:lnStyleLst>" +
            "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
            "<a:bgFillStyleLst>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
            "</a:bgFillStyleLst>" +
            "</a:fmtScheme></a:themeElements></a:theme>"
    }

    private fun slideMasterXml(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<p:sldMaster xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\">" +
            "<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" +
            "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            "</p:spTree></p:cSld>" +
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
            "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>" +
            "</p:sldMaster>"
    }

    private fun slideLayoutXml(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<p:sldLayout xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\" type=\"blank\" preserve=\"1\">" +
            "<p:cSld name=\"Blank\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
    }

    // ---------- Import (.pptx -> model) ----------

    fun import(file: File): Presentation {
        val presentation = Presentation(title = file.nameWithoutExtension)
        presentation.slides.clear()

        val slideXmls = sortedMapOf<Int, String>()
        java.util.zip.ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.name.matches(Regex("ppt/slides/slide\\d+\\.xml"))) {
                    val num = e.name.filter { it.isDigit() }.toInt()
                    slideXmls[num] = zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
                }
            }
        }

        if (slideXmls.isEmpty()) {
            presentation.slides.add(Slide())
            return presentation
        }

        slideXmls.toSortedMap().values.forEach { xml ->
            val slide = Slide(
                elements = mutableListOf(),
                background = SlideBackground(color = parseBg(xml))
            )
            val doc = try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(xml.byteInputStream(Charsets.UTF_8))
            } catch (_: Exception) { null }

            doc?.documentElement?.let { root ->
                // shapes
                val spNodes = root.getElementsByTagName("p:sp")
                for (i in 0 until spNodes.length) {
                    val sp = spNodes.item(i) as Element
                    val xfrm = firstByTag(sp, "a:xfrm") ?: continue
                    val off = firstByTag(xfrm, "a:off") ?: continue
                    val ext = firstByTag(xfrm, "a:ext") ?: continue
                    val x = (off.getAttribute("x").toFloatOrNull() ?: 0f) / EMU_W
                    val y = (off.getAttribute("y").toFloatOrNull() ?: 0f) / EMU_H
                    val w = (ext.getAttribute("cx").toFloatOrNull() ?: 0f) / EMU_W
                    val h = (ext.getAttribute("cy").toFloatOrNull() ?: 0f) / EMU_H
                    val prstGeom = firstByTag(sp, "a:prstGeom")
                    val prst = prstGeom?.getAttribute("prst") ?: "rect"
                    val texts = collectText(sp)
                    val isTextBox = (firstByTag(sp, "p:cNvSpPr")?.getAttribute("txBox") == "1") || texts.isNotEmpty()
                    if (isTextBox && (texts.isNotEmpty() || prst == "rect")) {
                        slide.elements.add(
                            SlideElement.TextBox(
                                x = x, y = y, width = w, height = h,
                                text = texts.joinToString("\n"),
                                isTitle = false
                            )
                        )
                    } else {
                        val type = when (prst) {
                            "ellipse" -> ShapeType.OVAL
                            "triangle" -> ShapeType.TRIANGLE
                            "diamond" -> ShapeType.DIAMOND
                            "rightArrow" -> ShapeType.ARROW
                            "star5" -> ShapeType.STAR
                            "pentagon" -> ShapeType.PENTAGON
                            else -> ShapeType.RECTANGLE
                        }
                        slide.elements.add(SlideElement.Shape(x = x, y = y, width = w, height = h, type = type))
                    }
                }
                // tables
                val tblNodes = root.getElementsByTagName("a:tbl")
                for (i in 0 until tblNodes.length) {
                    val tbl = tblNodes.item(i) as Element
                    val frame = tbl.parentNode?.parentNode as? Element ?: continue
                    val xfrm = firstByTag(frame, "p:xfrm") ?: firstByTag(frame, "a:xfrm") ?: continue
                    val off = firstByTag(xfrm, "a:off") ?: continue
                    val ext = firstByTag(xfrm, "a:ext") ?: continue
                    val rows = tbl.getElementsByTagName("a:tr")
                    val data = mutableListOf<List<String>>()
                    for (r in 0 until rows.length) {
                        val cells = (rows.item(r) as Element).getElementsByTagName("a:tc")
                        val row = mutableListOf<String>()
                        for (c in 0 until cells.length) row.add(collectText(cells.item(c) as Element).joinToString(" "))
                        data.add(row)
                    }
                    slide.elements.add(
                        SlideElement.Table(
                            x = (off.getAttribute("x").toFloatOrNull() ?: 0f) / EMU_W,
                            y = (off.getAttribute("y").toFloatOrNull() ?: 0f) / EMU_H,
                            width = (ext.getAttribute("cx").toFloatOrNull() ?: 0f) / EMU_W,
                            height = (ext.getAttribute("cy").toFloatOrNull() ?: 0f) / EMU_H,
                            rows = data.size.coerceAtLeast(1),
                            cols = data.maxOfOrNull { it.size } ?: 1,
                            data = data
                        )
                    )
                }
            }
            presentation.slides.add(slide)
        }
        return presentation
    }

    private fun firstByTag(el: Element, tag: String): Element? {
        val list = el.getElementsByTagName(tag)
        return if (list.length > 0) list.item(0) as? Element else null
    }

    private fun collectText(el: Element): List<String> {
        val out = mutableListOf<String>()
        val nodes = el.getElementsByTagName("a:t")
        for (i in 0 until nodes.length) {
            val t = nodes.item(i).textContent
            if (t.isNotBlank()) out.add(t)
        }
        return out
    }

    private fun parseBg(xml: String): Int {
        val m = Regex("<a:srgbClr val=\"([0-9A-Fa-f]{6})\"/>").find(xml.substringAfter("<p:bg>", ""))
            ?: return android.graphics.Color.WHITE
        return android.graphics.Color.parseColor("#" + m.groupValues[1])
    }

    // ---------- utils ----------

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun hex(color: Int): String = String.format("%06X", 0xFFFFFF and color)
}
