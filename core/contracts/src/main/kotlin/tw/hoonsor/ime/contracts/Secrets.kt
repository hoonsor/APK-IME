package tw.hoonsor.ime.contracts

/**
 * API key 讀取介面。實作必須以 Android Keystore 加密保存，不可寫死在程式碼或 APK。
 * 不提供列出全部 key 的方法，避免 key 被意外寫進 log。
 */
interface SecretStore {
    suspend fun get(provider: SecretProvider): String?

    suspend fun set(provider: SecretProvider, value: String?)
}

enum class SecretProvider {
    OPENAI, ANTHROPIC, GROQ, GOOGLE, MINIMAX,

    /** 備用 key（同帳號其他專案或付費 key）：主要 key 額度用完時自動改用。 */
    GROQ_2, MINIMAX_2, OPENAI_2,
}
