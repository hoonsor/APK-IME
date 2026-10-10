package tw.hoonsor.ime.zhuyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 簡拼引擎測試。使用真實系統詞庫（app/src/main/assets/zhuyin_dict.tsv）。
 * 執行：./gradlew :engine:zhuyin:test
 */
class AbbreviationTest {

    companion object {
        val system: Lexicon by lazy {
            val f = listOf(
                File("../../app/src/main/assets/zhuyin_dict.tsv"),
                File("app/src/main/assets/zhuyin_dict.tsv"),
            ).first { it.exists() }
            Lexicon.loadTsv(f.bufferedReader())
        }
    }

    private fun engine(listener: EngineListener = object : EngineListener {}) =
        DefaultZhuyinEngine(system, Lexicon(), listener)

    private fun ZhuyinEngine.type(s: String): EngineState {
        var st = state
        for (c in s) st = onSymbol(c)
        return st
    }

    /** 選第一個文字為 text 的候選。 */
    private fun ZhuyinEngine.pick(text: String): SelectResult {
        val i = state.candidates.indexOfFirst { it.text == text }
        assertTrue("找不到候選「$text」：${state.candidates.take(15).map { it.text }}", i >= 0)
        return select(i)
    }

    /** 期望的詞出現在前 n 個「完整吻合」候選中。 */
    private fun assertInTop(input: String, expected: String, n: Int) {
        val st = engine().type(input)
        val top = st.candidates.filter { it.consumed == input.length }.take(n).map { it.text }
        assertTrue("$input → 期望 $expected 在前 $n 個，實際 $top", expected in top)
    }

    // ---------- 超注音官方範例 ----------

    // 系統詞頻來自書面語料，口語詞（你好）排名較後；選過一次後即由學習機制排到第一（見 learnedNihaoFirst）
    @Test fun nihao() = assertInTop("ㄋㄏ", "你好", 15)

    @Test fun learnedNihaoFirst() {
        val e = engine()
        e.type("ㄋㄏ")
        e.select(e.state.candidates.indexOfFirst { it.text == "你好" })
        e.type("ㄋㄏ")
        assertEquals("你好", e.state.candidates.first().text)
    }

    @Test fun pengyouWithTone() = assertInTop("ㄆˊㄧ", "朋友", 3)

    @Test fun pingan() = assertInTop("ㄆㄥㄢ", "平安", 3)

    @Test fun mayingjiu() = assertInTop("ㄇˇㄧㄐ", "馬英九", 3)

    @Test fun yixinyiyi() = assertInTop("ㄧㄒㄧㄧㄧ", "一心一意", 3)

    @Test fun diannao() = assertInTop("ㄉㄋ", "電腦", 10)

    @Test fun fullPinyin() = assertInTop("ㄋㄧˇㄏㄠˇ", "你好", 1)

    @Test fun singleSyllableFull() = assertInTop("ㄋㄧˇ", "你", 3)

    @Test fun spaceIsFirstTone() {
        // ㄧ + 空白（一聲）只吻合以 ㄧ 開頭的一聲字（ㄧ、ㄧㄣ、ㄧㄥ…）
        val st = engine().type("ㄧ ")
        val exact = st.candidates.filter { it.consumed == 2 }
        assertTrue(exact.isNotEmpty())
        assertEquals("一", exact.first().text)
        assertTrue(exact.all { c -> c.readings.size == 1 && c.readings[0].startsWith("ㄧ") && c.readings[0].none { Zhuyin.isTone(it) } })
    }

    // ---------- 自建詞（Z5）：購物狂不在系統詞庫 ----------

    @Test fun buildUserPhraseBySequentialSelection() {
        val created = ArrayList<String>()
        val e = engine(object : EngineListener {
            override fun onUserPhraseCreated(text: String, readings: List<String>) { created += text }
        })
        e.type("ㄍㄡㄨㄎㄤ")
        assertNull(e.pick("購").commit)
        assertEquals("購ㄨㄎㄤ", e.state.composing)
        assertNull(e.pick("物").commit)
        assertEquals("購物ㄎㄤ", e.state.composing)
        assertEquals("購物狂", e.pick("狂").commit)
        assertEquals(listOf("購物狂"), created)

        // 下次直接出現在第一個
        e.type("ㄍㄡㄨㄎㄤ")
        assertEquals("購物狂", e.state.candidates.first().text)
        assertEquals(CandidateSource.USER, e.state.candidates.first().source)

        // 刪除（Z6）
        assertTrue(e.deleteUserPhrase(e.state.candidates.first()))
        assertTrue(e.state.candidates.none { it.text == "購物狂" && it.consumed == 5 })
    }

