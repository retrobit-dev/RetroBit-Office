package com.retrobit.office.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            Toast.makeText(this, "M-Pencil siap: gunakan mode GAMBAR untuk anotasi", Toast.LENGTH_LONG).show()
        }

        // Office cards
        findViewById<View>(R.id.cardWord)?.setOnClickListener { openWord() }
        findViewById<View>(R.id.cardExcel)?.setOnClickListener { openExcel() }
        findViewById<View>(R.id.cardPowerPoint)?.setOnClickListener { openPowerPoint() }

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

        // Search filter
        findViewById<android.widget.EditText>(R.id.searchInput)?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                recentAdapter.filter(s?.toString() ?: "")
            }
        })

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
        val options = arrayOf("Dokumen Word (.docx)", "Spreadsheet Excel (.xlsx)", "Presentasi PowerPoint (.pptx)")
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
        private var allFiles: List<File> = files
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_recent, parent, false)
            return VH(view)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = files[position]
            val icon = holder.view.findViewById<android.widget.ImageView>(R.id.recentIcon)
            val title = holder.view.findViewById<TextView>(R.id.recentTitle)
            val sub = holder.view.findViewById<TextView>(R.id.recentSub)
            title.text = f.name
            val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            sub.text = "${'$'}{f.length()/1024} KB • ${'$'}{sdf.format(Date(f.lastModified()))}"
            when {
                f.name.endsWith(".docx") -> { icon.setImageResource(R.drawable.ic_pi_file_text); icon.setColorFilter(Color.parseColor("#2E6BE6")) }
                f.name.endsWith(".xlsx") -> { icon.setImageResource(R.drawable.ic_pi_numbers); icon.setColorFilter(Color.parseColor("#1FA971")) }
                else -> { icon.setImageResource(R.drawable.ic_pi_presentation); icon.setColorFilter(Color.parseColor("#E8590C")) }
            }
            holder.view.setOnClickListener { onClick(f) }
        }
        override fun getItemCount() = files.size
        fun update(newFiles: List<File>) { allFiles = newFiles; files = newFiles; notifyDataSetChanged() }
        fun filter(query: String) {
            files = if (query.isBlank()) allFiles else allFiles.filter { it.name.contains(query, ignoreCase = true) }
            notifyDataSetChanged()
        }
    }
}
