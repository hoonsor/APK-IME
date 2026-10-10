package tw.hoonsor.ime.app.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.contracts.EditorHints
import tw.hoonsor.ime.contracts.FieldType
import tw.hoonsor.ime.contracts.TextTarget

/**
 * 剪貼簿搜尋框的送字目標：搜尋模式時 ImeController.currentTarget 換成它，
 * 注音鍵盤打的字就進到搜尋框，而不是 App 的輸入框。
 */
class SearchTarget(private val onChange: (String) -> Unit) : TextTarget {
    private val sb = StringBuilder()
    val query: String get() = sb.toString()

    override val hints = EditorHints(packageName = null, isPassword = false, isMultiLine = false, fieldType = FieldType.SEARCH)

    override fun commitText(text: CharSequence) { sb.append(text); onChange(query) }
    override fun selectedText(): CharSequence? = null
    override fun replaceSelection(text: CharSequence) = commitText(text)
    override fun textBeforeCursor(n: Int): CharSequence = sb.takeLast(n)
    override fun textAfterCursor(n: Int): CharSequence = ""

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        if (sb.isEmpty()) return
        sb.setLength((sb.length - beforeLength).coerceAtLeast(0))
        onChange(query)
    }

    fun clear() { sb.setLength(0) }
}

/** 搜尋列：〔🔍 關鍵字〕〔結果1〕〔結果2〕…〔✕〕，放在 ImeController.searchSlot。 */
class ClipSearchStrip(context: Context) : LinearLayout(context) {

    var onPick: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val queryView = TextView(context)
    private val results = LinearLayout(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Palette.PANEL_BG)
        setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(4f))

        queryView.apply {
            setTextColor(Palette.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxWidth = dpi(130f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setPadding(dpi(8f), 0, dpi(8f), 0)
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Palette.KEY_DARK); cornerRadius = dp(8f) }
        }
        addView(queryView, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(40f)))

        results.orientation = HORIZONTAL
        results.gravity = Gravity.CENTER_VERTICAL
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(results)
        }, LayoutParams(0, dpi(44f), 1f).apply { marginStart = dpi(6f) })

        addView(TextView(context).apply {
            text = "✕"
            contentDescription = "結束搜尋"
            gravity = Gravity.CENTER
            setTextColor(Palette.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setOnClickListener { onClose?.invoke() }
        }, LayoutParams(dpi(44f), dpi(44f)))
    }

    fun show(query: String, clips: List<ClipboardHistory.Clip>) {
        queryView.text = if (query.isEmpty()) "🔍 打字搜尋…" else "🔍 $query"
        results.removeAllViews()
        if (clips.isEmpty()) {
            results.addView(TextView(context).apply {
                text = "沒有符合的內容"
                setTextColor(0xFF8A8A8A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
        }
        for (c in clips.take(30)) {
            results.addView(TextView(context).apply {
                text = c.text.replace('\n', ' ')
                maxWidth = dpi(200f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Palette.TEXT_BRIGHT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dpi(10f), 0, dpi(10f), 0)
                background = GradientDrawable().apply { setColor(Palette.KEY); cornerRadius = dp(8f) }
                setOnClickListener { onPick?.invoke(c.text) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(38f)).apply { marginEnd = dpi(6f) })
        }
    }
}
