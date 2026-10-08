package tw.hoonsor.ime.app

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import tw.hoonsor.ime.contracts.EditorHints
import tw.hoonsor.ime.contracts.FieldType
import tw.hoonsor.ime.contracts.TextTarget

/**
 * 包裝目前 App 輸入框的 TextTarget（ARCHITECTURE §5.1 送字規則）。
 * ImeController 只透過 currentTarget 送字；預設就是這個物件。
 */
class ImeTextTarget(
    private val ic: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo?,
) : TextTarget {

    override val hints: EditorHints
        get() = hintsOf(editorInfo())

    override fun commitText(text: CharSequence) {
        ic()?.commitText(text, 1)
    }

    override fun selectedText(): CharSequence? = ic()?.getSelectedText(0)?.takeIf { it.isNotEmpty() }

    override fun replaceSelection(text: CharSequence) {
        ic()?.commitText(text, 1)
    }

    override fun textBeforeCursor(n: Int): CharSequence? = ic()?.getTextBeforeCursor(n, 0)

    override fun textAfterCursor(n: Int): CharSequence? = ic()?.getTextAfterCursor(n, 0)

    /**
     * 刪除：有選取時刪掉選取內容；游標前是 emoji 等代理字元時一次刪兩個 char；
     * 讀不到前文的輸入框（例如終端機）改送 DEL 按鍵。
     */
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        val conn = ic() ?: return
        if (beforeLength == 1 && afterLength == 0) {
            if (!conn.getSelectedText(0).isNullOrEmpty()) {
                conn.commitText("", 1)
                return
            }
            val before = conn.getTextBeforeCursor(2, 0)
            if (before == null) {
                sendKey(conn, KeyEvent.KEYCODE_DEL)
                return
            }
            if (before.isEmpty()) {
                sendKey(conn, KeyEvent.KEYCODE_DEL) // 讓 App 自行處理（例如刪除前一段）
                return
            }
            val n = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            conn.deleteSurroundingText(n, 0)
            return
        }
        conn.deleteSurroundingText(beforeLength, afterLength)
    }

    /** Enter：單行輸入框執行動作（送出、搜尋…），多行則換行。 */
    fun enter() {
        val conn = ic() ?: return
        val ei = editorInfo()
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val multiLine = ((ei?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!multiLine && !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            conn.performEditorAction(action)
        } else {
            sendKey(conn, KeyEvent.KEYCODE_ENTER)
        }
    }

    companion object {
        fun sendKey(conn: InputConnection, code: Int, meta: Int = 0) {
            val now = android.os.SystemClock.uptimeMillis()
            conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
            conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        }

        fun hintsOf(ei: EditorInfo?): EditorHints {
            val type = ei?.inputType ?: 0
            val cls = type and InputType.TYPE_MASK_CLASS
            val variation = type and InputType.TYPE_MASK_VARIATION
            val password = when (cls) {
                InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
            val field = when (cls) {
                InputType.TYPE_CLASS_NUMBER -> FieldType.NUMBER
                InputType.TYPE_CLASS_PHONE -> FieldType.PHONE
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldType.EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> FieldType.URI
                    InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> FieldType.MESSAGE
                    else -> if (((ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEARCH) FieldType.SEARCH else FieldType.TEXT
                }
                else -> FieldType.OTHER
            }
            return EditorHints(
                packageName = ei?.packageName,
                isPassword = password,
                isMultiLine = (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0,
                fieldType = field,
            )
        }
    }
}
