package tw.hoonsor.ime.voice.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import tw.hoonsor.ime.contracts.SecretStore
import tw.hoonsor.ime.contracts.VoiceErrorCode
import tw.hoonsor.ime.contracts.VoiceTermProvider
import java.util.concurrent.ConcurrentHashMap
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

/**
 * 依設定呼叫模型。所有方法都會阻塞，請在背景執行緒呼叫。
 *
 * 失敗處理順序：
 * 1. 主要模型 × 主要 key → 額度用完（429）→ 主要模型 × 備用 key
 * 2. 還是失敗 → 備援模型（同樣先主要 key、再備用 key）
 * 3. 全部因額度失敗 → 拋出 QUOTA，並通知 [quotaListener]（鍵盤跳出提醒）
 *
 * 用完額度的 key 會記住 10 分鐘，這段時間直接用下一把 key，不再浪費一次請求。
 */
class AiRunner(context: Context, private val secrets: SecretStore, private val terms: VoiceTermProvider?) {

    val config = AiConfig(context)

    /** 依序可用的 key（主要、備用；去掉空白與重複）。 */
    private fun keys(p: AiProvider): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (s in listOf(p.secret, p.backupSecret)) {
            val k = runSuspend { secrets.get(s) }?.trim()
            if (!k.isNullOrEmpty() && out.none { it.second == k }) out += s.name to k
        }
        return out
    }

    fun voiceTerms(limit: Int): List<String> = try {
        terms?.let { runSuspend { it.voiceTerms(limit) } } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** 回傳 (結果, 使用的模型說明)。 */
    fun chat(primary: AiRole, backup: AiRole, system: String, user: String, timeoutMs: Int): Pair<String, String> =
        withFallback(primary, backup) { c -> AiHttp.chat(c, system, user, timeoutMs) }

    fun transcribe(wav: ByteArray, language: String?, prompt: String?): Pair<String, String> =
        withFallback(AiRole.STT, AiRole.STT_BACKUP) { c -> AiHttp.transcribe(c, wav, language, prompt) }

    /** 一個模型：依序試每把 key；只有「額度用完」才換下一把（其他錯誤換 key 也沒用）。 */
    private fun runModel(choice: ModelChoice, run: (AiHttp.Call) -> String): String {
        val ks = keys(choice.provider)
        if (ks.isEmpty()) throw AiException(VoiceErrorCode.NO_API_KEY, "尚未設定 ${choice.provider.label} 的 API key")
        val now = System.currentTimeMillis()
        val fresh = ks.filter { (exhausted["${it.first}|${choice.model}"] ?: 0L) < now }
        if (fresh.isEmpty()) throw AiException(VoiceErrorCode.QUOTA, "${choice.provider.label} ${choice.model} 的免費額度已用完")
        var last: AiException? = null
        for ((name, key) in fresh) {
            try {
                return run(AiHttp.Call(config.baseUrl(choice.provider), key, choice.model))
            } catch (e: AiException) {
                if (e.code != VoiceErrorCode.QUOTA) throw e
                Log.w(TAG, "quota: $name ${choice.model}, trying next key")
                exhausted["$name|${choice.model}"] = System.currentTimeMillis() + EXHAUSTED_MS
                last = e
            }
        }
        throw last!!
    }

    private fun withFallback(primary: AiRole, backup: AiRole, run: (AiHttp.Call) -> String): Pair<String, String> {
        val p = config.choice(primary) ?: throw AiException(VoiceErrorCode.NO_API_KEY, "尚未設定「${primary.label}」的模型")
        val first = try {
            return runModel(p, run) to "${p.provider.label} ${p.model}"
        } catch (e: AiException) {
            e
        }
        val b = config.choice(backup)
        val second: AiException? = if (b == null || b == p) null else {
            Log.w(TAG, "${primary.name} failed (${first.code}), trying backup")
            try {
                return runModel(b, run) to "${b.provider.label} ${b.model}（備援）"
            } catch (e: AiException) {
                e
            }
        }
        // 主要、備援都因額度失敗（或主要額度用完、備援沒設定）→ 通知鍵盤跳出提醒
        if (first.code == VoiceErrorCode.QUOTA && (second == null || second.code == VoiceErrorCode.QUOTA)) {
            notifyQuota(primary)
            throw AiException(VoiceErrorCode.QUOTA, "免費額度已達上限")
        }
        throw first
    }

    companion object {
        private const val TAG = "AiRunner"
        private const val EXHAUSTED_MS = 10 * 60_000L

        /** key 名稱|模型 → 額度用完的 key 在這個時間之前先跳過。 */
        private val exhausted = ConcurrentHashMap<String, Long>()

        /** 額度用完時在主執行緒通知（參數是用途：STT、POLISH、REWRITE）。 */
        @Volatile var quotaListener: ((AiRole) -> Unit)? = null

        private val main = Handler(Looper.getMainLooper())

        private fun notifyQuota(role: AiRole) {
            val l = quotaListener ?: return
            main.post { l(role) }
        }

        /** 設定頁換了 key 之後清掉「已用完」的紀錄。 */
        fun resetQuota() = exhausted.clear()
    }
}
