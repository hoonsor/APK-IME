package tw.hoonsor.ime.zhuyin

/**
 * 簡拼注音引擎（對話 A 負責）。純 Kotlin、無 Android 依賴，所有邏輯可在 JVM 單元測試。
 *
 * 只有 :app 使用此介面，所以對話 A 可以自由調整；這裡先寫出設計方向。
 */
interface ZhuyinEngine {
    /** 處理一個注音/聲調/空白按鍵，回傳新的狀態。 */
    fun onSymbol(symbol: Char): EngineState

    fun onBackspace(): EngineState

    /** 選擇第 index 個候選詞。回傳要送出的文字（組字完成時）與新狀態。 */
    fun select(index: Int): SelectResult

    /** 依剛送出的文字產生聯想詞（Z8）。 */
    fun associations(previous: String, limit: Int): List<Candidate>

    fun reset(): EngineState
}

data class EngineState(
    /** 組字區顯示的注音，例如「ㄋㄏ」。 */
    val composing: String,
    val candidates: List<Candidate>,
)

data class Candidate(
    val text: String,
    val source: CandidateSource,
)

/** U = 使用者自建、C = 聯絡人（Z7 標示用）。 */
enum class CandidateSource { SYSTEM, USER, CONTACT }

data class SelectResult(
    /** 要送出的文字；若仍在組字（自建詞流程中）則為 null。 */
    val commit: String?,
    val state: EngineState,
)
