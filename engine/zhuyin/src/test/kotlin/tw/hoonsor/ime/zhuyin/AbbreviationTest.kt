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
        val i1 = e.state.candidates.indexOfFirst { it.text == "購物" }
        assertTrue("應有前段候選「購物」：${e.state.candidates.take(10)}", i1 >= 0)
        val r1 = e.select(i1)
        assertNull(r1.commit)
        assertEquals("購物ㄎㄤ", r1.state.composing)
        val i2 = e.state.candidates.indexOfFirst { it.text == "狂" }
        assertTrue(i2 >= 0)
        val r2 = e.select(i2)
        assertEquals("購物狂", r2.commit)
        assertEquals(listOf("購物狂"), created)

        // 下次直接出現在第一個
        e.type("ㄍㄡㄨㄎㄤ")
        assertEquals("購物狂", e.state.candidates.first().text)
        assertEquals(CandidateSource.USER, e.state.candidates.first().source)

        // 刪除（Z6）
        assertTrue(e.deleteUserPhrase(e.state.candidates.first()))
        assertTrue(e.state.candidates.none { it.text == "購物狂" && it.consumed == 5 })
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
        e.select(e.state.candidates.indexOfFirst { it.text == "購物" })
        e.onBackspace(); e.onBackspace() // 刪掉 ㄤ、ㄎ
        assertEquals("購物", e.state.composing)
        e.onBackspace() // 撤銷「購物」，還原注音
        assertEquals("ㄍㄡㄨ", e.state.composing)
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