    /** 倬描述的超注音行為：ㄋㄧㄏㄠ 依序選單字「妳」「好」→ 自動建立「妳好」。 */
    @Test fun sequentialSingleCharsCreatePhrase() {
        val created = ArrayList<String>()
        val e = engine(object : EngineListener {
            override fun onUserPhraseCreated(text: String, readings: List<String>) { created += text }
        })
        e.type("ㄋㄧㄏㄠ")
        val single = e.state.candidates.first { it.text == "妳" }
        assertEquals("「妳」應吃掉 ㄋㄧ 兩個符號", 2, single.consumed)
        assertNull(e.pick("妳").commit)
        assertEquals("妳ㄏㄠ", e.state.composing)
        assertEquals("妳好", e.pick("好").commit)
        assertEquals(listOf("妳好"), created)

        // 之後 ㄋㄧㄏㄠ 與 ㄋㄏ 都會優先出現「妳好」
        e.type("ㄋㄧㄏㄠ")
        assertEquals("妳好", e.state.candidates.first().text)
        e.reset(); e.type("ㄋㄏ")
        assertEquals("妳好", e.state.candidates.first().text)
    }

    /** 直接選整個詞「妳好」不會建詞（只學習頻率）。 */
    @Test fun pickingWholePhraseDoesNotCreate() {
        val created = ArrayList<String>()
        val e = engine(object : EngineListener {
            override fun onUserPhraseCreated(text: String, readings: List<String>) { created += text }
        })
        e.type("ㄋㄏ")
        assertEquals("妳好", e.pick("妳好").commit)
        assertTrue(created.isEmpty())
    }

    /** 詞候選之後是第一個字的單字候選，不再出現「只吃掉一部分的詞」。 */
    @Test fun partialCandidatesAreSingleChars() {
        val st = engine().type("ㄋㄧㄏㄠ")
        val partial = st.candidates.filter { it.consumed < 4 }
        assertTrue(partial.isNotEmpty())
        assertTrue("部分候選：${partial.take(10).map { it.text }}", partial.all { it.text.length == 1 })
        assertEquals("同一個字只出現一次", partial.size, partial.map { it.text }.distinct().size)
    }

    /** 組字區分段顯示，同超注音的「ㄋㄧ ┆ ㄏㄠ」。 */
    @Test fun composingSegments() {
        val e = engine()
        assertEquals(listOf("ㄋㄧ", "ㄏㄠ"), e.type("ㄋㄧㄏㄠ").segments)
        e.reset()
        e.type("ㄒㄧㄠㄗㄋㄢ")
        e.pick("小")
        assertEquals("小", e.state.segments.first())
        assertEquals("ㄗㄋㄢ", e.state.segments.drop(1).joinToString(""))
    }

    /** 「納入字典」用的注音反查（倬需求九）。 */
    @Test fun reverseLookupReadings() {
        assertEquals(listOf("ㄆㄥˊ", "ㄧㄡˇ"), system.readingsFor("朋友"))
        assertEquals(listOf("ㄒㄧㄠˇ", "ㄗ", "ㄋㄢˊ"), system.readingsFor("小資男"))
        assertEquals(4, system.readingsFor("一心一意")!!.size)
        assertNull(system.readingsFor("abc"))
    }

    @Test fun frequencyLearningPromotesChoice() {
        val e = engine()
        e.type("ㄉㄋ")
        val target = e.state.candidates.filter { it.consumed == 2 }.first { it.text != e.state.candidates[0].text }.text
        repeat(3) {
            e.reset(); e.type("ㄉㄋ")
            e.select(e.state.candidates.indexOfFirst { it.text == target && it.consumed == 2 })
        }
        e.type("ㄉㄋ")
        assertEquals(target, e.state.candidates.first().text)
    }

    @Test fun backspaceUndoesPartialSelection() {
        val e = engine()
        e.type("ㄍㄡㄨㄎㄤ")
        e.pick("購")
        assertEquals("購ㄨㄎㄤ", e.state.composing)
        repeat(3) { e.onBackspace() } // 刪掉 ㄤ、ㄎ、ㄨ
        assertEquals("購", e.state.composing)
        e.onBackspace() // 撤銷「購」，還原注音
        assertEquals("ㄍㄡ", e.state.composing)
    }

    @Test fun associations() {
        val words = engine().associations("你好")
        assertTrue("聯想詞：$words", words.isNotEmpty())
    }

    @Test fun extendedSearchFromFourFragments() {
        // 輸入 4 個片段後，也會出現更長的詞
        val st = engine().type("ㄧㄒㄧㄧ")
        assertTrue(st.candidates.any { it.extended && it.text.length > 4 } || st.candidates.any { it.text == "一心一意" })
    }

    @Test fun invalidInputHasNoCandidates() {
        val st = engine().type("ˇ")
        assertTrue(st.candidates.isEmpty())
    }

    @Test fun performance() {
        val e = engine()
        e.type("ㄋㄏ") // 暖身
        e.reset()
        val t0 = System.nanoTime()
        e.type("ㄨㄛㄇㄣㄇㄧㄥㄊㄧㄢㄐㄧㄢ")
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("12 個符號逐鍵輸入耗時 $ms ms", ms < 1500)
    }
}
