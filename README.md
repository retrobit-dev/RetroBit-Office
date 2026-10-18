# RetroBit Office — Huawei MatePad Mini Native Office App

**Aplikasi Office native Kotlin untuk Huawei MatePad Mini (8.8" OLED, 165Hz) dengan dukungan penuh M-Pencil stylus.**

![Platform](https://img.shields.io/badge/Platform-Android%20Kotlin-blue) ![Device](https://img.shields.io/badge/Device-Huawei%20MatePad%20Mini-red) ![Stylus](https://img.shields.io/badge/Stylus-M--Pencil%20Pro%204096-orange) ![MinSDK](https://img.shields.io/badge/minSdk-26-green)

---

## 🎨 Desain & Ikon
- **Retro cyberdeck grayscale e-paper**, clean & minimalis: palet monokrom (kertas `#F4F4EC`, tinta `#1B1B18`), font **monospace**, sudut kotak, garis 1px, tanpa shadow, state aktif = **inversi hitam-putih**, status strip ala terminal.
- **Sidebar tool di kiri** (ramah landscape): rail kategori vertikal + panel tool per kategori — **klik kategori untuk membuka/menutup tool-nya**, area dokumen dapat lebar penuh tanpa scroll horizontal.
- **Semua ikon = pixel-art** dari [pixelarticons](https://pixelarticons.com) v2.4.2 (MIT) dikonversi ke VectorDrawable (`tools/svg2vd.py`) + 12 ikon custom bergaya sama. Tanpa emoji/ikon Material di UI.
- Token desain di `values/colors.xml`; komponen `ui/ToolSidebar.kt` dipakai bersama ketiga editor.

## ✨ Fitur Utama

### 📝 Word (Dokumen .docx)
- Toolbar lengkap mirip MS Word: **Beranda, Sisipkan, Gambar, Tata Letak, Tinjau, Tampilan**
- Format: Bold, Italic, Underline, Warna, Font size, Alignment, Bullet/Numbering, Spasi
- Header/Footer, Margin, Orientasi (Portrait/Landscape), Ukuran kertas A4/Folio/Legal
- Export **.docx** via Apache POI (kompatibel MS Word), Import .docx, HTML Preview
- Halaman dengan pagination, word count

### 📊 Excel (Spreadsheet .xlsx)
- Grid native dengan **pinch-to-zoom, pan, seleksi sel, freeze pane**
- Format sel: warna, border, alignment, number format (IDR, USD, %, Tanggal)
- **Rumus**: `=SUM(A1:A5)`, `=AVERAGE()`, `=MAX()`, `=MIN()`, `=COUNT()`, aritmatika `=A1+B2*2`
- Grafik (Bar/Pie), Filter, Merge cell
- Export/Import **.xlsx** via Apache POI

### 📽 PowerPoint (Presentasi .pptx)
- Slide 16:9 dengan **drag & drop** elemen
- Elemen: TextBox, Gambar, Shape (Rect/Oval/Triangle/Diamond), Tabel, Chart (Bar/Pie)
- Tema: Modern, Dark, Warm, Nature, Retro
- Transisi slide, slideshow mode, duplicate/delete slide
- Export **.pptx** (OOXML native writer), Import .pptx

### ✏️ Dua Mode Stylus Huawei M-Pencil Pro

| Mode | Deskripsi | Teknologi |
|------|-----------|-----------|
| **Tulisan → Teks** | Tulis tangan dengan stylus, otomatis konversi jadi teks ketik setelah jeda 800ms | Google ML Kit Digital Ink + Huawei ML Handwriting fallback, support ID & EN |
| **Tulisan Bebas** | Coretan/gambar tetap sebagai vector ink (tidak dikonversi). Untuk anotasi & tanda tangan | Canvas + Pressure sensitivity (4096 level), Tilt, Palm Rejection, Hover preview |

**Fitur Stylus Khusus MatePad Mini:**
- Palm Rejection (abaikan sentuhan jari saat stylus aktif, 500ms window)
- Pressure sensitivity (ketebalan garis sesuai tekanan)
- Hover preview (lingkaran + crosshair saat pena melayang)
- Double-tap untuk ganti mode (Huawei Pencil SDK)
- Eraser (penghapus fisik stylus), Highlighter (transparan), Pencil (blur)

### 🖨 Cetak (Print)
- **Android Print Framework** (Mopria) kompatibel semua printer WiFi/USB: Huawei, HP, Canon, Epson
- Cetak HTML via WebView (`createPrintDocumentAdapter`) + fallback PdfDocument
- Cetak Bitmap (slide dengan ink), Export PDF lokal
- Footer otomatis: "Dicetak dengan RetroBit Office • Huawei MatePad Mini • tanggal"

---

## 📱 Optimasi Huawei MatePad Mini

- **Layar 8.8" OLED 165Hz** — rendering 165Hz, shadow & animasi halus
- **M-Pencil Pro Gen-3** — 4096 pressure, tilt ±60°, hover 15mm
- **HarmonyOS/Android** — ML Kit Digital Ink (HMS fallback via reflection)
- **Multi-window & Freeform** — `supportsScreens` xlarge, `requestLegacyExternalStorage`
- **File Provider** — berbagi .docx/.xlsx/.pptx via `FileProvider`

---

## 🏗 Arsitektur Project

```
RetroBit-Office/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions, activities, FileProvider
│   │   ├── java/com/retrobit/office/
│   │   │   ├── RetroBitApplication.kt   # Preload ML Kit models
│   │   │   ├── core/stylus/
│   │   │   │   ├── StylusState.kt       # InputMode, StylusConfig, InkStroke
│   │   │   │   ├── StylusInputView.kt   # Base: palm rejection, pressure, hover
│   │   │   │   ├── InkToTextEngine.kt   # ML Kit Digital Ink + Huawei fallback
│   │   │   │   ├── HandwritingView.kt   # Tulisan→Teks dengan auto-convert & guidelines
│   │   │   │   └── FreeDrawView.kt      # Tulisan bebas: bitmap + layers + undo
│   │   │   ├── word/
│   │   │   │   ├── WordDocument.kt      # Model: Paragraph, Run, Table, PageSettings
│   │   │   │   ├── DocxExporter.kt      # Export .docx (Apache POI)
│   │   │   │   ├── DocxImporter.kt      # Import .docx
│   │   │   │   ├── WordEditorView.kt    # Page layout + StaticLayout + cursor
│   │   │   │   ├── WordToolbar.kt       # 6 tab ribbon, ikon pixel-art, aksi lengkap
│   │   │   │   └── WordEditorActivity.kt # Main: mode toggle, handwriting, save/print/share
│   │   │   ├── excel/
│   │   │   │   ├── SpreadsheetModel.kt  # Sheet, Cell, FormulaEngine (SUM/AVERAGE/etc)
│   │   │   │   ├── SpreadsheetView.kt   # Grid, pinch-zoom, pan, selection
│   │   │   │   ├── XlsxExporter.kt      # Export .xlsx
│   │   │   │   └── ExcelEditorActivity.kt
│   │   │   ├── powerpoint/
│   │   │   │   ├── PresentationModel.kt # Slide, Elements, Theme
│   │   │   │   ├── SlideEditorView.kt   # 16:9 canvas, drag, table/chart rendering
│   │   │   │   ├── PptxExporter.kt      # Export .pptx
│   │   │   │   └── PowerPointEditorActivity.kt
│   │   │   ├── print/
│   │   │   │   └── PrintHelper.kt       # printHtml, printBitmap, exportPdf + OfficePrintService
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt      # Home dengan 3 cards + recent files
│   │   │       ├── FileBrowserActivity.kt
│   │   │       └── SettingsActivity.kt
│   │   └── res/
│   │       ├── layout/                  # activity_main, word/excel/ppt editor, etc
│   │       ├── values/                  # strings, colors, themes
│   │       ├── drawable/                # 79 ikon pixel ic_pi_* + shape bg_*
│   │       ├── menu/                    # word/excel/ppt menus, bottom_nav
│   │       └── xml/file_paths.xml       # FileProvider
│   ├── build.gradle.kts                 # Dependencies: POI, ML Kit, HMS, Material3
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔨 Build & Install

### ✅ Build Otomatis via GitHub Actions (Disarankan!)

Setiap push ke `main` / `arena/**` otomatis build APK via workflow `.github/workflows/android-build.yml`:

1. Buka tab **Actions** di repo GitHub → pilih run **"Build RetroBit Office APK"** terbaru (✓ success)
2. Scroll ke bagian **Artifacts**
3. Download **`RetroBit-Office-debug-apk`** (untuk install langsung) atau **`RetroBit-Office-release-apk`**
4. Install ke MatePad Mini:
   ```bash
   adb install RetroBit-Office-debug-apk/app-debug.apk
   ```
   atau copy file APK ke tablet lalu buka dari File Manager.

**Rilis otomatis:** buat tag `v1.0.0` lalu push → APK otomatis di-upload ke GitHub Release.

> Butuh APK terbaru? Cukup `git push` — build selesai ±5 menit.

### Build Lokal (Android Studio)

**Prasyarat:** Android Studio Hedgehog+, JDK 17.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Dependencies utama (`app/build.gradle.kts`)
```kotlin
// ML Kit - Handwriting -> Teks
implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
// Apache POI - Office files (.docx/.xlsx)
implementation("org.apache.poi:poi:5.2.5")
implementation("org.apache.poi:poi-ooxml:5.2.5")
// AndroidX + Material3 + Coroutines
```

> **Catatan teknis:** Export .pptx memakai OOXML writer manual (tanpa XSLF) agar bebas
> dependensi `java.awt` yang tidak ada di Android. .docx/.xlsx via Apache POI XSSF/XWPF.
> Handwriting engine: Google ML Kit Digital Ink (fallback HMS via reflection).

---

## 🖊 Cara Pakai Stylus

1. **Aktifkan Mode** — Pilih `✍️ Tulisan → Teks` atau `🎨 Tulisan Bebas` di toolbar atas (RadioGroup)
2. **Tulis** — Gunakan M-Pencil di area editor (Word) / overlay (Excel/PPT)
3. **Konversi** — Pada mode Tulisan→Teks, diam 800ms atau tap `→ Teks` untuk paksa konversi. Hasil muncul di dokumen + candidate bar (4 opsi)
4. **Warna/Ketebalan** — Pilih warna di palette, atur stroke width via SeekBar
5. **Hapus** — Tombol Eraser / Undo / Clear
6. **Cetak** — Tombol 🖨 di toolbar → pilih printer Mopria

---

## 🧪 Testing di MatePad Mini

- **Palm rejection**: Letakkan telapak tangan di layar sambil menulis — sentuhan jari diabaikan
- **Pressure**: Tekan ringan vs keras — ketebalan garis berubah (0.5x–1.3x)
- **Hover**: Layangkan pena 5mm di atas layar — muncul lingkaran preview
- **Print**: Hubungkan printer via WiFi Direct / Huawei Share → cetak dari menu

---

## 📄 Lisensi

MIT — RetroBit Dev 2025

---

## 🤝 Kontribusi

PR welcome! Fokus: improve FormulaEngine, tambah template Word/PPT, dukungan Huawei Pencil double-tap gesture.
