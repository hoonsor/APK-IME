package tw.hoonsor.ime.zhuyin

/**
 * 簡拼注音引擎（對話 A 負責）。純 Kotlin、無 Android 依賴，所有邏輯可在 JVM 單元測試。
 * 只有 :app 使用此介面。非執行緒安全：由 IME 在單一執行緒呼叫。
 */
interface ZhuyinEngine {
    /** 處理一個注音、聲調或空白（= 一聲）按鍵。 */
    fun onSymbol(symbol: Char): EngineState

    /** 刪除一個輸入符號；若已無符號則撤銷上一次的部分選字。 */
    fun onBackspace(): EngineState

    /** 選擇第 index 個候選詞。 */
    fun select(index: Int): SelectResult

    /** 依剛送出的文字產生聯想詞（Z8）。 */
    fun associations(previous: String, limit: Int = 20): List<Candidate>

    /** 刪除使用者詞（Z6），只對 USER / CONTACT 來源有效。 */
    fun deleteUserPhrase(candidate: Candidate): Boolean

    fun reset(): EngineState

    val state: EngineState
}

data class EngineState(
    /** 組字區顯示：已部分選定的字 + 尚未選的注音，例如「小ㄗㄋㄢ」。 */
    val composing: String,
    val candidates: List<Candidate>,
    /**
     * 組字區的分段顯示（超注音以虛線分隔每個字的注音）：已選定的字各為一段，
     * 其餘注音依第一個候選的切分方式分段，例如 ["ㄋㄧ", "ㄏㄠ"]、["小", "ㄗ", "ㄋㄢ"]。
     */
    val segments: List<String> = emptyList(),
) {
    val isComposing: Boolean get() = composing.isNotEmpty()

    companion object {
        val EMPTY = EngineState("", emptyList())
    }
}

data class Candidate(
    val text: String,
    val source: CandidateSource,
    /** 每字讀音，例如 ["ㄋㄧˇ", "ㄏㄠˇ"]。 */
    val readings: List<String>,
    /** 選這個候選會吃掉幾個輸入符號。 */
    val consumed: Int,
    /** true = 延長找詞得到的詞（字數比輸入多）。 */
    val extended: Boolean = false,
    /** 此候選對應的輸入切分，例如 ["ㄋㄧ", "ㄏㄠ"]。 */
    val fragments: List<String> = emptyList(),
)

/** U = 使用者自建、C = 聯絡人（Z7 標示用）。 */
enum class CandidateSource { SYSTEM, USER, CONTACT }

data class SelectResult(
    /** 要送出的文字；若仍有未選完的注音則為 null。 */
    val commit: String?,
    val state: EngineState,
)

/** 引擎把學習結果交給 app 保存（app 寫入 Room）。 */
interface EngineListener {
    /** 多段選字組成新詞（Z5）。 */
    fun onUserPhraseCreated(text: String, readings: List<String>) {}

    /** 使用者選了某個詞，用於頻率學習（Z4）。 */
    fun onPhraseUsed(text: String, readings: List<String>) {}

    fun onUserPhraseDeleted(text: String, readings: List<String>) {}
}

data class EngineConfig(
    /** 輸入片段達到此數量後，也找「比輸入更長」的詞（Z10，預設 4，同超注音）。 */
    val extendFromFragments: Int = 4,
    /** 多段選字自動建詞的最大字數。 */
    val maxAutoPhraseLength: Int = 10,
    val maxCandidates: Int = 120,
    /** 詞候選之後列出的單字候選上限。 */
    val maxSingleChars: Int = 60,
)
