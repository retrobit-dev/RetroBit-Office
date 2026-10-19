package com.retrobit.office.excel

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class XlsxExporter {

    fun export(model: SpreadsheetModel, file: File): File {
        val wb = XSSFWorkbook()

        model.sheets.forEach { sheetModel ->
            val sheet = wb.createSheet(sheetModel.name)

            // Styles cache
            val styleCache = mutableMapOf<CellStyle, CellStyle>()

            sheetModel.rows.forEachIndexed { rIdx, rowModel ->
                if (rIdx >= 100 && rowModel.cells.all { it.isEmpty }) return@forEachIndexed
                val row = sheet.createRow(rIdx)
                row.heightInPoints = rowModel.heightDp * 0.75f

                rowModel.cells.forEachIndexed { cIdx, cellModel ->
                    if (cellModel.isEmpty && cellModel.style == CellStyle()) return@forEachIndexed
                    val cell = row.createCell(cIdx)

                    // Value
                    if (cellModel.formula != null) {
                        cell.cellFormula = cellModel.formula!!.removePrefix("=")
                    } else {
                        val numeric = cellModel.value.toDoubleOrNull()
                        if (numeric != null) cell.setCellValue(numeric)
                        else cell.setCellValue(cellModel.value)
                    }

                    // Style
                    val poiStyle = wb.createCellStyle()
                    val font = wb.createFont()
                    font.bold = cellModel.style.bold
                    font.italic = cellModel.style.italic
                    font.underline = if (cellModel.style.underline) Font.U_SINGLE else Font.U_NONE
                    font.fontName = cellModel.style.fontName
                    font.fontHeightInPoints = cellModel.style.fontSizePt.toInt().toShort()
                    // Color
                    try {
                        val xssfColor = org.apache.poi.xssf.usermodel.XSSFColor(
                            byteArrayOf(
                                ((cellModel.style.fontColor shr 16) and 0xFF).toByte(),
                                ((cellModel.style.fontColor shr 8) and 0xFF).toByte(),
                                (cellModel.style.fontColor and 0xFF).toByte()
                            ), null
                        )
                        (font as org.apache.poi.xssf.usermodel.XSSFFont).setColor(xssfColor)
                    } catch (_: Exception) {}

                    poiStyle.setFont(font)
                    poiStyle.alignment = when (cellModel.style.alignment) {
                        CellAlignment.LEFT -> HorizontalAlignment.LEFT
                        CellAlignment.CENTER -> HorizontalAlignment.CENTER
                        CellAlignment.RIGHT -> HorizontalAlignment.RIGHT
                        else -> HorizontalAlignment.LEFT
                    }
                    poiStyle.verticalAlignment = when (cellModel.style.verticalAlignment) {
                        VerticalAlignment.TOP -> org.apache.poi.ss.usermodel.VerticalAlignment.TOP
                        VerticalAlignment.BOTTOM -> org.apache.poi.ss.usermodel.VerticalAlignment.BOTTOM
                        else -> org.apache.poi.ss.usermodel.VerticalAlignment.CENTER
                    }
                    // BG
                    if (cellModel.style.bgColor != android.graphics.Color.WHITE) {
                        poiStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
                        try {
                            val bg = org.apache.poi.xssf.usermodel.XSSFColor(
                                byteArrayOf(
                                    ((cellModel.style.bgColor shr 16) and 0xFF).toByte(),
                                    ((cellModel.style.bgColor shr 8) and 0xFF).toByte(),
                                    (cellModel.style.bgColor and 0xFF).toByte()
                                ), null
                            )
                            (poiStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(bg)
                        } catch (_: Exception) {}
                    }
                    // Border
                    if (cellModel.style.border.top) poiStyle.borderTop = org.apache.poi.ss.usermodel.BorderStyle.THIN
                    if (cellModel.style.border.bottom) poiStyle.borderBottom = org.apache.poi.ss.usermodel.BorderStyle.THIN
                    if (cellModel.style.border.left) poiStyle.borderLeft = org.apache.poi.ss.usermodel.BorderStyle.THIN
                    if (cellModel.style.border.right) poiStyle.borderRight = org.apache.poi.ss.usermodel.BorderStyle.THIN

                    // Number format (mengikuti jumlah desimal sel)
                    val dec = cellModel.style.decimals.coerceIn(0, 6)
                    val zeros = if (dec > 0) "." + "0".repeat(dec) else ""
                    val fmt = when (cellModel.style.numberFormat) {
                        NumberFormat.CURRENCY_IDR -> "\"Rp\"#,##0$zeros"
                        NumberFormat.CURRENCY_USD -> "\"$\"#,##0$zeros"
                        NumberFormat.PERCENT -> "0$zeros%"
                        NumberFormat.NUMBER -> "#,##0$zeros"
                        NumberFormat.ACCOUNTING -> "#,##0$zeros"
                        NumberFormat.DATE -> "yyyy-mm-dd"
                        else -> null
                    }
                    fmt?.let { poiStyle.dataFormat = wb.createDataFormat().getFormat(it) }

                    poiStyle.wrapText = cellModel.style.wrapText

                    cell.cellStyle = poiStyle

                    // Column width
                    sheet.setColumnWidth(cIdx, (((sheetModel.cols.getOrNull(cIdx)?.widthDp ?: 96f) * 36f).toInt()))
                }
            }

            // Merged cells
            sheetModel.mergedCells.forEach { range ->
                sheet.addMergedRegion(CellRangeAddress(range.startRow, range.endRow, range.startCol, range.endCol))
            }

            // Freeze pane
            if (sheetModel.frozenRow >= 0 || sheetModel.frozenCol >= 0) {
                sheet.createFreezePane(
                    (sheetModel.frozenCol + 1).coerceAtLeast(0),
                    (sheetModel.frozenRow + 1).coerceAtLeast(0)
                )
            }

            // Auto filter
            if (sheetModel.filterEnabled) {
                sheet.setAutoFilter(CellRangeAddress(0, 0, 0, sheetModel.cols.size - 1))
            }

            // Print setup
            sheet.printSetup.paperSize = PrintSetup.A4_PAPERSIZE
            sheet.fitToPage = true
        }

        FileOutputStream(file).use { wb.write(it) }
        wb.close()
        return file
    }

    fun import(file: File): SpreadsheetModel {
        val model = SpreadsheetModel(sheetName = file.nameWithoutExtension)
        file.inputStream().use { fis ->
            val wb = XSSFWorkbook(fis)
            model.sheets.clear()
            for (sIdx in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(sIdx)
                val sheetModel = Sheet(name = sheet.sheetName)
                sheetModel.rows.clear()
                for (rIdx in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rIdx) ?: continue
                    val rowModel = Row(rIdx)
                    rowModel.cells.clear()
                    for (cIdx in 0 until row.lastCellNum.toInt()) {
                        val cell = row.getCell(cIdx)
                        if (cell == null) {
                            rowModel.cells.add(Cell())
                            continue
                        }
                        val value = when (cell.cellType) {
                            CellType.STRING -> cell.stringCellValue
                            CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) cell.localDateTimeCellValue.toString() else cell.numericCellValue.toString()
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            CellType.FORMULA -> cell.cellFormula
                            else -> ""
                        }
                        rowModel.cells.add(Cell(value = value, displayValue = value, formula = if (cell.cellType == CellType.FORMULA) "=${cell.cellFormula}" else null))
                    }
                    sheetModel.rows.add(rowModel)
                }
                model.sheets.add(sheetModel)
            }
            wb.close()
        }
        if (model.sheets.isEmpty()) model.sheets.add(Sheet())
        return model
    }
}
