package tw.hoonsor.ime.app.ai

import tw.hoonsor.ime.app.UserPhraseStore
import tw.hoonsor.ime.contracts.VoiceTermProvider
import tw.hoonsor.vocab.VocabClient
import tw.hoonsor.vocab.VocabCore

/**
 * 個人字典 → 語音（V3）：使用者自建的注音詞（2 字以上）與英文詞，依使用次數排序。
 * 只放使用者確認過的詞（MOBILE_VOICE_HANDOFF §5：自動學到的片段會讓錯字變多）。
 * 語音個人詞庫中心的詞併在一起；中心連不上或沒設定時 [vocab] 是空的，行為和原本相同。
 */
class AppVoiceTerms(private val store: UserPhraseStore, private val vocab: VocabClient? = null) : VoiceTermProvider {
    private fun localTerms(): List<String> {
        val used = HashMap<String, Int>()
        for ((t, _, n) in store.usages()) used[t] = (used[t] ?: 0) + n
        val phrases = store.phrases().map { it.text }.filter { it.length >= 2 }.distinct()
            .sortedByDescending { used[it] ?: 0 }
        val words = store.words().filter { it.length in 2..30 }
        return (phrases + words).distinct()
    }

    override suspend fun voiceTerms(limit: Int): List<String> {
        val center = vocab?.terms()?.map { it.term }.orEmpty()
        return VocabCore.mergeForPrompt(center, localTerms(), limit)
    }

    override fun correctTranscript(text: String): String {
        val client = vocab ?: return text
        val corrected = client.correct(text).text
        client.noteUsage(corrected)
        return corrected
    }

    companion object {
        /** 可以提案到中心的本機詞：聯絡人一律排除，詞庫不放人名。 */
        fun proposable(phrases: List<UserPhraseStore.Phrase>, words: List<String>): List<String> =
            (phrases.filter { it.source != "CONTACT" && it.text.length >= 2 }.map { it.text } + words.filter { it.length in 2..30 }).distinct()
    }
}
