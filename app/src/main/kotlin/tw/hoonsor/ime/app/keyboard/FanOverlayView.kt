package tw.hoonsor.ime.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.centerOffset
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.fillPaint
import tw.hoonsor.ime.app.ui.strokePaint
import tw.hoonsor.ime.app.ui.textPaint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 扇子選單（K3）：單圈扇形，方塊與扇子鍵以放射線相連（依倬的截圖）。
 * - 按住扇子鍵滑向方塊放開 → 選擇。
 * - 點一下扇子鍵（不滑動）→ 扇子保持開啟，再點方塊選擇，點其他地方關閉。
 */
class FanOverlayView(context: Context) : View(context) {

    var onSelect: ((FanItem) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    var items: List<FanItem> = FanItem.entries.toList()
        set(v) { field = v; layoutTiles(); invalidate() }

    private var ox = 0f
    private var oy = 0f
    private val radius = dp(150f)
    private val tile = dp(46f)
    private val tiles = ArrayList<Pair<FanItem, RectF>>()
    private var hot: FanItem? = null

    /** true = 點一下開啟的「停留」模式，由本 View 處理觸控。 */
    var sticky = false
        private set

    private val scrim = fillPaint(Palette.SCRIM)
    private val disc = fillPaint(Palette.FAN_DISC)
    private val spoke = strokePaint(0x8CE6E6E6.toInt(), dp(2f))
    private val tileBg = fillPaint(Palette.FAN_TILE)
    private val tileHot = fillPaint(Palette.ACCENT)
    private val tileBorder = strokePaint(Palette.FAN_BORDER, dp(1.5f))
    private val label = textPaint(0xFFF2F2F2.toInt(), dp(20f), bold = true)
    private val labelHot = textPaint(Palette.HILITE_TEXT, dp(20f), bold = true)

    fun open(cx: Float, cy: Float) {
        ox = cx; oy = cy
        hot = null
        sticky = false
        layoutTiles()
        visibility = VISIBLE
        invalidate()
    }

    fun close() {
        visibility = GONE
        sticky = false
        hot = null
    }

    /** 拖曳中更新高亮方塊。 */
    fun track(x: Float, y: Float) {
        val h = hitTest(x, y)
        if (h != hot) { hot = h; invalidate() }
    }

    /** 放開時：有命中方塊就選擇；沒拖曳（點一下）就切換為停留模式。 */
    fun release(x: Float, y: Float, dragged: Boolean) {
        val h = hitTest(x, y)
        when {
            h != null -> { close(); onSelect?.invoke(h) }
            !dragged -> { sticky = true; hot = null; invalidate() }
            else -> { close(); onDismiss?.invoke() }
        }
    }

    private fun hitTest(x: Float, y: Float): FanItem? {
        // 方塊本身，或方塊方向上、離中心夠遠的位置都算命中（滑動不必精準）
        tiles.firstOrNull { it.second.contains(x, y) }?.let { return it.first }
        val d = hypot(x - ox, y - oy)
        if (d < radius * 0.45f) return null
        return tiles.minByOrNull { (_, r) -> hypot(r.centerX() - x, r.centerY() - y) }
            ?.takeIf { (_, r) -> hypot(r.centerX() - x, r.centerY() - y) < radius * 0.6f }?.first
    }

    private fun layoutTiles() {
        tiles.clear()
        // 尚未量測寬度時不計算（否則 coerceIn 範圍無效會讓輸入法當掉）；onSizeChanged 會再呼叫一次
        if (items.isEmpty() || width <= tile + dp(8f)) return
        val from = -106.0
        val to = if (items.size <= 4) -28.0 else -8.0
        items.forEachIndexed { i, item ->
            val t = if (items.size == 1) 0.5 else i.toDouble() / (items.size - 1)
            val a = Math.toRadians(from + (to - from) * t)
            val cx = (ox + radius * cos(a)).toFloat().coerceIn(tile / 2 + dp(4f), width - tile / 2 - dp(4f))
            val cy = (oy + radius * sin(a)).toFloat()
            tiles += item to RectF(cx - tile / 2, cy - tile / 2, cx + tile / 2, cy + tile / 2)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutTiles()

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        canvas.drawCircle(ox, oy, radius + tile * 0.6f, disc)
        for ((_, r) in tiles) canvas.drawLine(ox, oy, r.centerX(), r.centerY(), spoke)
        val rr = dp(10f)
        for ((item, r) in tiles) {
            val isHot = item == hot
            canvas.drawRoundRect(r, rr, rr, if (isHot) tileHot else tileBg)
            canvas.drawRoundRect(r, rr, rr, tileBorder)
            val p = if (isHot) labelHot else label
            canvas.drawText(item.glyph, r.centerX(), r.centerY() + p.centerOffset(), p)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!sticky) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> track(e.x, e.y)
            MotionEvent.ACTION_UP -> {
                val h = tiles.firstOrNull { it.second.contains(e.x, e.y) }?.first
                close()
                if (h != null) onSelect?.invoke(h) else onDismiss?.invoke()
            }
        }
        return true
    }
}
