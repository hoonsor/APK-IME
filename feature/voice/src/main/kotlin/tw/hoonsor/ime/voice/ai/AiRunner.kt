package tw.hoonsor.ime.voice.ai

import android.content.Context
import android.util.Log
import tw.hoonsor.ime.contracts.SecretStore
import tw.hoonsor.ime.contracts.VoiceErrorCode
import tw.hoonsor.ime.contracts.VoiceTermProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * 呼叫 suspend 函式（SecretStore、VoiceTermProvider 的實作都不會真的暫停）。
 * 不依賴 kotlinx.coroutines，避免為了兩個簡單呼叫引入整個執行環境。
 */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "suspend call did not complete synchronously" }.getOrThrow()
}

/** 依設定呼叫模型；主要模型失敗時改用備援（V10）。所有方法都會阻塞，請在背景執行緒呼叫。 */
class AiRunner(context: Context, private val secrets: SecretStore, private val terms: VoiceTermProvider?) {

    val config = AiConfig(context)

    fun call(choice: ModelChoice): AiHttp.Call {
        val key = runSuspend { secrets.get(choice.provider.secret) }
        if (key.isNullOrBlank()) throw AiException(VoiceErrorCode.NO_API_KEY, "尚未設定 ${choice.provider.label} 的 API key")
        return AiHttp.Call(config.baseUrl(choice.provider), key.trim(), choice.model)
    }

    fun voiceTerms(limit: Int): List<String> = try {
        terms?.let { runSuspend { it.voiceTerms(limit) } } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** 回傳 (結果, 使用的模型說明)。兩個都失敗時拋出主要模型的錯誤。 */
    fun chat(primary: AiRole, backup: AiRole, system: String, user: String, timeoutMs: Int): Pair<String, String> =
        withFallback(primary, backup) { c -> AiHttp.chat(c, system, user, timeoutMs) }

    fun transcribe(wav: ByteArray, language: String?, prompt: String?): Pair<String, String> =
        withFallback(AiRole.STT, AiRole.STT_BACKUP) { c -> AiHttp.transcribe(c, wav, language, prompt) }

    private fun withFallback(primary: AiRole, backup: AiRole, run: (AiHttp.Call) -> String): Pair<String, String> {
        val p = config.choice(primary) ?: throw AiException(VoiceErrorCode.NO_API_KEY, "尚未設定「${primary.label}」的模型")
        val first = try {
            return run(call(p)) to "${p.provider.label} ${p.model}"
        } catch (e: AiException) {
            e
        }
        val b = config.choice(backup)
        if (b == null || b == p) throw first
        Log.w(TAG, "${primary.name} failed (${first.code}), trying backup")
        return try {
            run(call(b)) to "${b.provider.label} ${b.model}（備援）"
        } catch (e: AiException) {
            throw first
        }
    }

    companion object {
        private const val TAG = "AiRunner"
    }
}
