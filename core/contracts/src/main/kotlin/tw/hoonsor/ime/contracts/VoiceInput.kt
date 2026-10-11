package tw.hoonsor.ime.contracts

import kotlinx.coroutines.flow.StateFlow

/**
 * 語音輸入控制器。由 :feature:voice 實作，:app 使用。
 *
 * 規則：
 * - 實作內部攔截所有例外，轉成 [VoiceState.Error]，絕不讓例外拋到 IME。
 * - 處理完成後由 controller 自己呼叫 TextTarget 送出文字，然後回到 Idle。
 */
interface VoiceInputController {
    val state: StateFlow<VoiceState>

    /** 開始錄音。若 state 不是 Idle 則忽略。 */
    fun start(mode: VoiceMode, target: TextTarget)

    /** 停止錄音並開始處理（轉錄、潤飾）。 */
    fun stop()

    /** 任何階段都可取消，不送出任何文字，回到 Idle。 */
    fun cancel()

    /** 把最後一次結果改為未潤飾的逐字稿（取代剛送出的文字）。 */
    fun revertLastToRaw()

    /** 釋放資源（麥克風、網路），IME onDestroy 時呼叫。 */
    fun release()
}

enum class VoiceMode {
    /** 一般聽寫：語音 → 潤飾後文字。 */
    DICTATE,

    /** 說話修改：以語音指令改寫目前選取的文字。 */
    EDIT_SELECTION,

    /** 語音翻譯：輸出設定中的目標語言。 */
    TRANSLATE,
}

sealed interface VoiceState {
    data object Idle : VoiceState

    /** @param level 0f..1f 音量，用於畫波形。 */
    data class Recording(val elapsedMs: Long, val level: Float) : VoiceState

    data object Transcribing : VoiceState

    data object Polishing : VoiceState

    /** 文字已送出；app 可短暫顯示後回到鍵盤。controller 會自行轉回 Idle。 */
    data class Done(val result: VoiceResult) : VoiceState

    data class Error(val code: VoiceErrorCode, val message: String) : VoiceState
}

data class VoiceResult(
    val mode: VoiceMode,
    /** STT 原始逐字稿。 */
    val rawTranscript: String,
    /** 實際送出的文字。 */
    val finalText: String,
    val totalLatencyMs: Long,
)

enum class VoiceErrorCode {
    NO_MIC_PERMISSION,
    NO_API_KEY,
    NETWORK,
    TIMEOUT,
    PROVIDER_ERROR,
    EMPTY_AUDIO,
    PASSWORD_FIELD,
    NO_SELECTION,

    /** 免費額度或速率限制用完（HTTP 429／402），主要與備用 key 都一樣。 */
    QUOTA,
    UNKNOWN,
}
