package tw.hoonsor.ime.app.topbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.centerOffset
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.strokePaint
import tw.hoonsor.ime.app.ui.textPaint

/** 工具列按鈕（K9）。CLIPBOARD 為剪貼簿預留，預設隱藏。 */
enum class ToolButton(val desc: String) {
    SETTINGS("設定選單"),
    MIC("語音輸入"),
    TRAD_SIMP("繁簡切換"),
    LEFT("游標左移"),
    RIGHT("游標右移"),
    CLIPBOARD("剪貼簿"),
    EDIT("編輯面板"),
    HIDE("收起鍵盤"),
}

/** 自繪工具列：⚙ 🎤 繁 ← → (📋) ☰ ⌨，等分排列。 */
class ToolbarView(context: Context) : View(context) {

    var onTap: ((ToolButton) -> Unit)? = null

    var buttons: List<ToolButton> = ToolButton.entries.filter { it != ToolButton.CLIPBOARD }
        set(v) { field = v; requestLayout(); invalidate() }

    /** 高亮中的按鈕（面板開啟時）。 */
    private val active = HashSet<ToolButton>()
    var simplified = false
        set(v) { field = v; invalidate() }

    private val stroke = strokePaint(Palette.ICON, dp(2.5f))
    private val fill = fillPaint(Palette.ICON)
    private val glyph = textPaint(Palette.ICON, dp(30f), bold = true)
    private val activeBg = fillPaint(0xFF2A2A2A.toInt())
    private val bgPaint = fillPaint(Palette.BG)
    private val divider = fillPaint(Palette.DIVIDER)
    private val path = Path()
    private var pressed: ToolButton? = null

    fun setActive(b: ToolButton, on: Boolean) {
        if (on) active.add(b) else active.remove(b)
        invalidate()
    }

    private fun slot(i: Int): RectF {
        val w = width.toFloat() / buttons.size
        return RectF(i * w, 0f, (i + 1) * w, height.toFloat())
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        c.drawRect(0f, height - dp(1f), width.toFloat(), height.toFloat(), divider)
        buttons.forEachIndexed { i, b ->
            val r = slot(i)
            val cx = r.centerX(); val cy = r.centerY()
            if (b in active || b == pressed) {
                val s = dp(25f)
                c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, dp(10f), dp(10f), activeBg)
            }
            drawIcon(c, b, cx, cy)
        }
    }

    private fun drawIcon(c: Canvas, b: ToolButton, cx: Float, cy: Float) {
        // 圖示放大約 30%（倬需求十二），按鈕數量不變
        val s = dp(14.5f)
        when (b) {
            ToolButton.SETTINGS -> {
                c.drawCircle(cx, cy, s * 0.42f, stroke)
                for (i in 0 until 8) {
                    c.save(); c.rotate(45f * i, cx, cy)
                    c.drawRect(cx - s * 0.16f, cy - s * 1.05f, cx + s * 0.16f, cy - s * 0.62f, fill)
                    c.restore()
                }
                c.drawCircle(cx, cy, s * 0.72f, stroke)
            }
            ToolButton.MIC -> {
                c.drawRoundRect(cx - s * 0.38f, cy - s, cx + s * 0.38f, cy + s * 0.25f, s * 0.38f, s * 0.38f, fill)
                path.reset()
                path.addArc(cx - s * 0.7f, cy - s * 0.6f, cx + s * 0.7f, cy + s * 0.55f, 0f, 180f)
                c.drawPath(path, stroke)
                c.drawLine(cx, cy + s * 0.55f, cx, cy + s * 0.95f, stroke)
                c.drawLine(cx - s * 0.45f, cy + s * 0.95f, cx + s * 0.45f, cy + s * 0.95f, stroke)
            }
            ToolButton.TRAD_SIMP ->
                c.drawText(if (simplified) "簡" else "繁", cx, cy + glyph.centerOffset(), glyph)
            ToolButton.LEFT, ToolButton.RIGHT -> {
                val d = if (b == ToolButton.LEFT) -1 else 1
                path.reset()
                path.moveTo(cx + d * s, cy)
                path.lineTo(cx, cy - s * 0.8f); path.lineTo(cx, cy - s * 0.35f)
                path.lineTo(cx - d * s, cy - s * 0.35f); path.lineTo(cx - d * s, cy + s * 0.35f)
                path.lineTo(cx, cy + s * 0.35f); path.lineTo(cx, cy + s * 0.8f); path.close()
                c.drawPath(path, fill)
            }
            ToolButton.CLIPBOARD -> {
                c.drawRoundRect(cx - s * 0.75f, cy - s * 0.8f, cx + s * 0.75f, cy + s, dp(3f), dp(3f), stroke)
                c.drawRect(cx - s * 0.35f, cy - s, cx + s * 0.35f, cy - s * 0.6f, fill)
                c.drawLine(cx - s * 0.4f, cy - s * 0.1f, cx + s * 0.4f, cy - s * 0.1f, stroke)
                c.drawLine(cx - s * 0.4f, cy + s * 0.4f, cx + s * 0.4f, cy + s * 0.4f, stroke)
            }
            ToolButton.EDIT -> {
                c.drawRect(cx - s, cy - s, cx + s, cy + s, stroke)
                for (k in -1..1) c.drawLine(cx - s * 0.6f, cy + k * s * 0.45f, cx + s * 0.6f, cy + k * s * 0.45f, stroke)
            }
            ToolButton.HIDE -> {
                c.drawRoundRect(cx - s * 1.2f, cy - s * 0.85f, cx + s * 1.2f, cy + s * 0.45f, dp(3f), dp(3f), stroke)
                for (k in -2..2) c.drawPoint(cx + k * s * 0.42f, cy - s * 0.35f, stroke)
                c.drawLine(cx - s * 0.6f, cy + s * 0.05f, cx + s * 0.6f, cy + s * 0.05f, stroke)
                path.reset()
                path.moveTo(cx - s * 0.35f, cy + s * 0.75f); path.lineTo(cx, cy + s * 1.1f); path.lineTo(cx + s * 0.35f, cy + s * 0.75f)
                path.close()
                c.drawPath(path, fill)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (buttons.isEmpty()) return false
        val i = (e.x / (width.toFloat() / buttons.size)).toInt().coerceIn(0, buttons.size - 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = buttons[i]; invalidate() }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                pressed = null
                invalidate()
                if (p != null && p == buttons[i]) onTap?.invoke(p)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }
}
