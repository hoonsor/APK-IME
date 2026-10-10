package tw.hoonsor.ime.voice.ai

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.ime.contracts.VoiceErrorCode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID

class AiException(val code: VoiceErrorCode, message: String) : Exception(message)

/**
 * OpenAI 相容 API 用戶端（Groq、MiniMax、OpenAI 共用）。只用 Android 內建的 HttpURLConnection，沒有額外依賴。
 * **所有方法都會阻塞，請在背景執行緒呼叫。** 錯誤訊息絕不包含 API key。
 */
object AiHttp {

    data class Call(val base: String, val key: String, val model: String)

    /** chat/completions，回傳 content（未清洗）。 */
    fun chat(c: Call, system: String, user: String, timeoutMs: Int = 15_000, temperature: Double = 0.2, maxTokens: Int = 2048): String {
        val body = JSONObject()
            .put("model", c.model)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val resp = request("${c.base}/chat/completions", c.key, "application/json; charset=utf-8",
            body.toString().toByteArray(Charsets.UTF_8), timeoutMs)
        return try {
            val msg = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            msg.optString("content", "")
        } catch (e: Exception) {
            throw AiException(VoiceErrorCode.PROVIDER_ERROR, "回應格式無法解析：${resp.take(200)}")
        }
    }

    /** audio/transcriptions（multipart），回傳 text。 */
    fun transcribe(c: Call, wav: ByteArray, language: String?, prompt: String?, timeoutMs: Int = 25_000): String {
        val boundary = "----zhuyin" + UUID.randomUUID().toString().replace("-", "")
        val out = ByteArrayOutputStream(wav.size + 1024)
        fun field(name: String, value: String) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
        }
        field("model", c.model)
        field("response_format", "json")
        if (!language.isNullOrEmpty()) field("language", language)
        if (!prompt.isNullOrEmpty()) field("prompt", prompt)
        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(wav)
        out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        val resp = request("${c.base}/audio/transcriptions", c.key, "multipart/form-data; boundary=$boundary", out.toByteArray(), timeoutMs)
        return try {
            JSONObject(resp).optString("text", "")
        } catch (e: Exception) {
            throw AiException(VoiceErrorCode.PROVIDER_ERROR, "回應格式無法解析：${resp.take(200)}")
        }
    }

    /** GET /models（設定頁「取得模型清單」「測試」用）。 */
    fun listModels(base: String, key: String, timeoutMs: Int = 10_000): List<String> {
        val resp = request("$base/models", key, null, null, timeoutMs)
        return try {
            val arr = JSONObject(resp).getJSONArray("data")
            (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }.filter { it.isNotEmpty() }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun request(url: String, key: String, contentType: String?, body: ByteArray?, timeoutMs: Int): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = timeoutMs
                requestMethod = if (body == null) "GET" else "POST"
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType)
                    setFixedLengthStreamingMode(body.size)
                }
            }
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code >= 400) throw AiException(VoiceErrorCode.PROVIDER_ERROR, httpMessage(code, text))
            return text
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException(VoiceErrorCode.TIMEOUT, "連線逾時")
        } catch (e: UnknownHostException) {
            throw AiException(VoiceErrorCode.NETWORK, "沒有網路連線")
        } catch (e: IOException) {
            throw AiException(VoiceErrorCode.NETWORK, "網路錯誤：${e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() } ?: body
        } catch (e: Exception) { body }
        val hint = when (code) {
            401, 403 -> "API key 無效或沒有權限"
            404 -> "找不到模型（可能已下架或名稱打錯）"
            413 -> "音檔太大"
            429 -> "請求太頻繁或額度用完"
            in 500..599 -> "供應商伺服器錯誤"
            else -> "請求失敗"
        }
        return "$hint（HTTP $code）：${detail.take(200)}"
    }
}
