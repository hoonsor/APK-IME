package tw.hoonsor.ime.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import tw.hoonsor.ime.app.UserPhraseStore
import java.io.File
import java.util.concurrent.Executors

/**
 * 剪貼簿歷史（類似電腦版 Ditto，需求十三）。
 *
 * Android 10 起只有「目前使用中的輸入法」能讀取剪貼簿，所以由輸入法在開啟時與剪貼簿變動時擷取。
 * 只記錄文字；釘選的項目不會被自動清除。資料只存在本機（clip_history.tsv）。
 */
class ClipboardHistory private constructor(context: Context) {

    data class Clip(val text: String, val time: Long, val pinned: Boolean)

    private val app = context.applicationContext
    private val file = File(app.filesDir, "clip_history.tsv")
    private val items = ArrayList<Clip>()   // 新的在前
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }
    private var loaded = false

    /** 最近一次「新複製」的內容，用於候選列 chip；被貼上或開始打字後清除。 */
    var recent: Clip? = null
        private set

    val listeners = ArrayList<() -> Unit>()

    @Synchronized
    fun load() {
        if (loaded) return
        if (file.exists()) {
            try {
                file.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val f = line.split('\t')
                        if (f.size >= 3) items += Clip(UserPhraseStore.unescape(f[2]), f[0].toLongOrNull() ?: 0, f[1] == "1")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "load failed", e)
            }
        }
        loaded = true
    }

    @Synchronized fun all(): List<Clip> = items.toList()

    /** 新增一筆；與最新一筆相同時忽略，與舊項目相同時移到最前面（保留釘選）。 */
    @Synchronized
    fun add(text: String, maxItems: Int): Boolean {
        load()
        if (text.isBlank() || text.length > MAX_LEN) return false
        if (items.firstOrNull()?.text == text) return false
        val old = items.indexOfFirst { it.text == text }
        val pinned = if (old >= 0) items.removeAt(old).pinned else false
        val clip = Clip(text, System.currentTimeMillis(), pinned)
        items.add(0, clip)
        // 超過上限時，從最舊的未釘選項目開始刪
        var i = items.size - 1
        while (items.size > maxItems && i >= 0) {
            if (!items[i].pinned) items.removeAt(i)
            i--
        }
        recent = clip
        changed()
        return true
    }

    @Synchronized
    fun togglePin(text: String) {
        val i = items.indexOfFirst { it.text == text }
        if (i >= 0) { items[i] = items[i].copy(pinned = !items[i].pinned); changed() }
    }

    @Synchronized
    fun remove(text: String) {
        if (items.removeAll { it.text == text }) changed()
        if (recent?.text == text) recent = null
    }

    @Synchronized
    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        recent = null
        changed()
    }

    fun dismissRecent() {
        recent = null
    }

    fun search(q: String): List<Clip> {
        if (q.isBlank()) return all()
        val k = q.trim().lowercase()
        return all().filter { it.text.lowercase().contains(k) }
    }

    /** 從系統剪貼簿讀取目前內容（只有輸入法使用中時才讀得到）。 */
    fun captureFrom(cm: ClipboardManager, maxItems: Int): Boolean = try {
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(app)?.toString() else null
        if (text != null) add(text, maxItems) else false
    } catch (e: Exception) {
        Log.w(TAG, "capture failed", e)
        false
    }

    private fun changed() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 800)
        main.post { listeners.toList().forEach { it() } }
    }

    fun saveNow() {
        val data = synchronized(this) {
            buildString { for (c in items) append("${c.time}\t${if (c.pinned) 1 else 0}\t${UserPhraseStore.esc(c.text)}\n") }
        }
        io.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(data)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            } catch (e: Exception) {
                Log.w(TAG, "save failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "ClipboardHistory"
        private const val MAX_LEN = 20_000

        @Volatile private var instance: ClipboardHistory? = null

        fun get(context: Context): ClipboardHistory =
            instance ?: synchronized(this) { instance ?: ClipboardHistory(context).also { instance = it } }
    }
}
