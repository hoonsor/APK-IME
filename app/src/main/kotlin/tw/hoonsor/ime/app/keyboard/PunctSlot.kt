package tw.hoonsor.ime.app.keyboard

/**
 * 六顆可自訂的四符號鍵與預設內容（倬，2026-10-25／11-02）。
 * Punct 參數順序：左下＝點一下、左上、右上、右下。
 * 空白鍵的左下固定是空白：注音組字的一聲要靠它，不開放修改。
 */
enum class PunctSlot(val title: String, val default: KeyAction.Punct) {
    ZHUYIN_LEFT("注音：空白鍵左邊", KeyAction.Punct("，", "？", "、", "！")),
    ZHUYIN_SPACE("注音：空白鍵", KeyAction.Punct(KeyAction.Punct.SPACE, "：", "；", "「")),
    ZHUYIN_RIGHT("注音：空白鍵右邊", KeyAction.Punct("。", "&", "●", "（")),
    ENGLISH_LEFT("英文：空白鍵左邊", KeyAction.Punct("@", ",", "-", "_")),
    ENGLISH_SPACE("英文：空白鍵", KeyAction.Punct(KeyAction.Punct.SPACE, "?", "!", ":")),
    ENGLISH_RIGHT("英文：空白鍵右邊", KeyAction.Punct(".com", ".", "~", ";"));

    val tapLocked get() = default.isSpace

    /** 存成以 Tab 分隔的四段。 */
    fun encode(p: KeyAction.Punct): String =
        listOf(p.downLeft, p.upLeft, p.upRight, p.downRight).joinToString(SEP) { clean(it) }

    /** 空白或格式不對的角用預設值，絕不丟例外（鍵盤不能因設定壞掉）。 */
    fun decode(raw: String?): KeyAction.Punct {
        val parts = raw?.split(SEP).orEmpty()
        fun at(i: Int, fallback: String) = clean(parts.getOrNull(i).orEmpty()).ifEmpty { fallback }
        return KeyAction.Punct(
            downLeft = if (tapLocked) default.downLeft else at(0, default.downLeft),
            upLeft = at(1, default.upLeft),
            upRight = at(2, default.upRight),
            downRight = at(3, default.downRight),
        )
    }

    companion object {
        /** 一個角最多幾個字元，再長鍵面就看不清楚了。 */
        const val MAX_CHARS = 12
        private const val SEP = "\t"

        /** 去掉前後空白與換行、Tab，超長就截斷（不把代理字元切成兩半）。 */
        fun clean(s: String): String {
            val t = s.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim()
            if (t.length <= MAX_CHARS) return t
            val end = if (Character.isHighSurrogate(t[MAX_CHARS - 1])) MAX_CHARS - 1 else MAX_CHARS
            return t.substring(0, end)
        }
    }
}
