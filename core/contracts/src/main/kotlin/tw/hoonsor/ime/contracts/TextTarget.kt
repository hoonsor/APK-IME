package tw.hoonsor.ime.contracts

/**
 * 文字輸出目標的抽象。
 * - app 以 InputConnection 實作（ImeTextTarget）。
 * - voice-sandbox 以 EditText 實作，讓語音模組可以不依賴 IME 獨立測試。
 *
 * 所有方法都必須在主執行緒呼叫（InputConnection 的限制）。
 */
interface TextTarget {
    /** 目前輸入框的資訊。 */
    val hints: EditorHints

    /** 在游標處插入文字（若有選取則取代選取內容）。 */
    fun commitText(text: CharSequence)

    /** 目前選取的文字；沒有選取時回傳 null。 */
    fun selectedText(): CharSequence?

    /** 以新文字取代目前選取內容；沒有選取時等同 commitText。 */
    fun replaceSelection(text: CharSequence)

    /** 游標前最多 n 個字元，供 LLM 參考上下文。 */
    fun textBeforeCursor(n: Int): CharSequence?

    /** 游標後最多 n 個字元。 */
    fun textAfterCursor(n: Int): CharSequence?

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int)
}

/** 輸入框資訊，用於語氣調整與安全判斷。 */
data class EditorHints(
    /** 目前 App 的套件名稱，例如 jp.naver.line.android。 */
    val packageName: String?,
    /** 密碼欄位：停用語音與詞頻學習。 */
    val isPassword: Boolean,
    val isMultiLine: Boolean,
    val fieldType: FieldType,
)

enum class FieldType { TEXT, EMAIL, URI, NUMBER, PHONE, SEARCH, MESSAGE, OTHER }
