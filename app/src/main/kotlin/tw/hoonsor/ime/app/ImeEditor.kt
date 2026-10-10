package tw.hoonsor.ime.app

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * 編輯面板（K10）專用操作：游標移動、選取、複製剪下貼上、前後刪。
 * 這些需要 InputConnection 專屬功能，不屬於 TextTarget 介面（ARCHITECTURE §5.1 例外）。
 * 剪貼簿搜尋模式下，編輯面板會停用。
 */
class ImeEditor(private val ic: () -> InputConnection?) {

    /** 「開選」後的選取模式：移動鍵會延伸選取範圍。 */
    var selecting = false

    private fun key(code: Int) {
        val conn = ic() ?: return
        val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        if (selecting) {
            val now = android.os.SystemClock.uptimeMillis()
            conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
            ImeTextTarget.sendKey(conn, code, meta)
            conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
        } else {
            ImeTextTarget.sendKey(conn, code, 0)
        }
    }

    fun left() = key(KeyEvent.KEYCODE_DPAD_LEFT)
    fun right() = key(KeyEvent.KEYCODE_DPAD_RIGHT)
    fun up() = key(KeyEvent.KEYCODE_DPAD_UP)
    fun down() = key(KeyEvent.KEYCODE_DPAD_DOWN)
    fun lineStart() = key(KeyEvent.KEYCODE_MOVE_HOME)
    fun lineEnd() = key(KeyEvent.KEYCODE_MOVE_END)

    fun startSelect() { selecting = true }

    /** 全選：只建立選取範圍，不進入「開選」模式（貼上或游標移走後就自動回到未選取）。 */
    fun selectAll() {
        ic()?.performContextMenuAction(android.R.id.selectAll)
        selecting = false
    }

    /** 停選：取消選取，游標停在選取範圍尾端。 */
    fun stopSelect(selEnd: Int) {
        selecting = false
        if (selEnd >= 0) ic()?.setSelection(selEnd, selEnd)
    }

    fun copy() {
        ic()?.performContextMenuAction(android.R.id.copy)
        selecting = false
    }

    fun cut() {
        ic()?.performContextMenuAction(android.R.id.cut)
        selecting = false
    }

    fun paste() {
        ic()?.performContextMenuAction(android.R.id.paste)
        selecting = false
    }

    fun deleteBackward() {
        ic()?.let { ImeTextTarget.sendKey(it, KeyEvent.KEYCODE_DEL) }
        selecting = false
    }

    fun deleteForward() {
        ic()?.let { ImeTextTarget.sendKey(it, KeyEvent.KEYCODE_FORWARD_DEL) }
        selecting = false
    }
}
