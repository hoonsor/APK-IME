package tw.hoonsor.ime.app.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** 鍵盤配色（深色，對照超注音「夜蘋果灰」與倬的截圖）。 */
object Palette {
    const val BG = 0xFF161616.toInt()
    const val PANEL_BG = 0xFF131313.toInt()
    const val KEY = 0xFF565656.toInt()
    const val KEY_DARK = 0xFF3A3A3A.toInt()
    const val KEY_PRESSED = 0xFF7A7A7A.toInt()
    const val TEXT = 0xFFA8D8EA.toInt()
    const val TEXT_BRIGHT = 0xFFE6F5FB.toInt()
    const val ICON = 0xFF8FC1D4.toInt()
    const val ACCENT = 0xFFFFD27A.toInt()
    const val PINK = 0xFFF2A7C3.toInt()
    const val HILITE_BG = 0xFFD9D9D9.toInt()
    const val HILITE_TEXT = 0xFF161616.toInt()
    const val AI_BG = 0xFF2F4A55.toInt()
    const val DIVIDER = 0xFF262626.toInt()
    const val FAN_TILE = 0xFF141414.toInt()
    const val FAN_BORDER = 0xFF9A9A9A.toInt()
    const val SCRIM = 0x59000000
    const val FAN_DISC = 0x21FFFFFF
    const val COMPOSE_BORDER = 0xFF4D7F91.toInt()
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Float): Int = context.dpi(v)

fun textPaint(color: Int, sizePx: Float, bold: Boolean = false): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx
        textAlign = Paint.Align.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

fun strokePaint(color: Int, widthPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = widthPx
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/** 讓文字在 y 中心垂直置中時的 baseline 位移。 */
fun Paint.centerOffset(): Float = -(fontMetrics.ascent + fontMetrics.descent) / 2f
