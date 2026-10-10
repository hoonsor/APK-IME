package tw.hoonsor.ime.voice.text

/**
 * 送字前的文字規則（MOBILE_VOICE_HANDOFF §8.2，桌面版已驗證）。不論有沒有經過 LLM 都要做。
 * 倬確認的格式：單句結尾保留「。」；中文與英文、數字之間加一個半形空格。
 */
object TextRules {
    private const val CJK = "[\\u4e00-\\u9fff\\u3400-\\u4dbf]"
    private val HALF_PUNCT_AFTER_CJK = Regex("(?<=$CJK)([,?!:;])(?!\\d)")
    private val FULL = mapOf(',' to "，", '?' to "？", '!' to "！", ':' to "：", ';' to "；")
    private val CJK_THEN_ASCII = Regex("($CJK)([A-Za-z0-9])")
    private val ASCII_THEN_CJK = Regex("([A-Za-z0-9%])($CJK)")

    fun finalizeText(s: String): String {
        var t = s.replace('﹐', '，') // Groq Whisper 會輸出 U+FE50 小型逗號
        t = HALF_PUNCT_AFTER_CJK.replace(t) { FULL.getValue(it.value[0]) }
        t = t.replace(CJK_THEN_ASCII, "$1 $2").replace(ASCII_THEN_CJK, "$1 $2")
        return t
    }
}
