package tw.hoonsor.ime.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * 使用者資料（D1、D2、D4）：注音自建詞、英文詞、常用短語、學習次數。
 *
 * - 整個 App 只有一個實例（[get]），輸入法與設定頁共用，避免互相覆蓋。
 * - 單一 UTF-8 文字檔，原子寫入（先寫暫存檔再改名），1 秒內多次變更合併寫檔。
 * - 設定頁的修改會增加 [externalVersion]，輸入法下次開啟時據此重新載入。
 *
 * 檔案格式（TAB 分隔；文字中的 \t \n \\ 會跳脫）：
 *   P  詞  讀音（空白分隔）  來源(USER/CONTACT)  頻率
 *   E  英文詞（含符號）
 *   Q  常用短語內容
 *   U  詞  讀音  使用次數
 */
class UserPhraseStore private constructor(context: Context) {

    data class Phrase(val text: String, val readings: List<String>, val source: String, val freq: Int)

    private val file = File(context.applicationContext.filesDir, "user_dict.tsv")
    private val phrases = LinkedHashMap<String, Phrase>()
    private val words = LinkedHashSet<String>()
    private val quick = ArrayList<String>()
    private val usage = HashMap<String, Pair<Pair<String, List<String>>, Int>>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    @Volatile
    var loaded = false
        private set

    /** 設定頁（外部）修改的次數。 */
    @Volatile
    var externalVersion = 0
        private set

    private fun key(text: String, readings: List<String>) = text + "|" + readings.joinToString(" ")

    /** 在背景執行緒呼叫；重複呼叫無作用。 */
    @Synchronized
    fun load() {
        if (loaded) return
        if (file.exists()) {
            try {
                file.bufferedReader().useLines { lines -> lines.forEach { parseLine(it) } }
            } catch (e: Exception) {
                Log.w(TAG, "load failed", e)
            }
        }
        loaded = true
    }

    private fun parseLine(line: String) {
        val f = line.split('\t').map { unescape(it) }
        when {
            f.size >= 5 && f[0] == "P" -> {
                val r = f[2].split(' ').filter { it.isNotEmpty() }
                phrases[key(f[1], r)] = Phrase(f[1], r, f[3], f[4].toIntOrNull() ?: 0)
            }
            f.size >= 2 && f[0] == "E" -> if (f[1].isNotEmpty()) words += f[1]
            f.size >= 2 && f[0] == "Q" -> if (f[1].isNotEmpty() && f[1] !in quick) quick += f[1]
            f.size >= 4 && f[0] == "U" -> {
                val r = f[2].split(' ').filter { it.isNotEmpty() }
                usage[key(f[1], r)] = (f[1] to r) to (f[3].toIntOrNull() ?: 0)
            }
        }
    }

    // ---------- 注音自建詞 ----------

    @Synchronized fun phrases(): List<Phrase> = phrases.values.toList()

    @Synchronized fun usages(): List<Triple<String, List<String>, Int>> =
        usage.values.map { Triple(it.first.first, it.first.second, it.second) }

    @Synchronized
    fun addPhrase(text: String, readings: List<String>, source: String = "USER", external: Boolean = false) {
        phrases[key(text, readings)] = Phrase(text, readings, source, phrases[key(text, readings)]?.freq ?: 0)
        changed(external)
    }

    @Synchronized
    fun removePhrase(text: String, readings: List<String>, external: Boolean = false) {
        phrases.remove(key(text, readings))
        usage.remove(key(text, readings))
        changed(external)
    }

    @Synchronized
    fun recordUse(text: String, readings: List<String>) {
        val k = key(text, readings)
        val old = usage[k]?.second ?: 0
        usage[k] = (text to readings) to (old + 1)
        changed(false)
    }

    // ---------- 英文詞（字母＋符號） ----------

    @Synchronized fun words(): List<String> = words.toList()

    @Synchronized
    fun addWord(w: String, external: Boolean = false): Boolean {
        val ok = w.isNotBlank() && words.add(w.trim())
        if (ok) changed(external)
        return ok
    }

    @Synchronized
    fun removeWord(w: String, external: Boolean = false) {
        if (words.remove(w)) changed(external)
    }

    // ---------- 常用短語 ----------

    @Synchronized fun quickPhrases(): List<String> = quick.toList()

    @Synchronized
    fun setQuickPhrase(index: Int, content: String, external: Boolean = true) {
        if (content.isBlank()) return
        if (index in quick.indices) quick[index] = content else quick += content
        changed(external)
    }

    @Synchronized
    fun removeQuickPhrase(index: Int, external: Boolean = true) {
        if (index in quick.indices) { quick.removeAt(index); changed(external) }
    }

    // ---------- 寫檔 ----------

    private fun changed(external: Boolean) {
        if (external) externalVersion++
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 1000)
    }

    @Synchronized
    private fun snapshot(): String = buildString {
        for (p in phrases.values) append("P\t${esc(p.text)}\t${p.readings.joinToString(" ")}\t${p.source}\t${p.freq}\n")
        for (w in words) append("E\t${esc(w)}\n")
        for (q in quick) append("Q\t${esc(q)}\n")
        for ((pair, n) in usage.values) append("U\t${esc(pair.first)}\t${pair.second.joinToString(" ")}\t$n\n")
    }

    fun saveNow() {
        if (!loaded) return // 尚未載入就寫檔會覆蓋掉原有資料
        val data = snapshot()
        io.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(data)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            } catch (e: Exception) {
                Log.w(TAG, "save failed", e)
            }
        }
    }

    fun exportTo(out: OutputStream) {
        out.write(snapshot().toByteArray(Charsets.UTF_8))
    }

    /** 還原：合併匯入的內容（不刪除現有資料）。回傳匯入筆數。 */
    fun importFrom(input: InputStream): Int {
        var n = 0
        synchronized(this) {
            input.bufferedReader().useLines { lines ->
                lines.forEach { if (it.length > 2 && it[1] == '\t' && it[0] in "PEQU") { parseLine(it); n++ } }
            }
        }
        changed(true)
        saveNow()
        return n
    }

    companion object {
        private const val TAG = "UserPhraseStore"

        @Volatile
        private var instance: UserPhraseStore? = null

        fun get(context: Context): UserPhraseStore =
            instance ?: synchronized(this) { instance ?: UserPhraseStore(context).also { instance = it } }

        fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        fun unescape(s: String): String {
            if ('\\' !in s) return s
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> sb.append('\t'); 'n' -> sb.append('\n'); '\\' -> sb.append('\\')
                        else -> { sb.append(c); sb.append(s[i + 1]) }
                    }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}
