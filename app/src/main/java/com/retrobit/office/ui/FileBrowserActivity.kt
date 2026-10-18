package com.retrobit.office.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.retrobit.office.R
import com.retrobit.office.excel.ExcelEditorActivity
import com.retrobit.office.powerpoint.PowerPointEditorActivity
import com.retrobit.office.word.WordEditorActivity
import java.io.File

class FileBrowserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "File Saya"

        val rv = findViewById<RecyclerView>(R.id.fileGrid)
        rv.layoutManager = GridLayoutManager(this, 2)
        val files = (getExternalFilesDir(null)?.listFiles()?.toList() ?: emptyList())
            .filter { it.extension in listOf("docx","xlsx","pptx","pdf") }
            .sortedByDescending { it.lastModified() }

        if (files.isEmpty()) {
            findViewById<View>(R.id.emptyView)?.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            findViewById<View>(R.id.emptyView)?.visibility = View.GONE
            rv.adapter = FileAdapter(files) { file ->
                val intent = when (file.extension) {
                    "docx" -> Intent(this, WordEditorActivity::class.java)
                    "xlsx" -> Intent(this, ExcelEditorActivity::class.java)
                    "pptx" -> Intent(this, PowerPointEditorActivity::class.java)
                    else -> null
                }
                intent?.putExtra("file_path", file.absolutePath)?.let { startActivity(it) }
            }
            findViewById<TextView>(R.id.fileCount)?.text = "${files.size} file"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    class FileAdapter(val files: List<File>, val onClick: (File)->Unit) : RecyclerView.Adapter<FileAdapter.VH>() {
        inner class VH(v: View): RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_file_grid, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = files[pos]
            h.itemView.findViewById<TextView>(R.id.fileName).text = f.name
            h.itemView.findViewById<TextView>(R.id.fileMeta).text = "${f.length()/1024} KB"
            h.itemView.findViewById<TextView>(R.id.fileIcon).text = when(f.extension){"docx"->"📄";"xlsx"->"📊";"pptx"->"📽";else->"📁"}
            h.itemView.setOnClickListener { onClick(f) }
        }
        override fun getItemCount() = files.size
    }
}
