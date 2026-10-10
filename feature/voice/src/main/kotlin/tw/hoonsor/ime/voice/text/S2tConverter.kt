package tw.hoonsor.ime.voice.text

import java.io.BufferedReader

/**
 * 簡體 → 台灣繁體（OpenCC `s2tw`：STPhrases + STCharacters → TWVariants，MOBILE_VOICE_HANDOFF §6.2）。
 * **不用 s2twp**：它會改詞彙（「文件」→「檔案」），改掉使用者的原話。
 * 資料在 feature/voice/src/main/assets/（OpenCC，Apache-2.0）。純 Kotlin，可在 JVM 測試。
 */
class S2tConverter(
    phrases: BufferedReader,
    chars: BufferedReader,
    variants: BufferedReader,
) {
    private val phraseMap = HashMap<String, String>(60_000)
    private val charMap = HashMap<Char, String>(5_000)
    private val variantMap = HashMap<Char, String>(100)
    private var maxPhrase = 1

    init {
        read(phrases) { k, v -> phraseMap[k] = v; if (k.length > maxPhrase) maxPhrase = k.length }
        read(chars) { k, v -> if (k.length == 1) charMap[k[0]] = v }
        read(variants) { k, v -> if (k.length == 1) variantMap[k[0]] = v }
        if (maxPhrase > 16) maxPhrase = 16
    }

    private fun read(r: BufferedReader, put: (String, String) -> Unit) = r.useLines { lines ->
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val v = line.substring(tab + 1).trim().split(' ').firstOrNull() ?: continue
            if (v.isNotEmpty()) put(line.substring(0, tab), v)
        }
    }

    fun convert(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var matched = false
            for (n in minOf(maxPhrase, text.length - i) downTo 2) {
                val hit = phraseMap[text.substring(i, i + n)] ?: continue
                sb.append(hit)
                i += n
                matched = true
                break
            }
            if (!matched) {
                val ch = text[i]
                sb.append(charMap[ch] ?: ch.toString())
                i++
            }
        }
        // 台灣異體字（僞→偽、啓→啟…）
        if (variantMap.isEmpty()) return sb.toString()
        val out = StringBuilder(sb.length)
        for (ch in sb) out.append(variantMap[ch] ?: ch.toString())
        return out.toString()
    }
}
