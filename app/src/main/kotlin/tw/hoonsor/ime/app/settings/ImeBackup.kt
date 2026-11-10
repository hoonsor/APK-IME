package tw.hoonsor.ime.app.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.backup.AutoBackup
import tw.hoonsor.ime.app.UserPhraseStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 倬注音的每日自動備份（hoonsor-kit android-auto-backup）：一個 zip，內容是
 *   manifest.json        格式名與版本
 *   user_dict.tsv        使用者字典原格式（自建詞、英文詞、常用短語、學習紀錄）
 *   prefs/ime.json       鍵盤偏好（SharedPreferences "ime"，每個值記型別）
 *   prefs/ai_config.json AI 模型設定（SharedPreferences "ai_config"，排除名稱像金鑰的鍵）
 *
 * 不備份：
 * - "secrets"：API key 用 Keystore 加密，換機解不開，也不該離開手機
 * - clip_history.tsv：剪貼簿可能有密碼、驗證碼等敏感內容
 * - "app_update"：只是「略過這版」之類的更新狀態，還原沒有意義
 * - user_dict.before-import.tsv：匯入前的暫存
 */
object ImeBackup {
    const val FORMAT = "zhuyin-ime-backup"
    const val VERSION = 1
    const val PREFS_FORMAT = "zhuyin-ime-prefs"

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_DICT = "user_dict.tsv"

    /** 要備份的 SharedPreferences（白名單；不在名單上的一律不碰） */
    val PREF_FILES = listOf("ime", "ai_config")

    private fun prefEntry(name: String) = "prefs/$name.json"

    /** 單一項目的上限，避免讀到異常的 zip 把記憶體吃光（字典正常只有幾百 KB） */
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024

    /** zip 內每個項目用固定時間：內容沒變時 zip 位元組完全相同，AutoBackup 才會判斷「沒變、不另存」 */
    private const val FIXED_TIME = 1767196800000L // 2026-01-01 00:00 UTC

    /** 名稱像金鑰、token、密碼的鍵不備份（目前 ai_config 沒有這種鍵，API key 都在 "secrets"；這是保險） */
    private val SENSITIVE = Regex("(?i)(api[_.-]?key|apikey|token|secret|password|passwd|credential|bearer|auth)")

    fun isSensitiveKey(key: String) = SENSITIVE.containsMatchIn(key)

    // =====================================================================
    // Android 端（讀寫實際資料）
    // =====================================================================

    /** 在背景執行緒呼叫（AutoBackup 的工作或設定頁的 io 執行緒） */
    fun export(ctx: Context, out: OutputStream) {
        val store = UserPhraseStore.get(ctx)
        store.load()
        val dict = ByteArrayOutputStream().also { store.exportTo(it) }.toString(Charsets.UTF_8.name())
        val prefs = PREF_FILES.associateWith { ctx.getSharedPreferences(it, Context.MODE_PRIVATE).all }
        writeZip(out, dict, prefs, appVersion(ctx))
    }

