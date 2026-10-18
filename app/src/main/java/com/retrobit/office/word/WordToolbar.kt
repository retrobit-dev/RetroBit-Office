package com.retrobit.office.word

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.retrobit.office.R

/**
 * MS Word-like Toolbar - Home, Insert, Draw, Layout, Review, View
 */
class WordToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onFormattingChanged: ((WordToolbarAction) -> Unit)? = null
    var onTabSelected: ((String) -> Unit)? = null

    sealed class WordToolbarAction {
        data class Bold(val enabled: Boolean) : WordToolbarAction()
        data class Italic(val enabled: Boolean) : WordToolbarAction()
        data class Underline(val enabled: Boolean) : WordToolbarAction()
        data class Align(val align: Alignment) : WordToolbarAction()
        data class FontSize(val sizePt: Float) : WordToolbarAction()
        data class FontColor(val color: Int) : WordToolbarAction()
        data class Bullet(val style: BulletStyle) : WordToolbarAction()
        object Undo : WordToolbarAction()
        object Redo : WordToolbarAction()
        object InsertTable : WordToolbarAction()
        object InsertImage : WordToolbarAction()
        object Print : WordToolbarAction()
        object Save : WordToolbarAction()
    }

    private val tabLayout: TabLayout
    private val actionContainer: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 6f

        // Tabs
        tabLayout = TabLayout(context).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            setSelectedTabIndicatorColor(Color.parseColor("#185ABC"))
        }
        listOf("Beranda", "Sisipkan", "Gambar", "Tata Letak", "Tinjau", "Tampilan").forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        addView(tabLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Action bar
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        actionContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(16, 12, 16, 12)
        }
        scroll.addView(actionContainer)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onTabSelected?.invoke(tab.text.toString())
                showActionsForTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        showActionsForTab(0)
    }

    private fun showActionsForTab(index: Int) {
        actionContainer.removeAllViews()
        when (index) {
            0 -> showHomeActions()
            1 -> showInsertActions()
            2 -> showDrawActions()
            3 -> showLayoutActions()
            4 -> showReviewActions()
            5 -> showViewActions()
        }
    }

    private fun showHomeActions() {
        addToggleButton("B", bold = true) { checked ->
            onFormattingChanged?.invoke(WordToolbarAction.Bold(checked))
        }
        addToggleButton("I", italic = true) { checked ->
            onFormattingChanged?.invoke(WordToolbarAction.Italic(checked))
        }
        addToggleButton("U", underline = true) { checked ->
            onFormattingChanged?.invoke(WordToolbarAction.Underline(checked))
        }
        addDivider()
        addActionButton("A+") { onFormattingChanged?.invoke(WordToolbarAction.FontSize(14f)) }
        addActionButton("A-") { onFormattingChanged?.invoke(WordToolbarAction.FontSize(10f)) }
        addDivider()
        addActionButton("≡") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.LEFT)) }
        addActionButton("≡≡") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.CENTER)) }
        addActionButton("≡≡≡") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.RIGHT)) }
        addActionButton("☰") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.JUSTIFY)) }
        addDivider()
        addActionButton("•") { onFormattingChanged?.invoke(WordToolbarAction.Bullet(BulletStyle.BULLET)) }
        addActionButton("1.") { onFormattingChanged?.invoke(WordToolbarAction.Bullet(BulletStyle.NUMBER)) }
        addDivider()
        addColorButton(Color.BLACK)
        addColorButton(Color.RED)
        addColorButton(Color.BLUE)
    }

    private fun showInsertActions() {
        addActionButton("⊞ Tabel") { onFormattingChanged?.invoke(WordToolbarAction.InsertTable) }
        addActionButton("🖼 Gambar") { onFormattingChanged?.invoke(WordToolbarAction.InsertImage) }
        addActionButton("🔗 Link") {}
        addActionButton("📄 Header") {}
        addActionButton("📑 Footer") {}
        addActionButton("🔢 Halaman") {}
    }

    private fun showDrawActions() {
        addActionButton("✏️ Pena") {}
        addActionButton("🖊 Stabilo") {}
        addActionButton("✎ Pensil") {}
        addActionButton("🧹 Hapus") {}
        addActionButton("◯ Bentuk") {}
        addActionButton("↩️ Urungkan") { onFormattingChanged?.invoke(WordToolbarAction.Undo) }
        addActionButton("↪️ Ulangi") { onFormattingChanged?.invoke(WordToolbarAction.Redo) }
    }

    private fun showLayoutActions() {
        addActionButton("↔️ Margin") {}
        addActionButton("📄 A4") {}
        addActionButton("🔄 Orientasi") {}
        addActionButton("📏 Kolom") {}
        addActionButton("↕️ Spasi") {}
    }

    private fun showReviewActions() {
        addActionButton("✓ Eja") {}
        addActionButton("💬 Komentar") {}
        addActionButton("🔍 Cari") {}
        addActionButton("🌐 Terjemah") {}
    }

    private fun showViewActions() {
        addActionButton("👁 Baca") {}
        addActionButton("🔍 100%") {}
        addActionButton("🖨 Cetak") { onFormattingChanged?.invoke(WordToolbarAction.Print) }
        addActionButton("💾 Simpan") { onFormattingChanged?.invoke(WordToolbarAction.Save) }
    }

    private fun addToggleButton(text: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, onChecked: (Boolean) -> Unit) {
        val btn = MaterialButton(context).apply {
            this.text = text
            isCheckable = true
            setPadding(18, 8, 18, 8)
            textSize = 13f
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (italic) setTypeface(typeface, android.graphics.Typeface.ITALIC)
            if (underline) paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        }
        btn.addOnCheckedChangeListener { _, isChecked ->
            btn.setBackgroundColor(if (isChecked) Color.parseColor("#E8F0FE") else Color.TRANSPARENT)
            onChecked(isChecked)
        }
        actionContainer.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) })
    }

    private fun addActionButton(text: String, onClick: () -> Unit = {}) {
        val btn = MaterialButton(context).apply {
            this.text = text
            setPadding(16, 8, 16, 8)
            textSize = 12f
        }
        btn.setOnClickListener { onClick() }
        actionContainer.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) })
    }

    private fun addColorButton(color: Int) {
        val btn = MaterialButton(context).apply {
            text = "●"
            setTextColor(color)
            iconTint = null
            setPadding(8, 8, 8, 8)
        }
        btn.setOnClickListener { onFormattingChanged?.invoke(WordToolbarAction.FontColor(color)) }
        actionContainer.addView(btn, LayoutParams(90, LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) })
    }

    private fun addDivider() {
        val v = android.view.View(context).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) }
        actionContainer.addView(v, LayoutParams(2, 60).apply { setMargins(8,0,8,0) })
    }
}
