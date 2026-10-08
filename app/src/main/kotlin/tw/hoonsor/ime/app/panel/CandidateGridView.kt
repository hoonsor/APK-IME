package tw.hoonsor.ime.app.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import tw.hoonsor.ime.app.topbar.CandItem
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.centerOffset
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.textPaint
import kotlin.math.abs

/** 下拉式候選視窗（K8）：覆蓋鍵盤區，候選詞自動換行，可上下滑動。 */
class CandidateGridView(context: Context) : ScrollView(context) {

    var onPick: ((Int) -> Unit)? = null
    private val flow = Flow(context)

    init {
        setBackgroundColor(Palette.PANEL_BG)
        isVerticalScrollBarEnabled = true
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setItems(items: List<CandItem>) {
        flow.items = items
        flow.requestLayout()
        flow.invalidate()
        scrollTo(0, 0)
    }

    private inner class Flow(context: Context) : View(context) {
        var items: List<CandItem> = emptyList()
        private val rects = ArrayList<RectF>()
        private val paint = textPaint(Palette.TEXT, dp(22f))
        private val hiPaint = textPaint(Palette.HILITE_TEXT, dp(22f))
        private val hiBg = fillPaint(Palette.HILITE_BG)
        private val cell = fillPaint(Palette.KEY_DARK)
        private val cellH = dp(48f)
        private val gap = dp(6f)
        private var downX = 0f
        private var downY = 0f

        private fun layoutCells(w: Int) {
            rects.clear()
            var x = gap; var y = gap
            for (it in items) {
                val cw = maxOf(paint.measureText(it.text) + dp(24f), dp(52f))
                if (x + cw > w - gap && x > gap) { x = gap; y += cellH + gap }
                rects += RectF(x, y, x + cw, y + cellH)
                x += cw + gap
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            layoutCells(w)
            val h = ((rects.lastOrNull()?.bottom ?: 0f) + gap).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(c: Canvas) {
            items.forEachIndexed { i, it ->
                val r = rects.getOrNull(i) ?: return
                c.drawRoundRect(r, dp(8f), dp(8f), if (it.highlight) hiBg else cell)
                val p = if (it.highlight) hiPaint else paint
                c.drawText(it.text, r.centerX(), r.centerY() + p.centerOffset(), p)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                MotionEvent.ACTION_UP -> if (abs(e.x - downX) < dp(10f) && abs(e.y - downY) < dp(10f)) {
                    val i = rects.indexOfFirst { it.contains(e.x, e.y) }
                    if (i >= 0) onPick?.invoke(i)
                }
            }
            return true
        }
    }
}
