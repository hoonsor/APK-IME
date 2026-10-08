package tw.hoonsor.ime.zhuyin

/**
 * 注音符號的基本規則。
 *
 * 音節由「聲母 → 介音 → 韻母」依序組成，最後可接聲調。
 * 內部以 'ˉ' 表示一聲（輸入時的空白鍵，讀音中沒有聲調符號者）。
 */
object Zhuyin {
    const val TONE1 = 'ˉ'
    private const val TONES = "ˉˊˇˋ˙"

    fun isTone(c: Char): Boolean = c in TONES

    /** 0 = 聲母 ㄅ–ㄙ、1 = 介音 ㄧㄨㄩ、2 = 韻母 ㄚ–ㄦ；非注音回傳 -1。 */
    fun rank(c: Char): Int = when (c) {
        in 'ㄅ'..'ㄙ' -> 0
        in 'ㄧ'..'ㄩ' -> 1
        in 'ㄚ'..'ㄦ' -> 2
        else -> -1
    }

    fun isSymbol(c: Char): Boolean = rank(c) >= 0

    /** 鍵盤輸入正規化：空白 → 一聲。不是注音或聲調則回傳 null。 */
    fun normalizeInput(c: Char): Char? = when {
        c == ' ' -> TONE1
        isSymbol(c) || isTone(c) -> c
        else -> null
    }
}

/** 一個完整音節，例如「ㄆㄧㄥˊ」→ core = ㄆㄧㄥ、tone = ˊ。 */
data class Syllable(val core: String, val tone: Char) {
    override fun toString(): String = if (tone == Zhuyin.TONE1) core else core + tone

    companion object {
        fun parse(s: String): Syllable {
            val last = s.last()
            return if (Zhuyin.isTone(last) && last != Zhuyin.TONE1) {
                Syllable(s.dropLast(1), last)
            } else {
                Syllable(s.trimEnd(Zhuyin.TONE1), Zhuyin.TONE1)
            }
        }
    }
}

/**
 * 使用者為一個字輸入的片段：開頭注音 + 任意後續注音（依序）+ 可選聲調。
 * 例如「平」可以輸入 ㄆ、ㄆㄥ、ㄆㄧㄥ、ㄆˊ、ㄆㄧㄥˊ。
 */
data class Fragment(val core: String, val tone: Char?) {
    /** 原始輸入長度（含聲調）。 */
    val length: Int get() = core.length + if (tone != null) 1 else 0

    fun matches(s: Syllable): Boolean {
        if (core.isEmpty() || s.core.isEmpty() || core[0] != s.core[0]) return false
        if (tone != null && tone != s.tone) return false
        // core 必須是 s.core 的子序列
        var i = 0
        for (c in s.core) {
            if (i < core.length && core[i] == c) i++
        }
        return i == core.length
    }

    override fun toString(): String = core + (tone?.toString() ?: "")
}

object Segmenter {
    /** 一個字的片段最多 3 個注音符號（聲母＋介音＋韻母）。 */
    private const val MAX_CORE = 3

    /**
     * 列出輸入所有可能的切分方式。
     * 規則：聲調只能結束一個片段；片段內符號類別必須遞增（所以每個聲母一定開始新片段）。
     * 回傳的每個切分都完整覆蓋輸入。
     */
    fun partitions(input: String, maxFragments: Int = 12, maxResults: Int = 4096): List<List<Fragment>> {
        val out = ArrayList<List<Fragment>>()
        val stack = ArrayList<Fragment>()
        fun dfs(pos: Int) {
            if (out.size >= maxResults) return
            if (pos == input.length) {
                out.add(stack.toList()); return
            }
            if (stack.size >= maxFragments) return
            val first = input[pos]
            if (!Zhuyin.isSymbol(first)) return
            var end = pos + 1
            var lastRank = Zhuyin.rank(first)
            while (true) {
                val core = input.substring(pos, end)
                if (end < input.length && Zhuyin.isTone(input[end])) {
                    stack.add(Fragment(core, input[end])); dfs(end + 1); stack.removeAt(stack.size - 1)
                } else {
                    stack.add(Fragment(core, null)); dfs(end); stack.removeAt(stack.size - 1)
                }
                if (end >= input.length || end - pos >= MAX_CORE) break
                val r = Zhuyin.rank(input[end])
                if (r <= lastRank) break
                lastRank = r
                end++
            }
        }
        dfs(0)
        return out
    }
}
