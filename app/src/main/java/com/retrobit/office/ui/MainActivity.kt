package com.retrobit.office.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.retrobit.office.R
import com.retrobit.office.excel.ExcelEditorActivity
import com.retrobit.office.powerpoint.PowerPointEditorActivity
import com.retrobit.office.word.WordEditorActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recentAdapter: RecentFilesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "RetroBit Office"

        // Huawei MatePad Mini - Stylus banner
        findViewById<View>(R.id.stylusBanner)?.setOnClickListener {
            Toast.makeText(this, "M-Pencil siap: gunakan untuk menulis langsung di dokumen", Toast.LENGTH_LONG).show()
        }

        // Office cards
        findViewById<CardView>(R.id.cardWord)?.setOnClickListener { openWord() }
        findViewById<CardView>(R.id.cardExcel)?.setOnClickListener { openExcel() }
        findViewById<CardView>(R.id.cardPowerPoint)?.setOnClickListener { openPowerPoint() }

        findViewById<View>(R.id.btnNewWord)?.setOnClickListener { openWord() }
        findViewById<View>(R.id.btnNewExcel)?.setOnClickListener { openExcel() }
        findViewById<View>(R.id.btnNewPpt)?.setOnClickListener { openPowerPoint() }

        // FAB
        findViewById<ExtendedFloatingActionButton>(R.id.fabCreate)?.setOnClickListener {
            showCreateDialog()
        }

        // Recent files
        val rv = findViewById<RecyclerView>(R.id.recentList)
        recentAdapter = RecentFilesAdapter(getRecentFiles()) { file ->
            when {
                file.name.endsWith(".docx") -> startActivity(Intent(this, WordEditorActivity::class.java).putExtra("file_path", file.absolutePath))
                file.name.endsWith(".xlsx") -> startActivity(Intent(this, ExcelEditorActivity::class.java).putExtra("file_path", file.absolutePath))
                file.name.endsWith(".pptx") -> startActivity(Intent(this, PowerPointEditorActivity::class.java).putExtra("file_path", file.absolutePath))
                else -> Toast.makeText(this, "Buka: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = recentAdapter

        findViewById<BottomNavigationView>(R.id.bottomNav)?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_files -> { startActivity(Intent(this, FileBrowserActivity::class.java)); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }

        // Storage info
        updateStorageInfo()
    }

    private fun openWord() {
        startActivity(Intent(this, WordEditorActivity::class.java))
    }
    private fun openExcel() {
        startActivity(Intent(this, ExcelEditorActivity::class.java))
    }
    private fun openPowerPoint() {
        startActivity(Intent(this, PowerPointEditorActivity::class.java))
    }

    private fun showCreateDialog() {
        val options = arrayOf("📄 Dokumen Word (.docx)", "📊 Spreadsheet Excel (.xlsx)", "📽 Presentasi PowerPoint (.pptx)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Buat Baru")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWord()
                    1 -> openExcel()
                    2 -> openPowerPoint()
                }
            }
            .show()
    }

    private fun getRecentFiles(): List<File> {
        val dir = getExternalFilesDir(null) ?: return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(".docx") || it.name.endsWith(".xlsx") || it.name.endsWith(".pptx") }
            ?.sortedByDescending { it.lastModified() }?.take(10) ?: emptyList()
    }

    private fun updateStorageInfo() {
        val dir = getExternalFilesDir(null)
        val count = dir?.listFiles()?.size ?: 0
        findViewById<TextView>(R.id.storageInfo)?.text = "$count file • Huawei MatePad Mini • M-Pencil siap"
    }

    override fun onResume() {
        super.onResume()
        recentAdapter.update(getRecentFiles())
        updateStorageInfo()
    }

    // Adapter
    class RecentFilesAdapter(private var files: List<File>, private val onClick: (File) -> Unit) : RecyclerView.Adapter<RecentFilesAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                radius = 16f; cardElevation = 4f
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply { setMargins(16,8,16,8) }
            }
            val row = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(24,24,24,24)
            }
            val icon = TextView(parent.context).apply { textSize = 28f; layoutParams = android.widget.LinearLayout.LayoutParams(80, 80) }
            val col = android.widget.LinearLayout(parent.context).apply { orientation = android.widget.LinearLayout.VERTICAL; layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val title = TextView(parent.context).apply { textSize = 14f; setTextColor(Color.parseColor("#202124")); maxLines = 1 }
            val sub = TextView(parent.context).apply { textSize = 11f; setTextColor(Color.GRAY) }
            col.addView(title); col.addView(sub)
            row.addView(icon); row.addView(col)
            card.addView(row)
            return VH(card)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = files[position]
            val card = holder.view as CardView
            val row = card.getChildAt(0) as android.widget.LinearLayout
            val icon = row.getChildAt(0) as TextView
            val col = row.getChildAt(1) as android.widget.LinearLayout
            val title = col.getChildAt(0) as TextView
            val sub = col.getChildAt(1) as TextView
            title.text = f.name
            val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            sub.text = "${f.length()/1024} KB • ${sdf.format(Date(f.lastModified()))}"
            icon.text = when {
                f.name.endsWith(".docx") -> "📄"
                f.name.endsWith(".xlsx") -> "📊"
                else -> "📽"
            }
            holder.view.setOnClickListener { onClick(f) }
        }
        override fun getItemCount() = files.size
        fun update(newFiles: List<File>) { files = newFiles; notifyDataSetChanged() }
    }
}
