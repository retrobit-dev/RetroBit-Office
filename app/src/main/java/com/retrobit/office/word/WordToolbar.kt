package com.retrobit.office.word

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.retrobit.office.R

/**
 * WordToolbar — ribbon tab ala MS Office / GenOffice.
 * Ikon pixel-art (pixelarticons), aksi dikirim via onFormattingChanged.
 */
class WordToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onFormattingChanged: ((WordToolbarAction) -> Unit)? = null

    private lateinit var tabLayout: TabLayout
    private lateinit var actionContainer: LinearLayout

    sealed class WordToolbarAction {
        data class Bold(val enabled: Boolean) : WordToolbarAction()
        data class Italic(val enabled: Boolean) : WordToolbarAction()
        data class Underline(val enabled: Boolean) : WordToolbarAction()
        data class Align(val align: Alignment) : WordToolbarAction()
        data class FontSize(val sizePt: Float) : WordToolbarAction()
        data class FontColor(val color: Int) : WordToolbarAction()
        data class Bullet(val style: BulletStyle) : WordToolbarAction()
        object InsertTable : WordToolbarAction()
        object InsertImage : WordToolbarAction()
        object Undo : WordToolbarAction()
        object Redo : WordToolbarAction()
        object WordCount : WordToolbarAction()
        object PageLayout : WordToolbarAction()
        object ToggleOrientation : WordToolbarAction()
        object PaperSize : WordToolbarAction()
        object Margins : WordToolbarAction()
        object ZoomIn : WordToolbarAction()
        object ZoomOut : WordToolbarAction()
        object Pen : WordToolbarAction()
        object Print : WordToolbarAction()
        object Save : WordToolbarAction()
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)

        tabLayout = TabLayout(context).apply {
            tabGravity = TabLayout.GRAVITY_FILL
            tabMode = TabLayout.MODE_SCROLLABLE
            setSelectedTabIndicatorColor(Color.parseColor("#2E6BE6"))
            setTabTextColors(Color.parseColor("#64676C"), Color.parseColor("#17181A"))
            elevation = 0f
        }
        listOf("Beranda", "Sisipkan", "Gambar", "Tata Letak", "Tinjau", "Tampilan").forEach {
            tabLayout.addTab(tabLayout.newTab().setText(it))
        }
        addView(tabLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        actionContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 6, 12, 8)
        }
        scroll.addView(actionContainer)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showActionsForTab(tab.position)
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
        addToggleButton(R.drawable.ic_pi_bold, "Tebal") { onFormattingChanged?.invoke(WordToolbarAction.Bold(it)) }
        addToggleButton(R.drawable.ic_pi_italic, "Miring") { onFormattingChanged?.invoke(WordToolbarAction.Italic(it)) }
        addToggleButton(R.drawable.ic_pi_underline, "Garis bawah") { onFormattingChanged?.invoke(WordToolbarAction.Underline(it)) }
        addDivider()
        addActionButton(R.drawable.ic_pi_type_size, "A+") { onFormattingChanged?.invoke(WordToolbarAction.FontSize(14f)) }
        addActionButton(R.drawable.ic_pi_type_size, "A-") { onFormattingChanged?.invoke(WordToolbarAction.FontSize(10f)) }
        addDivider()
        addActionButton(R.drawable.ic_pi_text_align_left, "Kiri") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.LEFT)) }
        addActionButton(R.drawable.ic_pi_text_align_center, "Tengah") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.CENTER)) }
        addActionButton(R.drawable.ic_pi_text_align_right, "Kanan") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.RIGHT)) }
        addActionButton(R.drawable.ic_pi_text_align_justify, "Rata") { onFormattingChanged?.invoke(WordToolbarAction.Align(Alignment.JUSTIFY)) }
        addDivider()
        addActionButton(R.drawable.ic_pi_bulletlist, "Poin") { onFormattingChanged?.invoke(WordToolbarAction.Bullet(BulletStyle.BULLET)) }
        addActionButton(R.drawable.ic_pi_numbers, "Angka") { onFormattingChanged?.invoke(WordToolbarAction.Bullet(BulletStyle.NUMBER)) }
        addDivider()
        addColorButton(Color.parseColor("#17181A"))
        addColorButton(Color.parseColor("#2E6BE6"))
        addColorButton(Color.parseColor("#E5484D"))
        addColorButton(Color.parseColor("#1FA971"))
    }

    private fun showInsertActions() {
        addActionButton(R.drawable.ic_pi_table, "Tabel") { onFormattingChanged?.invoke(WordToolbarAction.InsertTable) }
        addActionButton(R.drawable.ic_pi_image, "Gambar") { onFormattingChanged?.invoke(WordToolbarAction.InsertImage) }
    }

    private fun showDrawActions() {
        addActionButton(R.drawable.ic_pi_pencil, "Pena") { onFormattingChanged?.invoke(WordToolbarAction.Pen) }
        addActionButton(R.drawable.ic_pi_eraser, "Penghapus") { onFormattingChanged?.invoke(WordToolbarAction.Undo) }
        addActionButton(R.drawable.ic_pi_undo, "Urungkan") { onFormattingChanged?.invoke(WordToolbarAction.Undo) }
    }

    private fun showLayoutActions() {
        addActionButton(R.drawable.ic_pi_page, "Kertas") { onFormattingChanged?.invoke(WordToolbarAction.PaperSize) }
        addActionButton(R.drawable.ic_pi_orientation, "Orientasi") { onFormattingChanged?.invoke(WordToolbarAction.ToggleOrientation) }
        addActionButton(R.drawable.ic_pi_margin, "Margin") { onFormattingChanged?.invoke(WordToolbarAction.Margins) }
    }

    private fun showReviewActions() {
        addActionButton(R.drawable.ic_pi_undo, "Urungkan") { onFormattingChanged?.invoke(WordToolbarAction.Undo) }
        addActionButton(R.drawable.ic_pi_redo, "Ulangi") { onFormattingChanged?.invoke(WordToolbarAction.Redo) }
        addActionButton(R.drawable.ic_pi_book_open, "Hitung kata") { onFormattingChanged?.invoke(WordToolbarAction.WordCount) }
    }

    private fun showViewActions() {
        addActionButton(R.drawable.ic_pi_zoom_in, "Perbesar") { onFormattingChanged?.invoke(WordToolbarAction.ZoomIn) }
        addActionButton(R.drawable.ic_pi_zoom_out, "Perkecil") { onFormattingChanged?.invoke(WordToolbarAction.ZoomOut) }
        addActionButton(R.drawable.ic_pi_printer, "Cetak") { onFormattingChanged?.invoke(WordToolbarAction.Print) }
        addActionButton(R.drawable.ic_pi_save, "Simpan") { onFormattingChanged?.invoke(WordToolbarAction.Save) }
    }

    // ---------- builders ----------

    private fun addToggleButton(iconRes: Int, label: String, onChecked: (Boolean) -> Unit) {
        val btn = MaterialButton(context).apply {
            isCheckable = true
            addOnCheckedChangeListener { _, isChecked ->
                setBackgroundColor(if (isChecked) Color.parseColor("#EAF1FE") else Color.TRANSPARENT)
                onChecked(isChecked)
            }
        }
        decorateToolButton(btn, iconRes, label)
        actionContainer.addView(btn, toolLayoutParams())
    }

    private fun addActionButton(iconRes: Int, label: String, onClick: () -> Unit) {
        val btn = MaterialButton(context).apply { setOnClickListener { onClick() } }
        decorateToolButton(btn, iconRes, label)
        actionContainer.addView(btn, toolLayoutParams())
    }

    private fun decorateToolButton(btn: MaterialButton, iconRes: Int, label: String) {
        btn.text = label
        btn.textSize = 10f
        btn.setTextColor(Color.parseColor("#17181A"))
        btn.setIcon(iconRes)
        btn.iconSize = (20 * resources.displayMetrics.density).toInt()
        btn.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        btn.iconPadding = 8
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        btn.strokeWidth = 0
        btn.gravity = Gravity.CENTER
        btn.setPadding(18, 10, 18, 10)
    }

    private fun addColorButton(color: Int) {
        val v = AppCompatImageView(context).apply {
            setImageResource(R.drawable.ic_pi_circle)
            setColorFilter(color)
            setPadding(10, 10, 10, 10)
            setOnClickListener { onFormattingChanged?.invoke(WordToolbarAction.FontColor(color)) }
        }
        actionContainer.addView(v, LayoutParams(72, 72).apply { marginStart = 2; marginEnd = 2 })
    }

    private fun addDivider() {
        val line = TextView(context).apply {
            setBackgroundColor(Color.parseColor("#E6E8EB"))
        }
        actionContainer.addView(line, LayoutParams(2, 56).apply { marginStart = 8; marginEnd = 8 })
    }

    private fun toolLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
        marginStart = 4; marginEnd = 4
    }
}
