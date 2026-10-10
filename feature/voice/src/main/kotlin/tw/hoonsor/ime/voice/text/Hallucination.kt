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
    private val TOKEN = Regex("[\\u4e00-\\u9fff]+|[A-Za-z']+")

    private val ZH = """
        謝謝收看 謝謝觀看 謝謝大家 謝謝大家的收看 謝謝大家的觀看 多謝收看 多謝觀看 多謝你的觀看 多謝您的觀看 多謝你的收看 多謝您的收看
        感謝收看 感謝觀看 感謝你的觀看 感謝您的觀看 感謝大家的收看 感謝大家的觀看 感謝你的收看 感謝您的收看
        我們下次再見 我們下集再見 下次見 下集再見 下次再見 我們下次見 下期再見
        請訂閱 請按讚 請按讚訂閱 別忘了訂閱 記得訂閱 請按讚並訂閱 別忘了按讚訂閱 別忘了按讚訂閱開啟小鈴鐺 記得按讚訂閱開啟小鈴鐺 按讚訂閱開啟小鈴鐺
        請不吝點讚訂閱轉發打賞支持明鏡與點點欄目 訂閱我的頻道 打開小鈴鐺 開啟小鈴鐺
        嗯 啊 喔 哦 嗯哼 呃 嗯嗯 。 ... …
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private val EN = """
        thank you for watching | thanks for watching | thank you | thank you so much for watching | thanks so much for watching
        thank you for listening | thanks for listening | please subscribe | subscribe | subscribe to my channel
        like and subscribe | like comment and subscribe | like, comment and subscribe | like comment subscribe | please like and subscribe
        don't forget to subscribe | remember to subscribe | hit the subscribe button | hit that subscribe button
        bye | bye bye | goodbye | see you next time | see you | see ya
        字幕由amara.org社群提供 | 字幕由 amara.org 社群提供 | 由 amara.org 社群提供 | 由amara.org社群提供
        subtitles by the amara.org community | subtitles by the amara org community | amara.org | mbc뉴스 | mbc 뉴스
        請不吝點讚 訂閱 轉發 打賞支持明鏡與點點欄目
    """.trimIndent().split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private val PHRASES: Set<String> = (ZH + EN).map { norm(it) }.filter { it.isNotEmpty() }.toSet() + setOf("。", "...", "…")


    private fun norm(s: String): String = s.trim().replace(END_PUNCT, "").trim().lowercase()

    /** true = 整段是幻覺，應丟棄。 */
    fun isHallucination(text: String): Boolean {
        val t = norm(text)
        if (t.isEmpty()) return true
        if (t in PHRASES) return true
        val sentences = text.split(SENT_SPLIT).map { norm(it) }.filter { it.isNotEmpty() }
        if (sentences.isNotEmpty() && sentences.all { it in PHRASES }) return true
        val long = sentences.filter { it.length > 2 }
        if (long.isNotEmpty() && long.all { it in PHRASES }) return true
        return repetitionDominated(text)
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
