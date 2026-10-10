package tw.hoonsor.ime.voice.ai

/** LLM 輸出清洗（MOBILE_VOICE_HANDOFF §8.1）。 */
object Sanitizer {
    private val THINK = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val THINK_OPEN = Regex("^[\\s\\S]*?</think>", RegexOption.IGNORE_CASE) // 只有結尾標籤（開頭被截掉）
    private val FENCE_START = Regex("^```[a-zA-Z]*\\s*\\n?")
    private val FENCE_END = Regex("\\n?```\\s*$")
    private val DRAFT_TAG = Regex("</?\\s*Draft\\s*>", RegexOption.IGNORE_CASE)
    private val PREAMBLE = Regex("^\\s*(輸出|整理後|修正後|結果|Output|Result)\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
    private val QUOTES = listOf("「" to "」", "“" to "”", "\"" to "\"", "'" to "'")

    /**
     * @param original 原文（草稿）
     * @param guardAnswer 聽寫模式為 true：輸出過長視為模型在「回答」，改用原文
     * @return 清洗後文字；空字串表示模型判定為雜訊（不送字）；null 表示應改用原文
     */
    fun clean(output: String?, original: String, guardAnswer: Boolean): String? {
        if (output == null) return null
        var t = output.trim()
        t = THINK.replace(t, "").trim()
        if (t.contains("</think>", ignoreCase = true)) t = THINK_OPEN.replace(t, "").trim()
        t = FENCE_START.replace(t, "")
        t = FENCE_END.replace(t, "")
        t = DRAFT_TAG.replace(t, "").trim()
        t = PREAMBLE.replace(t, "").trim()
        // 整段被一對引號包住、原文沒有、且引號只出現這一對 → 去掉
        for ((l, r) in QUOTES) {
            if (t.length < 2 || !t.startsWith(l) || !t.endsWith(r)) continue
            val o = original.trim()
            val origWrapped = o.startsWith(l) && o.endsWith(r)
            val onlyPair = if (l == r) t.count { it == l[0] } == 2 else t.count { it == l[0] } == 1
            if (!origWrapped && onlyPair) t = t.substring(1, t.length - 1).trim()
            break
        }
        if (guardAnswer) {
            val n = original.length
            if (t.length > maxOf(n * 2.5, n + 80.0)) return null
        }
        return t
    }
}
