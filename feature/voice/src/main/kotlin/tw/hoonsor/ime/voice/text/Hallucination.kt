package tw.hoonsor.ime.voice.text

/**
 * Whisper 幻覺過濾（MOBILE_VOICE_HANDOFF §6.1）。
 * **整句比對**，不做子字串比對（桌面舊版曾把含「訂閱」的正常句子整句刪掉）。
 * 真正的防線是錄音端的靜音判斷；這裡只擋固定的片尾句與重複輸出。
 */
object Hallucination {
    // 注意：Regex 要宣告在 PHRASES 之前（PHRASES 初始化時會用到 norm()）
    private val END_PUNCT = Regex("[。.!?！？,，、\\s]+$")
    private val SENT_SPLIT = Regex("[。.!?！？]+")
    private val TOKEN = Regex("[\\u4e00-\\u9fff]|[A-Za-z']+")
    // 子句切分：多加逗號、頓號、波浪號；英文句點後面接字母（amara.org）不切
    private val PART_SPLIT = Regex("[。!?！？,，、～~]+|\\.(?![A-Za-z0-9])")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private val ZH = """
        謝謝收看 謝謝觀看 謝謝大家 謝謝大家的收看 謝謝大家的觀看 多謝收看 多謝觀看 多謝你的觀看 多謝您的觀看 多謝你的收看 多謝您的收看
        感謝收看 感謝觀看 感謝你的觀看 感謝您的觀看 感謝大家的收看 感謝大家的觀看 感謝你的收看 感謝您的收看
        我們下次再見 我們下集再見 下次見 下集再見 下次再見 我們下次見 下期再見
        請訂閱 請按讚 請按讚訂閱 別忘了訂閱 記得訂閱 請按讚並訂閱 別忘了按讚訂閱 別忘了按讚訂閱開啟小鈴鐺 記得按讚訂閱開啟小鈴鐺 按讚訂閱開啟小鈴鐺
        請不吝點讚訂閱轉發打賞支持明鏡與點點欄目 訂閱我的頻道 打開小鈴鐺 開啟小鈴鐺
        訂閱 請訂閱我的頻道 記得訂閱我的頻道 非常感謝您的觀看 非常感謝你的觀看 非常感謝大家的觀看 謝謝觀賞 感謝觀賞
        請按讚訂閱分享 記得按讚訂閱分享 記得按讚訂閱分享哦 記得按讚訂閱分享喔 按讚留言分享訂閱開啟小鈴鐺
        嗯 啊 喔 哦 嗯哼 呃 嗯嗯 。 ... …
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private val EN = """
        thank you for watching | thanks for watching | thank you | thank you so much for watching | thanks so much for watching
        thank you for listening | thanks for listening | please subscribe | subscribe | subscribe to my channel
        like and subscribe | like comment and subscribe | like, comment and subscribe | like comment subscribe | please like and subscribe
        don't forget to subscribe | remember to subscribe | hit the subscribe button | hit that subscribe button
        bye | bye bye | goodbye | see you next time | see you | see ya
        please subscribe to my channel | subscribe to our channel | please subscribe to our channel
        thank you so much for watching this video | thank you for watching this video | thanks for watching this video
        see you in the next video | see you in the next one | thanks for tuning in | thank you for tuning in
        subtitles by amara.org community
        字幕由amara.org社群提供 | 字幕由 amara.org 社群提供 | 由 amara.org 社群提供 | 由amara.org社群提供
        subtitles by the amara.org community | subtitles by the amara org community | amara.org | mbc뉴스 | mbc 뉴스
        請不吝點讚 訂閱 轉發 打賞支持明鏡與點點欄目
    """.trimIndent().split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private val PHRASES: Set<String> = (ZH + EN).map { norm(it) }.filter { it.isNotEmpty() }.toSet() + setOf("。", "...", "…")

    /** 去掉標點空白後的套話（比對「謝謝收看謝謝收看」「字幕由amara.org…」這類黏在一起的重複）。 */
    private val STRIPPED: Set<String> = PHRASES.map { strip(it) }.filter { it.isNotEmpty() }.toSet()

    /**
     * 只在「多個子句、每個子句都是套話」時才算數的片段；單獨出現一律保留
     * （使用者單講「分享」「拜拜」「Thanks」是正常的）。
     */
    private val PART_ONLY: Set<String> = """
        拜拜|再見|按讚|點讚|留言|分享|轉發|如果你喜歡這個影片|如果您喜歡這個影片
        like|comment|share|thanks|please like|don't forget to like|subscribe and hit the bell|hit the bell
        turn on notifications|if you enjoyed this video|if you like this video
    """.trimIndent().split('|', '\n').map { norm(it) }.filter { it.isNotEmpty() }.toSet()

    /** 整段只是某個單位重複時，會被丟掉的單位（去標點空白後）與最少次數。 */
    private val REPEAT_UNITS: Map<String, Int> = mapOf(
        "謝謝" to 3, "謝" to 5, "thankyou" to 3, "thanks" to 3, "thank" to 3,
        "訂閱" to 3, "請訂閱" to 3, "subscribe" to 3, "bye" to 3, "拜" to 5, "拜拜" to 3,
        "的" to 5, "the" to 5,
    )
    /** 任意單位重複幾次才算幻覺（保守：「好好好」「對對對」「哈哈哈哈」要留著）。 */
    private const val ANY_UNIT_MIN_REPEAT = 8


    private fun norm(s: String): String = s.trim().replace(END_PUNCT, "").trim().lowercase()

    private fun strip(s: String): String = s.lowercase().replace(NON_WORD, "")

    /** true = 整段是幻覺，應丟棄。 */
    fun isHallucination(text: String): Boolean {
        val t = norm(text)
        if (t.isEmpty()) return true
        if (t in PHRASES) return true
        // 只差標點、空白、波浪號（「記得按讚訂閱分享哦～」「按讚留言分享 訂閱開啟小鈴鐺」）
        if (strip(t).let { it.isNotEmpty() && it in STRIPPED }) return true
        val sentences = text.split(SENT_SPLIT).map { norm(it) }.filter { it.isNotEmpty() }
        if (sentences.isNotEmpty() && sentences.all { it in PHRASES }) return true
        val long = sentences.filter { it.length > 2 }
        if (long.isNotEmpty() && long.all { it in PHRASES }) return true
        if (allPartsBoilerplate(text)) return true
        if (shortRepetition(t)) return true
        return repetitionDominated(text)
    }

    /**
     * 用逗號、頓號串起來的多句套話（「感謝觀看，請訂閱、按讚、開啟小鈴鐺」）。
     * 仍是**逐子句整句比對**：每個子句都要是套話才丟，任一子句是一般內容就保留。
     */
    private fun allPartsBoilerplate(text: String): Boolean {
        val parts = text.split(PART_SPLIT).map { norm(it).removePrefix("and ").trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return false
        if (!parts.all { it in PHRASES || it in PART_ONLY || strip(it) in STRIPPED }) return false
        return parts.size >= 3 || parts.any { it in PHRASES || strip(it) in STRIPPED }
    }

    /** 整段就是同一個單位重複（「謝謝謝謝謝謝」「Bye bye bye bye」「我我我我我我我我」）。 */
    private fun shortRepetition(t: String): Boolean {
        val s = strip(t)
        val n = s.length
        if (n < 2) return false
        for (p in 1..n / 2) {
            if (n % p != 0) continue
            val unit = s.substring(0, p)
            if (unit.repeat(n / p) != s) continue
            val k = n / p
            // 找到最小週期後，也檢查較大的週期（「謝謝收看」×3 的最小週期就是它本身）
            var q = p
            while (q <= n / 2) {
                if (n % q == 0) {
                    val u = s.substring(0, q)
                    val times = n / q
                    if (u.repeat(times) == s) {
                        if (u in STRIPPED && times >= 2) return true
                        val min = REPEAT_UNITS[u]
                        if (min != null && times >= min) return true
                    }
                }
                q += p
            }
            return k >= ANY_UNIT_MIN_REPEAT
        }
        return false
    }

    /** 某個 1～4-gram 出現 ≥ 8 次且佔 ≥ 65%（例：「通過 通過 通過…」）。 */
    private fun repetitionDominated(text: String): Boolean {
        val tokens = TOKEN.findAll(text).map { it.value.lowercase() }.toList()
        if (tokens.size < 12) return false
        for (n in 1..4) {
            val counts = HashMap<String, Int>()
            for (i in 0..tokens.size - n) {
                val k = tokens.subList(i, i + n).joinToString(" ")
                counts[k] = (counts[k] ?: 0) + 1
            }
            val best = counts.values.maxOrNull() ?: continue
            if (best >= 8 && best * n >= tokens.size * 0.65) return true
        }
        return false
    }
}
