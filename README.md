# RetroBit Office — Huawei MatePad Mini Native Office App

**Aplikasi Office native Kotlin untuk Huawei MatePad Mini (8.8" OLED, 165Hz) dengan dukungan penuh M-Pencil stylus.**

![Platform](https://img.shields.io/badge/Platform-Android%20Kotlin-blue) ![Device](https://img.shields.io/badge/Device-Huawei%20MatePad%20Mini-red) ![Stylus](https://img.shields.io/badge/Stylus-M--Pencil%20Pro%204096-orange) ![MinSDK](https://img.shields.io/badge/minSdk-26-green)

---

## 🎨 Desain & Ikon
- **Retro cyberdeck grayscale e-paper**, clean & minimalis: palet monokrom (kertas `#F4F4EC`, tinta `#1B1B18`), font **monospace**, sudut kotak, garis 1px, tanpa shadow, state aktif = **inversi hitam-putih**, status strip ala terminal.
- **Sidebar tool di kiri** (ramah landscape): rail kategori vertikal + panel tool per kategori — **klik kategori untuk membuka/menutup tool-nya**, area dokumen dapat lebar penuh tanpa scroll horizontal.
- **Semua ikon = pixel-art** dari [pixelarticons](https://pixelarticons.com) v2.4.2 (MIT) dikonversi ke VectorDrawable (`tools/svg2vd.py`) + ikon custom bergaya sama. Tanpa emoji/ikon Material di UI.
- **Label kecil di bawah setiap ikon** (rail kategori + tile tool) agar fungsi langsung terbaca tanpa membuka tooltip.
- Token desain di `values/colors.xml`; komponen `ui/ToolSidebar.kt` dipakai bersama ketiga editor.

## ✨ Fitur Utama

### 📝 Word (Dokumen .docx) — 51 tool
- **Keyboard-first**: ketuk paragraf di halaman → bar input di bawah aktif → ketik langsung; `Enter`/`Done` membuat paragraf baru.
- **8 kategori toolbar**: `FILE`, `EDIT`, `TEKS`, `PARAGRAF`, `SISIPKAN`, `TATA LETAK`, `TAMPILAN`, `CORET` — semua tombol berfungsi.
- Format karakter: **tebal, miring, garis bawah, coret, subscript, superscript**, warna teks, **stabilo**, ukuran font, H1/H2/Normal, reset format.
- Format paragraf: rata kiri/tengah/kanan/justify, poin & nomor, **indent +/−**, spasi baris.
- Edit: undo/redo, potong/salin/tempel paragraf, duplikat, hapus paragraf.
- Sisipkan: tabel, gambar galeri, tanggal; tata letak: ukuran kertas, orientasi, margin.
- Tampilan: zoom, cari teks, statistik kata, lompat paragraf atas/bawah.
- Export/import **.docx** (Apache POI), cetak, bagikan; anotasi tinta bebas di halaman.

### 📊 Excel (Spreadsheet .xlsx) — 42 tool
- **Keyboard-first**: ketuk sel → ketik di **formula bar** → tombol ✓ / `Done` menerapkan.
- **8 kategori toolbar**: `BERKAS`, `EDIT`, `FONT`, `RATA`, `ANGKA`, `SEL`, `DATA`, `TAMPILAN`.
- Format sel: tebal/miring/garis bawah, warna teks, warna isi, ukuran font, rata + lipat teks, border, reset format.
- Angka: **Rupiah, Dollar, persen, angka, desimal +/−** (tampilan langsung berubah).
- Sel: **sisip/hapus baris & kolom**, border, hapus isi.
- Data: **SUM, AVERAGE** otomatis dari data di atas, **sort naik/turun**, filter, grafik batang.
- Rumus Excel (`=SUM`, `=AVERAGE`, `=MAX`, `=MIN`, `=COUNT`, aritmatika) + rekalkulasi otomatis.
- Undo/redo, potong/salin/tempel, zoom, toggle gridline, export/import **.xlsx**, cetak, bagikan.

### 📽 PowerPoint (Presentasi .pptx) — 34 tool
- **5 kategori toolbar**: `BERKAS`, `SLIDE`, `SISIPKAN`, `FORMAT`, `CORET`.
- Teks diedit lewat **dialog keyboard** (ketuk text box), mendukung tebal/miring/garis bawah, ukuran, warna, rata.
- Sisipkan: teks, shape (7 bentuk), tabel (baris×kolom), grafik, gambar galeri.
- Elemen: geser dengan sentuhan, **ke depan / ke belakang**, duplikat, hapus.
- Slide: baru, duplikat, hapus, **naik/turun urutan**, tema, transisi, catatan slide, slideshow.
- Export **.pptx** (WYSIWYG, termasuk tinta), cetak, bagikan.

### ⌨️ Input Teks & Stylus

| Mode | Deskripsi |
|------|-----------|
| **Keyboard (default)** | Semua input teks lewat keyboard sistem. Word: bar input per paragraf. Excel: formula bar. PowerPoint: dialog edit teks. Tidak ada lagi konversi tulisan tangan. |
| **Gambar / Ink** | M-Pencil atau jari untuk coretan bebas & anotasi di atas dokumen/slide (Word & PowerPoint). Bisa undo/erase/clear. |

**Dukungan stylus MatePad Mini:**
- Pressure sensitivity (ketebalan garis mengikuti tekanan)
- Palm rejection saat stylus aktif
- Hover preview pena
- Eraser fisik stylus pada mode gambar

### 🖨 Cetak (Print)
- **Android Print Framework** (Mopria) kompatibel semua printer WiFi/USB: Huawei, HP, Canon, Epson
- Cetak HTML via WebView (`createPrintDocumentAdapter`) + fallback PdfDocument
- Cetak Bitmap (slide dengan ink), Export PDF lokal
- Footer otomatis: "Dicetak dengan RetroBit Office • Huawei MatePad Mini • tanggal"

---

## 📱 Optimasi Huawei MatePad Mini

- **Layar 8.8" OLED 165Hz** — rendering 165Hz, shadow & animasi halus
- **M-Pencil Pro Gen-3** — 4096 pressure, tilt ±60°, hover 15mm
- **HarmonyOS/Android** — input keyboard + kanvas tinta native
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
│   │   │   ├── RetroBitApplication.kt   # Application (keyboard-first)
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
│   │   │   │   └── WordEditorActivity.kt # Main: keyboard editor, format, save/print/share
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

> **APK sudah ditandatangani** (keystore `keystore/retrobit.p12`, alias `retrobit`) — kedua artefak langsung bisa diinstal, dan bisa di-update tanpa uninstall karena tanda tangan sama di setiap run.

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
> Input teks memakai keyboard sistem; kanvas tinta memakai `FreeDrawView` native (tanpa ML Kit saat start).

---

## 🖊 Cara Pakai

1. **Buka dokumen** — pilih Word / Excel / PowerPoint di layar utama.
2. **Pilih tool** — ketuk kategori di rail kiri (mis. `TEKS`), lalu ketuk ikon berlabel di panel.
3. **Word** — ketuk paragraf → keyboard terbuka → ketik di bar bawah. Tombol `+` = paragraf baru.
4. **Excel** — ketuk sel → ketik nilai/rumus di formula bar → tekan ✓. Format lewat kategori `ANGKA`/`FONT`.
5. **PowerPoint** — ketuk text box untuk edit lewat keyboard; tombol `SISIPKAN` untuk tabel/grafik/gambar.
6. **Stylus** — pilih `GAMBAR` lalu coret di kanvas; `CORET` menyediakan pena/penghapus/undo/bersihkan.
7. **Simpan & cetak** — FAB simpan, ikon printer, atau menu bagikan.

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
