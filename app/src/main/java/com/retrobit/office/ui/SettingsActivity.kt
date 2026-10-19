package com.retrobit.office.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.retrobit.office.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Pengaturan"

        findViewById<TextView>(R.id.appInfo)?.text = """
            RetroBit Office v1.1.0
            • Huawei MatePad Mini Optimized
            • Input teks: keyboard (tanpa konversi tulisan tangan)
            • M-Pencil Pro: gambar bebas / anotasi tinta
            • Toolbar lengkap dengan label ikon
            • Output: .docx, .xlsx, .pptx
            • Cetak via Mopria / PDF
            • Dibuat dengan Kotlin + Apache POI
        """.trimIndent()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
