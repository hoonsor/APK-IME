package tw.hoonsor.ime.app.text

import android.content.Context
import android.util.Log

/**
 * 繁體 → 簡體轉換（需求八）。資料來自 OpenCC（Apache-2.0）：先比對詞，再逐字轉換。
 * 第一次使用時載入（約 5,500 筆，很小），之後常駐記憶體。
 */
object ChineseConverter {
    @Volatile private var chars: HashMap<Char, String>? = null
    @Volatile private var phrases: HashMap<String, String>? = null
    private var maxPhrase = 1

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (chars != null) return
        try {
            val c = HashMap<Char, String>(6000)
            read(context, "opencc_ts_chars.txt") { k, v -> if (k.length == 1) c[k[0]] = v }
            val p = HashMap<String, String>(600)
            read(context, "opencc_ts_phrases.txt") { k, v ->
                p[k] = v
                if (k.length > maxPhrase) maxPhrase = k.length
            }
            phrases = p
            chars = c
        } catch (e: Exception) {
            Log.e("ChineseConverter", "load failed", e)
            chars = HashMap()
            phrases = HashMap()
        }
    }

    private fun read(context: Context, name: String, put: (String, String) -> Unit) {
        context.assets.open(name).bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                // 多個候選以空白分隔，取第一個
                val v = line.substring(tab + 1).trim().split(' ').firstOrNull() ?: continue
                if (v.isNotEmpty()) put(line.substring(0, tab), v)
            }
        }
    }

    fun toSimplified(text: String): String {
        val c = chars ?: return text
        val p = phrases ?: return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var matched = false
            val maxLen = minOf(maxPhrase, text.length - i)
            for (n in maxLen downTo 2) {
                val hit = p[text.substring(i, i + n)] ?: continue
                sb.append(hit)
                i += n
                matched = true
                break
            }
            if (!matched) {
                val ch = text[i]
                sb.append(c[ch] ?: ch.toString())
                i++
            }
        }
        return sb.toString()
    }
}
