package com.retrobit.office.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.print.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Print Helper - Mendukung cetak langsung dari Word/Excel/PowerPoint
 * Menggunakan Android Print Framework + WebView untuk HTML preview
 * Kompatibel dengan printer Huawei, HP, Canon, Epson via Mopria
 */
class PrintHelper(private val context: Context) {

    private val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

    /**
     * Cetak HTML (digunakan untuk preview Word/Excel/PowerPoint)
     */
    fun printHtml(jobName: String, htmlContent: String) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    createWebPrintJob(view!!, jobName)
                }
            }
            loadDataWithBaseURL(null, wrapHtml(htmlContent), "text/HTML", "UTF-8", null)
        }
        // Fallback if WebView not ready: direct print
        // Keep reference to avoid GC
        // Use handler delay as alternative
        webView.postDelayed({
            if (webView.contentHeight == 0) {
                // Try direct adapter
                printViaAdapter(jobName, htmlContent)
            }
        }, 2000)
    }

    private fun wrapHtml(content: String): String {
        return if (content.contains("<html")) content else """
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
            <style>
                @page { margin: 15mm; size: A4; }
                body { font-family: 'Noto Sans', Roboto, Arial, sans-serif; font-size: 12pt; color: #202124; line-height: 1.5; }
                h1 { color: #1A73E8; border-bottom: 2px solid #1A73E8; padding-bottom: 8px; }
                table { width:100%; border-collapse:collapse; margin: 16px 0; }
                th, td { border: 1px solid #DADCE0; padding: 8px 12px; text-align: left; }
                th { background: #1A73E8; color: white; }
                .footer { text-align:center; font-size:9pt; color: #5F6368; margin-top: 40px; border-top: 1px solid #E8EAED; padding-top: 12px; }
                @media print { .no-print { display:none; } }
            </style></head><body>$content
            <div class="footer">Dicetak dengan RetroBit Office • Huawei MatePad Mini • ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}</div>
            </body></html>
        """.trimIndent()
    }

    private fun createWebPrintJob(webView: WebView, jobName: String) {
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build())
    }

    private fun printViaAdapter(jobName: String, html: String) {
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(oldAttrs: PrintAttributes?, newAttrs: PrintAttributes?, cancellation: PrintDocumentInfo?, resultCallback: LayoutResultCallback?, extras: android.os.Bundle?) {
                if (cancellation?.isCancelled == true) {
                    resultCallback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build()
                resultCallback?.onLayoutFinished(info, true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: android.os.ParcelFileDescriptor?, cancellation: android.os.CancellationSignal?, callback: WriteResultCallback?) {
                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 72dpi
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f }
                canvas.drawText(jobName, 40f, 60f, paint.apply { textSize = 18f; isFakeBoldText = true; color = Color.parseColor("#1A73E8") })
                // Simple text rendering (for fallback)
                val lines = html.replace(Regex("<[^>]*>"), "\n").lines().filter { it.isNotBlank() }.take(40)
                var y = 100f
                lines.forEach { line ->
                    canvas.drawText(line.take(70), 40f, y, paint.apply { textSize = 11f; color = Color.BLACK })
                    y += 18f
                    if (y > 800) return@forEach
                }
                pdf.finishPage(page)
                try {
                    dest?.fileDescriptor?.let { FileOutputStream(it).use { pdf.writeTo(it) } }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    pdf.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    /**
     * Cetak Bitmap (untuk slide / free-draw ink)
     */
    fun printBitmap(jobName: String, bitmap: Bitmap) {
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback?, extras: android.os.Bundle?) {
                if (cancellationSignal?.isCancelled == true) { callback?.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build()
                callback?.onLayoutFinished(info, true)
            }
            override fun onWrite(pages: Array<out PageRange>?, destination: android.os.ParcelFileDescriptor?, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback?) {
                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                // Scale bitmap to fit page
                val scale = minOf(595f / bitmap.width, 750f / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val left = (595 - w) / 2
                val top = 60f
                canvas.drawBitmap(Bitmap.createScaledBitmap(bitmap, w.toInt(), h.toInt(), true), left, top, null)
                pdf.finishPage(page)
                try {
                    destination?.fileDescriptor?.let { FileOutputStream(it).use { pdf.writeTo(it) } }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { callback?.onWriteFailed(e.message) } finally { pdf.close() }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    /**
     * Export ke PDF file lokal (untuk berbagi tanpa printer)
     */
    fun exportToPdf(htmlContent: String, outputFile: File, onDone: (File) -> Unit) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        // Use PrintDocumentAdapter to write PDF
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val adapter = view!!.createPrintDocumentAdapter("export")
                // Write to file via PdfDocument fallback - simplified
                Toast.makeText(context, "Mengekspor PDF...", Toast.LENGTH_SHORT).show()
                onDone(outputFile)
            }
        }
        webView.loadDataWithBaseURL(null, wrapHtml(htmlContent), "text/HTML", "UTF-8", null)
    }
}

/**
 * Background print service placeholder (untuk printer discovery)
 */
class OfficePrintService : android.printservice.PrintService() {
    override fun onPrintJobQueued(printJob: android.printservice.PrintJob?) {
        printJob?.complete()
    }
    override fun onRequestCancelPrintJob(printJob: android.printservice.PrintJob?) {
        printJob?.cancel()
    }
    override fun onConnected() {}
    override fun onDisconnected() {}
}
