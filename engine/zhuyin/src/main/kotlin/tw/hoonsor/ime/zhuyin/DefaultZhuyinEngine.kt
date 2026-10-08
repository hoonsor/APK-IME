package tw.hoonsor.ime.zhuyin

import kotlin.math.ln

/**
 * 簡拼引擎實作。
 *
 * 流程：
 * 1. 把輸入切成所有可能的「每字片段」（Segmenter）。
 * 2. 每種切分以首符號組成索引鍵，到系統詞庫與使用者詞庫查詢，再逐字檢查片段是否吻合讀音。
 * 3. 候選分三群：完整吻合 → 延長找詞 → 只吻合前段（選了之後剩下的注音繼續選）。
 * 4. 多段選字完成時，自動把整段建成使用者詞（超注音的自建詞方式）。
 */
class DefaultZhuyinEngine(
    private val system: Lexicon,
    val user: Lexicon = Lexicon(),
    private val listener: EngineListener = object : EngineListener {},
    private val config: EngineConfig = EngineConfig(),
) : ZhuyinEngine {

    private val input = StringBuilder()
    private val pieces = ArrayList<Piece>()

    /** 使用次數（含系統詞），鍵為 text + 讀音。app 啟動時可用 [setUsage] 回填。 */
    private val usage = HashMap<String, Int>()

    private class Piece(val text: String, val readings: List<String>, val raw: String)

    override var state: EngineState = EngineState.EMPTY
        private set

    // ---------- 公開操作 ----------

    override fun onSymbol(symbol: Char): EngineState {
        val c = Zhuyin.normalizeInput(symbol) ?: return state
        if (c == Zhuyin.TONE1 && input.isEmpty()) return state // 空白在無注音時交給 app
        input.append(c)
        return refresh()
    }

    override fun onBackspace(): EngineState {
        if (input.isNotEmpty()) {
            input.setLength(input.length - 1)
        } else if (pieces.isNotEmpty()) {
            val p = pieces.removeAt(pieces.size - 1)
            input.insert(0, p.raw)
        }
        return refresh()
    }

    override fun select(index: Int): SelectResult {
        val c = state.candidates.getOrNull(index) ?: return SelectResult(null, state)
        val consumed = c.consumed.coerceAtMost(input.length)
        pieces.add(Piece(c.text, c.readings, input.substring(0, consumed)))
        input.delete(0, consumed)
        if (input.isNotEmpty()) return SelectResult(null, refresh())

        val text = pieces.joinToString("") { it.text }
        val readings = pieces.flatMap { it.readings }
        if (pieces.size >= 2 && text.length <= config.maxAutoPhraseLength && text.length == readings.size) {
            addUserPhrase(text, readings, CandidateSource.USER)
            listener.onUserPhraseCreated(text, readings)
        }
        recordUse(text, readings)
        pieces.clear()
        return SelectResult(text, refresh())
    }

    override fun associations(previous: String, limit: Int): List<Candidate> {
        if (previous.isEmpty()) return emptyList()
        val tail = previous.takeLast(4)
        // 由長到短嘗試，找到以前文結尾開頭的較長詞，取其後半段
        for (n in tail.length downTo 1) {
            val head = tail.takeLast(n)
            val found = (user.textStartingWith(head) + system.textStartingWith(head))
                .sortedByDescending { score(it) }
                .map { it.text.substring(head.length) }
                .distinct()
                .take(limit)
            if (found.isNotEmpty()) {
                return found.map { Candidate(it, CandidateSource.SYSTEM, emptyList(), 0) }
            }
        }
        return emptyList()
    }

    override fun deleteUserPhrase(candidate: Candidate): Boolean {
        if (candidate.source == CandidateSource.SYSTEM) return false
        val syls = candidate.readings.map { Syllable.parse(it) }
        val ok = user.remove(candidate.text, syls)
        if (ok) {
            listener.onUserPhraseDeleted(candidate.text, candidate.readings)
            refresh()
        }
        return ok
    }

    override fun reset(): EngineState {
        input.setLength(0)
        pieces.clear()
        return refresh()
    }

    // ---------- 給 app 載入使用者資料 ----------

    fun addUserPhrase(text: String, readings: List<String>, source: CandidateSource, frequency: Int = 0) {
        val syls = readings.map { Syllable.parse(it) }
        if (user.exact(keyOf(syls)).any { it.text == text && it.syllables == syls }) return
        user.add(LexEntry(text, syls, frequency, source))
    }

    fun setUsage(text: String, readings: List<String>, count: Int) {
        usage[usageKey(text, readings)] = count
    }

    // ---------- 內部 ----------

    private fun recordUse(text: String, readings: List<String>) {
        val k = usageKey(text, readings)
        usage[k] = (usage[k] ?: 0) + 1
        listener.onPhraseUsed(text, readings)
    }

    private fun usageKey(text: String, readings: List<String>) = text + "|" + readings.joinToString(" ")

    private fun keyOf(syls: List<Syllable>) = buildString { syls.forEach { if (it.core.isNotEmpty()) append(it.core[0]) } }

    private fun score(e: LexEntry): Double {
        val used = usage[usageKey(e.text, e.syllables.map { it.toString() })] ?: 0
        val base = ln(e.count + 1.0)
        val userBonus = if (e.source != CandidateSource.SYSTEM) 20.0 else 0.0
        return base + userBonus + 8.0 * ln(used + 1.0)
    }

    private fun refresh(): EngineState {
        val composing = pieces.joinToString("") { it.text } + input
        state = EngineState(composing, if (input.isEmpty()) emptyList() else search(input.toString()))
        return state
    }

    private data class Hit(val entry: LexEntry, val consumed: Int, val chars: Int, val extended: Boolean)

    private fun search(text: String): List<Candidate> {
        val parts = Segmenter.partitions(text)
        if (parts.isEmpty()) return emptyList()

        val exact = HashMap<String, Hit>()
        val extended = HashMap<String, Hit>()
        val prefix = HashMap<String, Hit>()
        val seenPrefix = HashSet<String>()

        fun lookup(key: String): List<LexEntry> = user.exact(key) + system.exact(key)

        for (frags in parts) {
            val fullKey = buildString { frags.forEach { append(it.core[0]) } }

            for (e in lookup(fullKey)) {
                if (e.matches(frags)) exact.putIfBetter(e, Hit(e, text.length, frags.size, false))
            }

            if (frags.size >= config.extendFromFragments) {
                for (e in user.longerThan(fullKey) + system.longerThan(fullKey)) {
                    if (e.matches(frags)) extended.putIfBetter(e, Hit(e, text.length, frags.size, true))
                }
            }

            // 前段吻合：選了之後，剩下的注音繼續選
            var consumed = 0
            for (j in 1 until frags.size) {
                consumed += frags[j - 1].length
                val sub = frags.subList(0, j)
                val sig = sub.joinToString("|")
                if (!seenPrefix.add(sig)) continue
                val key = fullKey.substring(0, j)
                for (e in lookup(key)) {
                    if (e.matches(sub)) prefix.putIfBetter(e, Hit(e, consumed, j, false))
                }
            }
        }

        val out = ArrayList<Candidate>()
        val usedText = HashSet<String>()
        fun emit(h: Hit) {
            if (!usedText.add(h.entry.text + "#" + h.consumed)) return
            val readings = h.entry.syllables.map { it.toString() }
            out.add(Candidate(h.entry.text, h.entry.source, readings, h.consumed, h.extended))
        }

        exact.values.sortedByDescending { score(it.entry) }.forEach(::emit)
        extended.values.sortedByDescending { score(it.entry) }.take(30).forEach(::emit)
        prefix.values.groupBy { it.consumed }.toSortedMap(reverseOrder()).forEach { (_, hits) ->
            hits.sortedByDescending { score(it.entry) }.take(config.maxPrefixPerLength).forEach(::emit)
        }
        return out.take(config.maxCandidates)
    }

    /** 同一個詞（不同讀音）只留分數最高的。 */
    private fun HashMap<String, Hit>.putIfBetter(e: LexEntry, hit: Hit) {
        val k = e.text + "#" + hit.consumed
        val old = this[k]
        if (old == null || score(e) > score(old.entry)) this[k] = hit
    }
}
