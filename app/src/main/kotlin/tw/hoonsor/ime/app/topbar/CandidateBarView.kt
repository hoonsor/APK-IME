package tw.hoonsor.ime.app.topbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.app.ui.strokePaint

/** 候選列上的一個項目。 */
data class CandItem(
    val text: String,
    /** 第一個候選反白（K7）。 */
    val highlight: Boolean = false,
    /** U / C 來源標示（Z7），null 為系統詞。 */
    val badge: String? = null,
    /** 鄰近修正候選以較亮的藍色顯示（Z9，預留）。 */
    val near: Boolean = false,
    /** 黃色字：刪除確認、「＋加入字典」等操作項目。 */
    val accent: Boolean = false,
    /** 紅底：危險操作（刪除）。 */
    val danger: Boolean = false,
)

/** 候選列「特殊 chip」（剪貼簿最近內容，R2 預留）。 */
data class SpecialChip(val label: String)

/**
 * 候選列（K7）：最左 ▼ 展開、中間可左右滑動、最右 › 翻頁。
 * 最前面可放一個特殊 chip（剪貼簿預留）。TextView 重複使用，避免每次按鍵都建立新 View。
 */
class CandidateBarView(context: Context) : LinearLayout(context) {

    var onPick: ((Int) -> Unit)? = null
    var onLongPick: ((Int) -> Unit)? = null
    var onExpand: (() -> Unit)? = null
    var onChipClick: (() -> Unit)? = null

    private val scroll = HorizontalScrollView(context)
    private val row = LinearLayout(context)
    private val chipView = TextView(context)
    private val pool = ArrayList<TextView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Palette.BG)

        addView(arrowButton(down = true) { onExpand?.invoke() }, LayoutParams(dpi(40f), LayoutParams.MATCH_PARENT))

        scroll.isHorizontalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(arrowButton(down = false) { scroll.smoothScrollBy((scroll.width * 0.85f).toInt(), 0) },
            LayoutParams(dpi(32f), LayoutParams.MATCH_PARENT))

        chipView.apply {
            setTextColor(Palette.TEXT_BRIGHT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxWidth = dpi(180f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dpi(10f), 0, dpi(10f), 0)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Palette.AI_BG); cornerRadius = dp(16f) }
            setOnClickListener { onChipClick?.invoke() }
            visibility = GONE
        }
        row.addView(chipView, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(34f)).apply { marginEnd = dpi(6f) })
    }

    fun setChip(chip: SpecialChip?) {
        chipView.visibility = if (chip == null) GONE else VISIBLE
        chipView.text = chip?.label ?: ""
    }

    fun setItems(items: List<CandItem>) {
        while (pool.size < items.size) {
            val i = pool.size
            val tv = TextView(context).apply {
                // 字放大約 27%（22→28sp），左右留白 12→6dp 抵銷，一排顯示的詞數大致不變
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(dpi(6f), 0, dpi(6f), 0)
                maxLines = 1
                setOnTouchListener(PressListener(i))
            }
            pool += tv
            row.addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(48f)))
        }
        pool.forEachIndexed { i, tv ->
            val item = items.getOrNull(i)
            if (item == null) {
                tv.visibility = GONE
            } else {
                tv.visibility = VISIBLE
                val badge = when (item.badge) { "U" -> "ᵁ"; "C" -> "ᶜ"; else -> "" }
                tv.text = item.text + badge
                when {
                    item.danger -> { tv.setTextColor(0xFFFFFFFF.toInt()); tv.setBackgroundColor(0xFFB3403A.toInt()) }
                    item.accent -> { tv.setTextColor(Palette.ACCENT); tv.background = null }
                    item.highlight -> {
                        tv.setTextColor(Palette.HILITE_TEXT)
                        tv.setBackgroundColor(Palette.HILITE_BG)
                    }
                    item.near -> { tv.setTextColor(0xFF3FA9D6.toInt()); tv.background = null }
                    else -> { tv.setTextColor(Palette.TEXT); tv.background = null }
                }
            }
        }
        scroll.scrollTo(0, 0)
    }

    /**
     * 點擊與長按（長按需 0.8 秒，避免誤刪；倬需求六）。
     * 左右滑動時 HorizontalScrollView 會攔截觸控並送出 CANCEL，不會誤觸。
     */
    private inner class PressListener(private val index: Int) : OnTouchListener {
        private var fired = false
        private val long = Runnable {
            fired = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPick?.invoke(index)
        }

        override fun onTouch(v: View, e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    fired = false
                    v.isPressed = true
                    v.postDelayed(long, LONG_PRESS_MS)
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(long)
                    v.isPressed = false
                    if (!fired) onPick?.invoke(index)
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(long)
                    v.isPressed = false
                }
            }
            return true
        }
    }

    private fun arrowButton(down: Boolean, onClick: () -> Unit): View = object : View(context) {
        private val p = strokePaint(Palette.ICON, dp(2.4f))
        private val path = Path()
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val s = dp(8f)
            path.reset()
            if (down) { path.moveTo(cx - s, cy - s * 0.5f); path.lineTo(cx, cy + s * 0.5f); path.lineTo(cx + s, cy - s * 0.5f) }
            else { path.moveTo(cx - s * 0.4f, cy - s); path.lineTo(cx + s * 0.5f, cy); path.lineTo(cx - s * 0.4f, cy + s) }
            c.drawPath(path, p)
        }
    }.apply {
        contentDescription = if (down) "展開候選詞" else "下一頁候選詞"
        setOnClickListener { onClick() }
    }

    companion object {
        const val LONG_PRESS_MS = 800L
    }
}
