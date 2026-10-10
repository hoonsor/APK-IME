package tw.hoonsor.ime.contracts

/**
 * AI 文字改寫（REQUESTS R1，FEATURES 第 6 節）。由 :feature:voice 實作，:app 的編輯面板 AI 欄使用。
 *
 * 兩個方法都會連網、可能耗時數秒：**請在背景執行緒呼叫**。失敗回傳 Result.failure（[RewriteException]），絕不拋例外。
 */
interface TextRewriter {
    /** 依指令改寫 text（潤飾、翻譯等）。 */
    fun rewrite(text: String, instruction: String, hints: EditorHints): Result<String>

    /** AI 寫作：把簡短的指示或重點寫成可以直接送出的完整訊息。 */
    fun compose(brief: String, hints: EditorHints): Result<String>
}

class RewriteException(val code: VoiceErrorCode, message: String) : Exception(message)
