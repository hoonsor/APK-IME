package tw.hoonsor.ime.app.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.centerOffset
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.textPaint

/** 方塊格子中的一格。 */
data class Tile(
    val id: String,
    val label: String,
    val glyph: String,
    /** AI 欄用不同底色與字色。 */
    val accent: Boolean = false,
)

/**
 * 方塊格子（自繪）：用於編輯面板（K10）與齒輪選單（K9a）。
 * 支援點擊與長按（長按「貼上」開剪貼簿）。
 */
class TileGridView(context: Context) : View(context) {

    var columns = 4
    var onTap: ((Tile) -> Unit)? = null
    var onLongPress: ((Tile) -> Unit)? = null

    var tiles: List<Tile> = emptyList()
        set(v) { field = v; invalidate() }

    private val pad = dp(8f)
    private val gap = dp(6f)
    private val bg = fillPaint(Palette.PANEL_BG)
    private val tileBg = fillPaint(Palette.KEY)
    private val tileAi = fillPaint(Palette.AI_BG)
    private val tilePressed = fillPaint(Palette.KEY_PRESSED)
    private val glyphPaint = textPaint(Palette.TEXT, dp(20f))
    private val labelPaint = textPaint(Palette.TEXT, dp(16f), bold = true)
    private val glyphAi = textPaint(Palette.ACCENT, dp(20f))
    private val labelAi = textPaint(Palette.ACCENT, dp(15f), bold = true)

    private var pressed: Tile? = null
    private var longFired = false
    private val handler = Handler(Looper.getMainLooper())
    private val longRunnable = Runnable {
        val t = pressed ?: return@Runnable
        longFired = true
        onLongPress?.invoke(t)
    }

    private fun rectOf(i: Int): RectF {
        val rows = (tiles.size + columns - 1) / columns
        val w = (width - pad * 2 - gap * (columns - 1)) / columns
        val h = (height - pad * 2 - gap * (rows - 1)) / rows
        val c = i % columns
        val r = i / columns
        val x = pad + c * (w + gap)
        val y = pad + r * (h + gap)
        return RectF(x, y, x + w, y + h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val rr = dp(9f)
        tiles.forEachIndexed { i, t ->
            val r = rectOf(i)
            canvas.drawRoundRect(r, rr, rr, when {
                t === pressed -> tilePressed
                t.accent -> tileAi
                else -> tileBg
            })
            val gp = if (t.accent) glyphAi else glyphPaint
            val lp = if (t.accent) labelAi else labelPaint
            lp.textSize = dp(if (t.label.length > 2) 14f else 16f)
            canvas.drawText(t.glyph, r.centerX(), r.centerY() - dp(11f) + gp.centerOffset(), gp)
            canvas.drawText(t.label, r.centerX(), r.centerY() + dp(13f) + lp.centerOffset(), lp)
        }
    }

    private fun tileAt(x: Float, y: Float): Tile? =
        tiles.indices.firstOrNull { rectOf(it).contains(x, y) }?.let { tiles[it] }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = tileAt(e.x, e.y)
                longFired = false
                if (pressed != null) handler.postDelayed(longRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longRunnable)
                val t = pressed
                pressed = null
                invalidate()
                if (t != null && !longFired && tileAt(e.x, e.y) === t) onTap?.invoke(t)
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longRunnable)
                pressed = null
                invalidate()
            }
        }
        return true
    }
}
