package tw.hoonsor.ime.app.ai

import tw.hoonsor.ime.app.UserPhraseStore
import tw.hoonsor.ime.contracts.VoiceTermProvider

/**
 * 個人字典 → 語音（V3）：使用者自建的注音詞（2 字以上）與英文詞，依使用次數排序。
 * 只放使用者確認過的詞（MOBILE_VOICE_HANDOFF §5：自動學到的片段會讓錯字變多）。
 */
class AppVoiceTerms(private val store: UserPhraseStore) : VoiceTermProvider {
    override suspend fun voiceTerms(limit: Int): List<String> {
        val used = HashMap<String, Int>()
        for ((t, _, n) in store.usages()) used[t] = (used[t] ?: 0) + n
        val phrases = store.phrases().map { it.text }.filter { it.length >= 2 }.distinct()
            .sortedByDescending { used[it] ?: 0 }
        val words = store.words().filter { it.length in 2..30 }
        return (phrases + words).distinct().take(limit)
    }
}
