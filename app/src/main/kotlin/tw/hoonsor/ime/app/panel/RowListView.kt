package tw.hoonsor.ime.app.panel

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.dpi

/**
 * 面板區用的清單（剪貼簿、常用短語）：上方一列按鈕，下方可捲動的項目。
 * 只用框架元件，項目數量不大（≤ 200），每次重建即可。
 */
class RowListView(context: Context) : LinearLayout(context) {

    data class Action(val label: String, val highlight: Boolean = false, val onClick: () -> Unit)

    data class Row(
        val text: String,
        val sub: String? = null,
        val pinned: Boolean = false,
        val onClick: () -> Unit,
        val actions: List<Action> = emptyList(),
    )

    private val header = LinearLayout(context)
    private val title = TextView(context)
    private val scroll = ScrollView(context)
    private val list = LinearLayout(context)
    private val empty = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.PANEL_BG)

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(4f))
        title.setTextColor(Palette.TEXT)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(header)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dpi(48f)))

        list.orientation = VERTICAL
        list.setPadding(dpi(6f), 0, dpi(6f), dpi(6f))
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        empty.setTextColor(0xFF8A8A8A.toInt())
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        empty.setPadding(dpi(12f), dpi(16f), dpi(12f), dpi(16f))
    }

    fun setHeader(titleText: String?, actions: List<Action>) {
        header.removeAllViews()
        for (a in actions) header.addView(button(a), LayoutParams(LayoutParams.WRAP_CONTENT, dpi(40f)).apply { marginEnd = dpi(6f) })
        if (titleText != null) {
            title.text = titleText
            header.addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(6f) })
        }
    }

    fun setRows(rows: List<Row>, emptyText: String) {
        list.removeAllViews()
        if (rows.isEmpty()) {
            empty.text = emptyText
            list.addView(empty)
        }
        for (r in rows) list.addView(rowView(r), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6f) })
        scroll.scrollTo(0, 0)
    }

    private fun rowView(r: Row): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(if (r.pinned) 0xFF2B3A40.toInt() else Palette.KEY_DARK)
                cornerRadius = dp(9f)
            }
            setPadding(dpi(12f), dpi(8f), dpi(6f), dpi(8f))
            minimumHeight = dpi(52f)
        }
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(TextView(context).apply {
            text = r.text
            setTextColor(Palette.TEXT_BRIGHT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (r.sub != null) texts.addView(TextView(context).apply {
            text = r.sub
            setTextColor(0xFF8A8A8A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        texts.setOnClickListener { r.onClick() }
        row.addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        for (a in r.actions) row.addView(button(a), LayoutParams(dpi(44f), dpi(40f)).apply { marginStart = dpi(4f) })
        return row
    }

    private fun button(a: Action): TextView = TextView(context).apply {
        text = a.label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(if (a.highlight) Palette.HILITE_TEXT else Palette.TEXT)
        setPadding(dpi(12f), 0, dpi(12f), 0)
        background = GradientDrawable().apply {
            setColor(if (a.highlight) Palette.ACCENT else Palette.KEY)
            cornerRadius = dp(8f)
        }
        setOnClickListener { a.onClick() }
    }
}
