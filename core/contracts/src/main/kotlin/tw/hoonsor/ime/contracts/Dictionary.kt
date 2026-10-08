package tw.hoonsor.ime.contracts

import kotlinx.coroutines.flow.Flow

/** 使用者詞彙。 */
data class UserPhrase(
    val id: Long = 0,
    /** 詞本身，例如「小資男」。 */
    val text: String,
    /** 每個字的完整注音，例如 ["ㄒㄧㄠˇ", "ㄗ", "ㄋㄢˊ"]，長度須等於 text 的字數。 */
    val readings: List<String>,
    val source: PhraseSource,
    /** 使用次數，用於排序。 */
    val frequency: Int = 0,
    val lastUsedEpochMs: Long = 0,
    /** true = 也提供給語音輸入（STT 關鍵詞 + LLM 字典）。 */
    val useForVoice: Boolean = true,
)

enum class PhraseSource { USER, CONTACT, IMPORTED }

/** 常用短語（快速插入的整段文字）。 */
data class QuickPhrase(
    val id: Long = 0,
    val label: String,
    val content: String,
    val sortOrder: Int = 0,
)

/** 使用者詞庫完整介面。由 :core:dictionary 實作。 */
interface UserDictionary {
    fun observeAll(): Flow<List<UserPhrase>>

    /** 依注音前綴查詢（給引擎用）；abbreviation 為每字的輸入片段。 */
    suspend fun findByReadingPrefixes(abbreviation: List<String>, limit: Int): List<UserPhrase>

    suspend fun upsert(phrase: UserPhrase): Long

    suspend fun delete(id: Long)

    suspend fun recordUse(id: Long)

    fun observeQuickPhrases(): Flow<List<QuickPhrase>>

    suspend fun upsertQuickPhrase(phrase: QuickPhrase): Long

    suspend fun deleteQuickPhrase(id: Long)
}

/**
 * 語音模組唯一需要的詞庫介面（唯讀）。
 * :core:dictionary 實作；voice-sandbox 用假資料實作。
 */
interface VoiceTermProvider {
    /** 回傳要提供給語音的詞，依重要性排序（常用優先），最多 limit 筆。 */
    suspend fun voiceTerms(limit: Int): List<String>
}
