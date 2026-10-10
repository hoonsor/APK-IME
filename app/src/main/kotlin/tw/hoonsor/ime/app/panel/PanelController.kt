package tw.hoonsor.ime.app.panel

import android.view.View
import android.widget.FrameLayout

/** 面板區顯示的內容（ARCHITECTURE §5.1）。KEYBOARD 內再分注音／英文／符號子版面。 */
enum class PanelMode { KEYBOARD, VOICE, CLIPBOARD }

/** 浮在面板區上方的覆蓋層，同時最多一個。 */
enum class Overlay { NONE, CANDIDATES, EDIT, GEAR, HEIGHT, PHRASES, FAN }

/**
 * 集中管理面板區的切換。所有面板 View 只建立一次，切換時只改 visibility，
 * 面板區高度固定 = 鍵盤高度（K15），切換時整體高度不跳動。
 */
class PanelController(
    private val container: FrameLayout,
    private val keyboard: View,
    private val voiceSlot: FrameLayout,
    private val clipboardSlot: FrameLayout,
    private val overlays: Map<Overlay, View>,
) {
    var mode: PanelMode = PanelMode.KEYBOARD
        private set
    var overlay: Overlay = Overlay.NONE
        private set

    fun showPanel(m: PanelMode) {
        mode = m
        keyboard.visibility = if (m == PanelMode.KEYBOARD) View.VISIBLE else View.INVISIBLE
        voiceSlot.visibility = if (m == PanelMode.VOICE) View.VISIBLE else View.GONE
        clipboardSlot.visibility = if (m == PanelMode.CLIPBOARD) View.VISIBLE else View.GONE
        showOverlay(Overlay.NONE)
    }

    fun showOverlay(o: Overlay) {
        overlay = o
        for ((k, v) in overlays) v.visibility = if (k == o) View.VISIBLE else View.GONE
    }

    fun toggleOverlay(o: Overlay) = showOverlay(if (overlay == o) Overlay.NONE else o)

    /** 調整面板區高度（K15 高度調整）。 */
    fun setHeight(px: Int) {
        val lp = container.layoutParams
        if (lp.height != px) {
            lp.height = px
            container.layoutParams = lp
        }
    }
}
