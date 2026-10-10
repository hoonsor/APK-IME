package tw.hoonsor.ime.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.centerOffset
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.strokePaint
import tw.hoonsor.ime.app.ui.textPaint
import kotlin.math.abs

/**
 * 自繪鍵盤（Canvas），用於注音、英文、符號三種版面。
 * 一般按鍵在放開時觸發；第二根手指按下時，先觸發第一根手指的按鍵（快速打字）。
 * 刪除鍵按住連續刪除；扇子鍵與標點鍵為手勢鍵。
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: KeyDef)
        fun onPunct(text: String)
        fun onFanStart(cx: Float, cy: Float)
        fun onFanMove(x: Float, y: Float)
        fun onFanEnd(x: Float, y: Float, dragged: Boolean)
    }

    var listener: Listener? = null
    var haptics = false
    var shiftOn = false
        set(v) { field = v; invalidate() }

    private var rows: List<List<KeyDef>> = emptyList()
    private val rects = ArrayList<Pair<KeyDef, RectF>>()

    private val gap = dp(4f)
    private val rowGap = dp(6f)
    private val padTop = dp(8f)
    private val padSide = dp(4f)
    private val radius = dp(9f)

    private val keyPaint = fillPaint(Palette.KEY)
    private val darkPaint = fillPaint(Palette.KEY_DARK)
    private val pressedPaint = fillPaint(Palette.KEY_PRESSED)
    private val labelPaint = textPaint(Palette.TEXT, dp(22f))
    private val smallPaint = textPaint(Palette.TEXT, dp(13f), bold = true)
    private val pinkPaint = textPaint(Palette.PINK, dp(13f), bold = true)
    private val iconStroke = strokePaint(Palette.TEXT, dp(2.4f))
    private val iconFill = fillPaint(Palette.TEXT)
    private val fanPaint = fillPaint(Palette.ICON)
    private val fanHole = fillPaint(Palette.KEY_DARK)
    private val crossPaint = strokePaint(Palette.KEY_DARK, dp(2f))
    private val enterPaint = strokePaint(Palette.TEXT, dp(3f))
    private val accentPaint = textPaint(Palette.ACCENT, dp(22f), true)
    private val path = Path()

    private var pressed: KeyDef? = null
    private var downX = 0f
    private var downY = 0f
    private var fanDragged = false
    private val handler = Handler(Looper.getMainLooper())
    private val repeat = object : Runnable {
        override fun run() {
            val k = pressed ?: return
            if (k.action == KeyAction.Backspace) {
                listener?.onKey(k)
                handler.postDelayed(this, 50)
            }
        }
    }

    fun setRows(newRows: List<List<KeyDef>>) {
        rows = newRows
        layoutKeys()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        rects.clear()
        if (rows.isEmpty() || width == 0 || height == 0) return
        val rowH = (height - padTop - rowGap * (rows.size - 1) - dp(4f)) / rows.size
        var y = padTop
        for (row in rows) {
            val totalW = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (width - padSide * 2 - gap * (row.size - 1)) / totalW
            var x = padSide
            for (k in row) {
                val w = unit * k.weight
                rects += k to RectF(x, y, x + w, y + rowH)
                x += w + gap
            }
            y += rowH + rowGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        for ((k, r) in rects) {
            val bg = when {
                k === pressed -> pressedPaint
                k.style == KeyStyle.DARK -> darkPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(r, radius, radius, bg)
            drawKeyFace(canvas, k, r)
        }
    }

    private fun drawKeyFace(c: Canvas, k: KeyDef, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        when (k.action) {
            KeyAction.Fan -> drawFan(c, cx, cy, dp(13f))
            KeyAction.Punct -> {
                val dx = r.width() * 0.18f
                val dy = r.height() * 0.17f
                c.drawText("？", cx - dx, cy - dy + smallPaint.centerOffset(), smallPaint)
                c.drawText("、", cx + dx, cy - dy + smallPaint.centerOffset(), smallPaint)
                c.drawText("，", cx - dx, cy + dy + pinkPaint.centerOffset(), pinkPaint)
                c.drawText("！", cx + dx, cy + dy + smallPaint.centerOffset(), smallPaint)
            }
            KeyAction.Space -> {
                val w = dp(18f)
                val p = Path().apply {
                    moveTo(cx - w, cy + dp(1f)); lineTo(cx - w, cy + dp(7f))
                    lineTo(cx + w, cy + dp(7f)); lineTo(cx + w, cy + dp(1f))
                }
                c.drawPath(p, iconStroke)
            }
            KeyAction.Backspace -> {
                val w = dp(11f); val h = dp(8f)
                val p = Path().apply {
                    moveTo(cx - w - dp(6f), cy); lineTo(cx - w, cy - h); lineTo(cx + w, cy - h)
                    lineTo(cx + w, cy + h); lineTo(cx - w, cy + h); close()
                }
                c.drawPath(p, iconFill)
                c.drawLine(cx - dp(3f), cy - dp(4f), cx + dp(5f), cy + dp(4f), crossPaint)
                c.drawLine(cx + dp(5f), cy - dp(4f), cx - dp(3f), cy + dp(4f), crossPaint)
            }
            KeyAction.Enter -> {
                val p = Path().apply {
                    moveTo(cx + dp(10f), cy - dp(8f)); lineTo(cx + dp(10f), cy + dp(2f))
                    lineTo(cx - dp(9f), cy + dp(2f))
                    moveTo(cx - dp(4f), cy - dp(3f)); lineTo(cx - dp(9f), cy + dp(2f)); lineTo(cx - dp(4f), cy + dp(7f))
                }
                c.drawPath(p, enterPaint)
            }
            else -> {
                val p = if (k.action == KeyAction.Shift && shiftOn) accentPaint else labelPaint
                p.textSize = dp(k.textSizeSp)
                c.drawText(k.label, cx, cy + p.centerOffset(), p)
            }
        }
    }

    /** 風車狀扇子圖示。 */
    private fun drawFan(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = path
        for (i in 0 until 4) {
            c.save()
            c.rotate(90f * i, cx, cy)
            p.reset()
            p.moveTo(cx, cy)
            p.quadTo(cx + s * 0.1f, cy - s * 0.9f, cx + s * 0.75f, cy - s)
            p.quadTo(cx + s * 0.5f, cy - s * 0.35f, cx, cy)
            c.drawPath(p, fanPaint)
            c.restore()
        }
        c.drawCircle(cx, cy, s * 0.22f, fanHole)
    }

    fun keyCenter(action: KeyAction): Pair<Float, Float>? =
        rects.firstOrNull { it.first.action == action }?.second?.let { it.centerX() to it.centerY() }

    private fun keyAt(x: Float, y: Float): KeyDef? {
        rects.firstOrNull { it.second.contains(x, y) }?.let { return it.first }
        // 落在按鍵間隙時，取最近的按鍵
        return rects.minByOrNull { (_, r) ->
            val dx = maxOf(r.left - x, 0f, x - r.right)
            val dy = maxOf(r.top - y, 0f, y - r.bottom)
            dx * dx + dy * dy
        }?.first
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        try {
            handleTouch(e)
        } catch (ex: Exception) {
            android.util.Log.e("KeyboardView", "touch error", ex)
            pressed = null
            invalidate()
        }
        return true
    }

    private fun handleTouch(e: MotionEvent) {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 快速打字：先送出前一個按鍵
                releaseKey(downX, downY)
                press(e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                val k = pressed ?: return
                if (k.action == KeyAction.Fan) {
                    val x = e.getX(0); val y = e.getY(0)
                    if (abs(x - downX) > dp(10f) || abs(y - downY) > dp(10f)) fanDragged = true
                    listener?.onFanMove(x, y)
                }
            }
            MotionEvent.ACTION_UP -> releaseKey(e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_POINTER_UP -> {
                // 只處理目前按下的那根手指
                if (pressed != null && e.getPointerId(idx) == 0) releaseKey(e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_CANCEL -> cancelPress()
        }
    }

    private fun press(x: Float, y: Float) {
        val k = keyAt(x, y) ?: return
        pressed = k
        downX = x; downY = y
        fanDragged = false
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (k.action) {
            KeyAction.Backspace -> {
                listener?.onKey(k)
                handler.postDelayed(repeat, 400)
            }
            KeyAction.Fan -> keyCenter(KeyAction.Fan)?.let { (cx, cy) -> listener?.onFanStart(cx, cy) }
            else -> {}
        }
        invalidate()
    }

    private fun releaseKey(x: Float, y: Float) {
        val k = pressed ?: return
        handler.removeCallbacks(repeat)
        pressed = null
        when (k.action) {
            KeyAction.Backspace -> {} // 已在按下時送出
            KeyAction.Fan -> listener?.onFanEnd(x, y, fanDragged)
            KeyAction.Punct -> listener?.onPunct(punctFor(x - downX, y - downY))
            else -> listener?.onKey(k)
        }
        invalidate()
    }

    private fun cancelPress() {
        handler.removeCallbacks(repeat)
        if (pressed?.action == KeyAction.Fan) listener?.onFanEnd(downX, downY, true)
        pressed = null
        invalidate()
    }

    /** 標點手勢：點 = ，；↖ ？；↗ 、；↘ ！；↙ ， */
    private fun punctFor(dx: Float, dy: Float): String {
        val t = dp(12f)
        if (abs(dx) < t && abs(dy) < t) return "，"
        return if (dy < 0) (if (dx < 0) "？" else "、") else (if (dx < 0) "，" else "！")
    }
}
