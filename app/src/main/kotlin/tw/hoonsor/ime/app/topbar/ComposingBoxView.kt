package tw.hoonsor.ime.app.topbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.strokePaint

/**
 * 組字框（K11）：浮在候選列左上方的外框小方塊，每個字的注音以虛線分隔，例如「ㄋㄧ ┆ ㄏㄠ」。
 * 本 View 佔據輸入法頂端的透明區，背景透明；只有方塊本身有底色。
 */
class ComposingBoxView(context: Context) : View(context) {

    private var segments: List<String> = emptyList()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT
        textSize = dp(17f)
        letterSpacing = 0.06f
    }
    private val box = fillPaint(Palette.BG)
    private val border = strokePaint(Palette.COMPOSE_BORDER, dp(1f))
    private val dash = strokePaint(Palette.TEXT, dp(1.5f)).apply {
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }

    fun setSegments(segs: List<String>) {
        if (segs == segments) return
        segments = segs
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (segments.isEmpty()) return
        val pad = dp(6f)
        val gap = dp(12f)
        val widths = segments.map { text.measureText(it) }
        val w = widths.sum() + gap * (segments.size - 1) + pad * 2
        val r = RectF(dp(4f), dp(2f), dp(4f) + w, height.toFloat() + dp(6f))
        c.drawRoundRect(r, dp(6f), dp(6f), box)
        c.drawRoundRect(r, dp(6f), dp(6f), border)
        var x = r.left + pad
        val baseline = height / 2f + dp(2f) - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2f
        segments.forEachIndexed { i, s ->
            if (i > 0) {
                val dx = x - gap / 2
                c.drawLine(dx, r.top + dp(5f), dx, height.toFloat() - dp(3f), dash)
            }
            c.drawText(s, x, baseline, text)
            x += widths[i] + gap
        }
    }
}