    /**
     * 套用備份內容：字典整份取代（取代前自動存 user_dict.before-import.tsv，可用「復原上次匯入前的字典」反悔），
     * 偏好以備份檔為準（備份裡沒有的鍵恢復預設值）；名稱像金鑰的鍵不動。回傳給使用者看的結果。
     */
    fun restore(ctx: Context, b: Contents): String {
        val parts = ArrayList<String>()
        if (b.dict != null) {
            val store = UserPhraseStore.get(ctx)
            store.load()
            val n = store.replaceAllFromTsv(b.dict)
            parts += "字典 $n 筆"
        }
        for ((name, values) in b.prefs) {
            if (name !in PREF_FILES) continue
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val e = sp.edit()
            sp.all.keys.filterNot { isSensitiveKey(it) }.forEach { e.remove(it) }
            values.forEach { (k, v) ->
                if (isSensitiveKey(k)) return@forEach
                when (v) {
                    is String -> e.putString(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
                }
            }
            e.commit()
            parts += (if (name == "ime") "鍵盤設定" else "AI 模型設定") + " ${values.size} 項"
        }
        return "已還原：" + parts.joinToString("、").ifEmpty { "（沒有內容）" }
    }

    private fun appVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""

    // =====================================================================
    // 純邏輯（ImeBackupTest，JVM 可測）
    // =====================================================================

    /** 備份檔讀出來的內容 */
    data class Contents(
        val dict: String?,
        val prefs: Map<String, Map<String, Any>>,
        val appVersion: String,
    ) {
        /** 字典的資料列數（P／E／Q／U 開頭的行） */
        val dictLines: Int
            get() = dict?.lineSequence()?.count { it.length > 2 && it[1] == '\t' && it[0] in "PEQU" } ?: 0
    }

    fun writeZip(out: OutputStream, dictTsv: String, prefs: Map<String, Map<String, *>>, appVersion: String) {
        val zip = ZipOutputStream(out)
        fun put(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
            zip.write(bytes)
            zip.closeEntry()
        }
        val kept = prefs.filterKeys { it in PREF_FILES }.toSortedMap()
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", "倬注音")
            .put("appVersion", appVersion)
            .put("entries", JSONArray(listOf(ENTRY_DICT) + kept.keys.map { prefEntry(it) }))
        put(ENTRY_MANIFEST, manifest.toString(2).toByteArray(Charsets.UTF_8))
        put(ENTRY_DICT, dictTsv.toByteArray(Charsets.UTF_8))
        kept.forEach { (name, values) ->
            put(prefEntry(name), encodePrefs(name, values).toString(2).toByteArray(Charsets.UTF_8))
        }
        zip.finish() // 不關閉 out，由呼叫端負責
    }

    /** 讀備份 zip；不是倬注音的備份檔丟 IllegalArgumentException。不認得的項目一律略過 */
    fun readZip(input: InputStream): Contents {
        val raw = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                val wanted = name == ENTRY_MANIFEST || name == ENTRY_DICT || PREF_FILES.any { prefEntry(it) == name }
                if (!e.isDirectory && wanted) raw[name] = readLimited(zip, name)
                zip.closeEntry()
            }
        }
        val manifest = raw[ENTRY_MANIFEST]?.let { JSONObject(String(it, Charsets.UTF_8)) }
            ?: throw IllegalArgumentException("這不是倬注音的自動備份檔（沒有 manifest.json）")
        require(manifest.optString("format") == FORMAT) { "這不是倬注音的自動備份檔" }
        require(manifest.optInt("version", 0) in 1..VERSION) { "備份檔版本 ${manifest.optInt("version")} 比這個 App 新，請先更新 App" }
        val prefs = LinkedHashMap<String, Map<String, Any>>()
        for (name in PREF_FILES) {
            val bytes = raw[prefEntry(name)] ?: continue
            prefs[name] = decodePrefs(name, JSONObject(String(bytes, Charsets.UTF_8)))
        }
        return Contents(
            dict = raw[ENTRY_DICT]?.let { String(it, Charsets.UTF_8) },
            prefs = prefs,
            appVersion = manifest.optString("appVersion"),
        )
    }

    private fun readLimited(input: InputStream, name: String): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalArgumentException("備份檔的 $name 太大，可能已損壞")
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    /** 每個值記型別（照語音總控 SettingsBackup 的寫法），還原時才放得回同一種 put；名稱像金鑰的鍵直接略過 */
    fun encodePrefs(name: String, all: Map<String, *>): JSONObject {
        val prefs = JSONObject()
        all.toSortedMap().forEach { (k, v) ->
            if (isSensitiveKey(k)) return@forEach
            val (t, value) = when (v) {
                is String -> "s" to v
                is Boolean -> "b" to v
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                is Set<*> -> "ss" to JSONArray(v.map { it.toString() }.sorted())
                else -> return@forEach
            }
            prefs.put(k, JSONObject().put("t", t).put("v", value))
        }
        return JSONObject().put("format", PREFS_FORMAT).put("version", 1).put("name", name).put("prefs", prefs)
    }

    fun decodePrefs(name: String, j: JSONObject): Map<String, Any> {
        require(j.optString("format") == PREFS_FORMAT) { "備份檔的 $name 設定格式不對" }
        val prefs = j.optJSONObject("prefs") ?: throw IllegalArgumentException("備份檔的 $name 沒有設定內容")
        val out = linkedMapOf<String, Any>()
        prefs.keys().forEach { k ->
            if (isSensitiveKey(k)) return@forEach
            val o = prefs.optJSONObject(k) ?: return@forEach
            out[k] = when (o.optString("t")) {
                "s" -> o.getString("v")
                "b" -> o.getBoolean("v")
                "i" -> o.getInt("v")
                "l" -> o.getLong("v")
                "f" -> o.getDouble("v").toFloat()
                "ss" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> return@forEach
            }
        }
        return out
    }
}

/**
 * 每日自動備份的來源（AndroidManifest 的 tw.hoonsor.backup.SOURCE 指到這裡）。
 * 本專案沒有 lab flavor，所以資料夾固定是 Documents/HoonsorBackup/ZhuyinIME/。
 */
class ZhuyinBackupSource : AutoBackup.Source {
    override fun appName() = "ZhuyinIME"
    override fun extension() = "zip"
    override fun mimeType() = "application/zip"
    override fun write(context: Context, out: OutputStream) = ImeBackup.export(context, out)
}
