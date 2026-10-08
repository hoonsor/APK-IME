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
 * 使用者詞庫的保存（D1、D4）。
 *
 * MVP 用單一 UTF-8 文字檔，原子寫入（先寫暫存檔再改名），備份／還原就是複製這個檔。
 * 之後要換成 Room（core:dictionary）時，只需替換這個類別，引擎不受影響。
 *
 * 格式（每行以 TAB 分隔）：
 *   P  詞  讀音（空白分隔）  來源(USER/CONTACT)  頻率
 *   U  詞  讀音  使用次數        ← 系統詞的學習次數
 */
class UserPhraseStore(context: Context) {

    data class Phrase(val text: String, val readings: List<String>, val source: String, val freq: Int)

    private val file = File(context.filesDir, "user_dict.tsv")
    private val phrases = LinkedHashMap<String, Phrase>()
    private val usage = HashMap<String, Pair<Pair<String, List<String>>, Int>>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    private fun key(text: String, readings: List<String>) = text + "|" + readings.joinToString(" ")

    /** 在背景執行緒呼叫。 */
    fun load() {
        if (!file.exists()) return
        try {
            file.bufferedReader().useLines { lines -> lines.forEach { parseLine(it) } }
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
        }
    }

    private fun parseLine(line: String) {
        val f = line.split('\t')
        when {
            f.size >= 5 && f[0] == "P" -> {
                val r = f[2].split(' ').filter { it.isNotEmpty() }
                phrases[key(f[1], r)] = Phrase(f[1], r, f[3], f[4].toIntOrNull() ?: 0)
            }
            f.size >= 4 && f[0] == "U" -> {
                val r = f[2].split(' ').filter { it.isNotEmpty() }
                usage[key(f[1], r)] = (f[1] to r) to (f[3].toIntOrNull() ?: 0)
            }
        }
    }

    fun phrases(): List<Phrase> = phrases.values.toList()

    fun usages(): List<Triple<String, List<String>, Int>> = usage.values.map { Triple(it.first.first, it.first.second, it.second) }

    fun addPhrase(text: String, readings: List<String>, source: String = "USER") {
        phrases[key(text, readings)] = Phrase(text, readings, source, 0)
        scheduleSave()
    }

    fun removePhrase(text: String, readings: List<String>) {
        phrases.remove(key(text, readings))
        usage.remove(key(text, readings))
        scheduleSave()
    }

    fun recordUse(text: String, readings: List<String>) {
        val k = key(text, readings)
        val old = usage[k]?.second ?: 0
        usage[k] = (text to readings) to (old + 1)
        scheduleSave()
    }

    /** 1 秒內的多次變更合併成一次寫檔。 */
    private fun scheduleSave() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 1000)
    }

    private fun snapshot(): String = buildString {
        for (p in phrases.values) append("P\t${p.text}\t${p.readings.joinToString(" ")}\t${p.source}\t${p.freq}\n")
        for ((pair, n) in usage.values) append("U\t${pair.first}\t${pair.second.joinToString(" ")}\t$n\n")
    }

    fun saveNow() {
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

    /** 還原：合併匯入的內容（不刪除現有詞）。回傳匯入筆數。 */
    fun importFrom(input: InputStream): Int {
        var n = 0
        input.bufferedReader().useLines { lines ->
            lines.forEach { if (it.startsWith("P\t") || it.startsWith("U\t")) { parseLine(it); n++ } }
        }
        saveNow()
        return n
    }

    companion object {
        private const val TAG = "UserPhraseStore"
    }
}
