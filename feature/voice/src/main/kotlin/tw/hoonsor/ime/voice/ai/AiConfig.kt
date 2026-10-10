package tw.hoonsor.ime.voice.ai

import android.content.Context
import tw.hoonsor.ime.contracts.SecretProvider

/** 支援的 AI 供應商（都是 OpenAI 相容 API）。模型清單只是建議值，實際以「取得模型清單」為準。 */
enum class AiProvider(
    val label: String,
    val defaultBase: String,
    val secret: SecretProvider,
    val sttModels: List<String>,
    val chatModels: List<String>,
) {
    GROQ(
        "Groq", "https://api.groq.com/openai/v1", SecretProvider.GROQ,
        sttModels = listOf("whisper-large-v3", "whisper-large-v3-turbo"),
        chatModels = listOf("qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"),
    ),
    MINIMAX(
        "MiniMax", "https://api.minimax.io/v1", SecretProvider.MINIMAX,
        sttModels = emptyList(),
        chatModels = listOf("MiniMax-M3"),
    ),
    OPENAI(
        "OpenAI", "https://api.openai.com/v1", SecretProvider.OPENAI,
        sttModels = listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe"),
        chatModels = listOf("gpt-4.1-mini", "gpt-4o-mini"),
    ),
}

/** 各用途分開設定模型（倬：語音輸入與 AI 潤飾可用不同模型）。 */
enum class AiRole(val label: String, val isStt: Boolean, val optional: Boolean) {
    STT("語音轉文字", true, false),
    STT_BACKUP("語音轉文字（備援）", true, true),
    POLISH("語音潤飾", false, false),
    POLISH_BACKUP("語音潤飾（備援）", false, true),
    REWRITE("文字 AI（潤飾／寫作／翻譯）", false, false),
    REWRITE_BACKUP("文字 AI（備援）", false, true),
}

data class ModelChoice(val provider: AiProvider, val model: String)

/**
 * AI 模型設定（SharedPreferences "ai_config"）。:app 的「AI 模型設定」頁面讀寫，語音與改寫模組讀取。
 * 模型 ID 存成可編輯字串（MOBILE_VOICE_HANDOFF §2.2：模型會被無預警下架）。
 */
class AiConfig(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("ai_config", Context.MODE_PRIVATE)

    fun choice(role: AiRole): ModelChoice? {
        val p = sp.getString("${role.name}.provider", null)
        if (p == NONE) return null
        val provider = p?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: DEFAULTS[role]?.provider ?: return null
        val model = sp.getString("${role.name}.model", null)?.takeIf { it.isNotBlank() }
            ?: DEFAULTS[role]?.takeIf { it.provider == provider }?.model
            ?: (if (role.isStt) provider.sttModels else provider.chatModels).firstOrNull()
            ?: return null
        return ModelChoice(provider, model.trim())
    }

    /** provider = null 表示「不使用」（只限備援）。 */
    fun setChoice(role: AiRole, provider: AiProvider?, model: String?) {
        sp.edit()
            .putString("${role.name}.provider", provider?.name ?: NONE)
            .putString("${role.name}.model", model?.trim() ?: "")
            .apply()
    }

    fun baseUrl(p: AiProvider): String =
        sp.getString("base.${p.name}", null)?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") } ?: p.defaultBase

    fun setBaseUrl(p: AiProvider, url: String?) {
        sp.edit().putString("base.${p.name}", url?.trim() ?: "").apply()
    }

    /** 聽寫是否用 LLM 潤飾（關閉時只做 STT＋文字規則）。 */
    var polishEnabled: Boolean
        get() = sp.getBoolean("polishEnabled", true)
        set(v) = sp.edit().putBoolean("polishEnabled", v).apply()

    /** 說話語言："zh"、"en"、"auto"。 */
    var language: String
        get() = sp.getString("language", "zh") ?: "zh"
        set(v) = sp.edit().putString("language", v).apply()

    /** 翻譯模式的目標語言。 */
    var translateTo: String
        get() = sp.getString("translateTo", "英文 (English)") ?: "英文 (English)"
        set(v) = sp.edit().putString("translateTo", v).apply()

    /** 語音按鈕：true = 按住說話、放開送出；false = 點一下開始、再點一下送出。 */
    var holdToTalk: Boolean
        get() = sp.getBoolean("holdToTalk", false)
        set(v) = sp.edit().putBoolean("holdToTalk", v).apply()

    /** 打開語音面板就自動開始錄音。 */
    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", false)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()

    companion object {
        private const val NONE = "NONE"

        /** 預設值依 MOBILE_VOICE_HANDOFF §2 實測結果。 */
        val DEFAULTS: Map<AiRole, ModelChoice?> = mapOf(
            AiRole.STT to ModelChoice(AiProvider.GROQ, "whisper-large-v3"),
            AiRole.STT_BACKUP to ModelChoice(AiProvider.GROQ, "whisper-large-v3-turbo"),
            AiRole.POLISH to ModelChoice(AiProvider.GROQ, "qwen/qwen3.8-27b"),
            AiRole.POLISH_BACKUP to ModelChoice(AiProvider.GROQ, "openai/gpt-oss-120b"),
            AiRole.REWRITE to ModelChoice(AiProvider.MINIMAX, "MiniMax-M3"),
            AiRole.REWRITE_BACKUP to ModelChoice(AiProvider.GROQ, "qwen/qwen3.8-27b"),
        )
    }
}
