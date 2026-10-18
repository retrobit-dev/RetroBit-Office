package com.retrobit.office.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.retrobit.office.R

data class ToolItem(val id: String, val iconRes: Int, val label: String)
data class ToolCategory(val id: String, val iconRes: Int, val label: String, val tools: List<ToolItem>)

/**
 * ToolSidebar — rail kategori vertikal + panel tool per kategori (ala cyberdeck).
 * Klik kategori -> panel tool kategori itu terbuka; klik lagi -> tutup.
 * onToolSelected(categoryId, toolId).
 */
class ToolSidebar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onToolSelected: ((String, String) -> Unit)? = null

    private var categories: List<ToolCategory> = emptyList()
    private var activeCategoryId: String? = null
    private val railButtons = mutableMapOf<String, LinearLayout>()
    private var panelTitle: TextView
    private var panelTools: LinearLayout
    private var panel: LinearLayout
    private val density = resources.displayMetrics.density

    private fun dp(v: Int): Int = (v * density).toInt()

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Color.TRANSPARENT)

        // ---- Rail ----
        val rail = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(context.getColor(R.color.panel))
        }
        addView(rail, LayoutParams(dp(52), LayoutParams.MATCH_PARENT))

        // Divider rail
        addView(divider(), LayoutParams(dp(1), LayoutParams.MATCH_PARENT))

        // ---- Panel ----
        panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(context.getColor(R.color.panel))
            visibility = GONE
        }
        addView(panel, LayoutParams(dp(168), LayoutParams.MATCH_PARENT))
        addView(divider(), LayoutParams(dp(1), LayoutParams.MATCH_PARENT))

        // Header panel
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setBackgroundColor(context.getColor(R.color.ink_900))
        }
        panelTitle = TextView(context).apply {
            setTextColor(context.getColor(R.color.invert_ink))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            letterSpacing = 0.1f
        }
        val close = TextView(context).apply {
            text = "[X]"
            setTextColor(context.getColor(R.color.invert_ink))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { collapse() }
        }
        header.addView(panelTitle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)
        panel.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        panelTools = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        panel.addView(panelTools, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ---- isi rail ----
        // (diisi oleh setCategories)
        rail.tag = rail // anchor untuk setCategories
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(context.getColor(R.color.line))
    }

    fun setCategories(cats: List<ToolCategory>) {
        categories = cats
        val rail = getChildAt(0) as LinearLayout
        rail.removeAllViews()
        railButtons.clear()
        cats.forEach { cat ->
            val btn = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_rail_item)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                tooltipText = cat.label
                contentDescription = cat.label
                setOnClickListener { onCategoryClick(cat.id) }
            }
            val icon = ImageView(context).apply {
                setImageResource(cat.iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.ink_600))
            }
            btn.addView(icon, LayoutParams(dp(22), dp(22)))
            rail.addView(btn, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
            railButtons[cat.id] = btn
        }
    }

    private fun onCategoryClick(catId: String) {
        if (activeCategoryId == catId && panel.visibility == VISIBLE) {
            collapse()
            return
        }
        selectCategory(catId)
    }

    fun selectCategory(catId: String) {
        val cat = categories.firstOrNull { it.id == catId } ?: return
        activeCategoryId = catId
        railButtons.forEach { (id, btn) ->
            val sel = id == catId
            btn.isSelected = sel
            (btn.getChildAt(0) as ImageView).imageTintList =
                android.content.res.ColorStateList.valueOf(
                    context.getColor(if (sel) R.color.invert_ink else R.color.ink_600)
                )
        }
        panelTitle.text = cat.label
        panelTools.removeAllViews()
        val cols = 3
        var row: LinearLayout? = null
        cat.tools.forEachIndexed { i, tool ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                panelTools.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            val btn = buildToolButton(cat.id, tool)
            row!!.addView(btn, LayoutParams(0, dp(50), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        panel.visibility = VISIBLE
    }

    fun collapse() {
        panel.visibility = GONE
        activeCategoryId = null
        railButtons.values.forEach { btn ->
            btn.isSelected = false
            (btn.getChildAt(0) as ImageView).imageTintList =
                android.content.res.ColorStateList.valueOf(context.getColor(R.color.ink_600))
        }
    }

    fun isOpen(): Boolean = panel.visibility == VISIBLE

    private fun buildToolButton(catId: String, tool: ToolItem): LinearLayout {
        val btn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_tool_square)
            tooltipText = tool.label
            contentDescription = tool.label
            setOnClickListener { onToolSelected?.invoke(catId, tool.id) }
        }
        val icon = ImageView(context).apply {
            setImageResource(tool.iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.ink_900))
        }
        btn.addView(icon, LayoutParams(dp(22), dp(22)))
        return btn
    }
}
