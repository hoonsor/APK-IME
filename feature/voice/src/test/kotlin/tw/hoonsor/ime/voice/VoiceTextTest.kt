package tw.hoonsor.ime.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.ime.voice.ai.Prompts
import tw.hoonsor.ime.voice.ai.Sanitizer
import tw.hoonsor.ime.voice.audio.AudioRules
import tw.hoonsor.ime.voice.text.Hallucination
import tw.hoonsor.ime.voice.text.S2tConverter
import tw.hoonsor.ime.voice.text.TextRules
import java.io.File

/**
 * MOBILE_VOICE_HANDOFF §4、§6、§8 的預期結果（桌面版實測）。不需要麥克風、網路或 API key。
 * 執行：./gradlew :feature:voice:test
 */
class VoiceTextTest {

    // ---------- §8.2 文字規則 ----------

    @Test fun spacesBetweenCjkAndAscii() =
        assertEquals("幫我用 Python 把 CSV 轉成 Excel。", TextRules.finalizeText("幫我用Python把CSV轉成Excel。"))

    @Test fun numbersGetSpaces() =
        assertEquals("明天下午 3 點開會，大概 3,500 元", TextRules.finalizeText("明天下午3點開會，大概3,500元"))

    @Test fun listAndExistingSpacesUnchanged() {
        assertEquals("1. 買牛奶", TextRules.finalizeText("1. 買牛奶"))
        assertEquals("用 Python 把", TextRules.finalizeText("用 Python 把"))
        assertEquals("Hello world, 3:30 PM.", TextRules.finalizeText("Hello world, 3:30 PM."))
    }

    @Test fun halfPunctAfterCjk() = assertEquals("好的，我知道了？", TextRules.finalizeText("好的,我知道了?"))

    @Test fun smallComma() = assertEquals("記得分享給大家，也幫我訂閱那個頻道。", TextRules.finalizeText("記得分享給大家﹐也幫我訂閱那個頻道。"))

    // ---------- §6.1 幻覺過濾 ----------

    @Test fun hallucinationsDropped() {
        assertTrue(Hallucination.isHallucination("謝謝收看。"))
        assertTrue(Hallucination.isHallucination("Thank you."))
        assertTrue(Hallucination.isHallucination("請按讚訂閱。我們下次再見！"))
        assertTrue(Hallucination.isHallucination("通過 通過 通過 通過 通過 通過 通過 通過 通過 通過 通過 通過"))
    }

    @Test fun normalSentenceKept() {
        assertTrue(!Hallucination.isHallucination("記得分享給大家，也幫我訂閱那個頻道。"))
        assertTrue(!Hallucination.isHallucination("明天下午兩點開會。不對,三點。"))
        assertTrue(!Hallucination.isHallucination("好"))
    }

    // ---------- §8.1 輸出清洗 ----------

    @Test fun sanitizerStripsWrappers() {
        assertEquals("我們明天下午 3 點開會。", Sanitizer.clean("<think>想一下</think>\n輸出：我們明天下午 3 點開會。", "x", true))
        assertEquals("你好", Sanitizer.clean("```\n你好\n```", "你好", true))
        assertEquals("你好", Sanitizer.clean("<Draft>你好</Draft>", "你好", true))
        assertEquals("你好嗎？", Sanitizer.clean("「你好嗎？」", "你好嗎", true))
    }

    @Test fun sanitizerAnswerGuard() {
        val q = "你覺得台北明天會下雨嗎"
        // 長度 > max(原文 × 2.5, 原文 + 80) 才判定為「回答」
        val answer = "根據氣象預報，台北明天降雨機率約為百分之七十，建議出門攜帶雨具，並留意午後雷陣雨與強風特報，注意交通安全。" +
            "如果要去戶外活動，最好改到室內，或是準備好替換的衣物與鞋子，也可以隨時查詢中央氣象署的最新資訊。"
        assertNull(Sanitizer.clean(answer, q, guardAnswer = true))
        assertEquals(answer, Sanitizer.clean(answer, q, guardAnswer = false))
        assertEquals("", Sanitizer.clean("  ", q, true))
    }

    // ---------- §4 靜音判斷 ----------

    @Test fun silenceDetection() {
        val silence = List(600) { 0.0063f }
        assertNull(AudioRules.voicedRange(silence))
        val cough = List(100) { 0.002f } + List(3) { 0.1f } + List(100) { 0.002f }
        assertNull(AudioRules.voicedRange(cough))
        val word = List(20) { 0.002f } + List(6) { 0.1f } + List(20) { 0.002f }
        assertEquals(14..31, AudioRules.voicedRange(word))
    }

    // ---------- §5 STT 提示詞 ----------

    @Test fun sttPrompt() {
        assertEquals("以下是台灣繁體中文的口語內容，可能夾雜英文專有名詞，請加上適當的標點符號。常用詞彙：倬、Antigravity。",
            Prompts.sttPrompt(listOf("倬", "Antigravity"), english = false))
        assertEquals("Vocabulary: a, b.", Prompts.sttPrompt(listOf("a", "b"), english = true))
    }

    // ---------- §6.2 簡轉繁（s2tw，不改詞彙） ----------

    @Test fun s2tw() {
        val dir = listOf(File("src/main/assets"), File("feature/voice/src/main/assets")).first { it.exists() }
        val c = S2tConverter(
            File(dir, "opencc_st_phrases.txt").bufferedReader(),
            File(dir, "opencc_st_chars.txt").bufferedReader(),
            File(dir, "opencc_tw_variants.txt").bufferedReader(),
        )
        assertEquals("幫我用 Python 寫一個腳本，然後發給我", c.convert("帮我用 Python 写一个脚本，然后发给我"))
        assertEquals("這個文件", c.convert("这个文件")) // s2tw 不會改成「檔案」
        assertEquals("記得分享給大家", c.convert("记得分享给大家"))
    }
}
