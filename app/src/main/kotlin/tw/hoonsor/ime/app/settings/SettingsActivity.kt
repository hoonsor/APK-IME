package tw.hoonsor.ime.app.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import tw.hoonsor.ime.app.UserPhraseStore
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.app.update.CheckResult
import tw.hoonsor.ime.app.update.ReleaseInfo
import tw.hoonsor.ime.app.update.UpdateChecker
import tw.hoonsor.ime.app.update.UpdateDialog
import tw.hoonsor.ime.app.update.UpdateInstaller

/**
 * 設定頁（參數設定）：啟用輸入法、測試、偏好開關、詞庫備份／還原（D4）、App 更新。
 * 只用 Android 框架元件，不依賴 AndroidX。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpi(20f)
            setPadding(p, p, p, p)
        }
        col.addView(TextView(this).apply {
            text = "倬注音"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(0, dpi(4f), 0, dpi(4f))
        })
        fun title(t: String) = col.addView(TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dpi(18f), 0, dpi(6f))
        })
        fun button(t: String, onClick: () -> Unit) = col.addView(Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        })
        fun switch(t: String, get: () -> Boolean, set: (Boolean) -> Unit) = col.addView(Switch(this).apply {
            text = t
            isChecked = get()
            setPadding(0, dpi(8f), 0, dpi(8f))
            setOnCheckedChangeListener { _, v -> set(v) }
        })

        title("1. 啟用輸入法")
        button("開啟系統輸入法設定") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("切換輸入法") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }

        title("2. 試打")
        col.addView(EditText(this).apply { hint = "在這裡測試打字"; minLines = 3 },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        title("AI 與語音")
        button("AI 模型設定（API key、模型、語音選項）") {
            startActivity(Intent(this, tw.hoonsor.ime.app.ai.AiSettingsActivity::class.java))
        }

        title("3. 偏好設定")
        switch("按鍵振動", { prefs.haptics }) { prefs.haptics = it }
        switch("英文鍵盤上排固定顯示數字", { prefs.numberRow }) { prefs.numberRow = it }
        switch("顯示聯想字詞", { prefs.associations }) { prefs.associations = it }
        switch("工具列顯示剪貼簿按鈕", { prefs.toolbarClipboard }) { prefs.toolbarClipboard = it }
        switch("扇子顯示剪貼簿項目", { prefs.fanClipboard }) { prefs.fanClipboard = it }
        button("鍵盤高度恢復預設") {
            prefs.panelHeightDp = Prefs.DEFAULT_HEIGHT_DP
            Toast.makeText(this, "下次開啟鍵盤時生效", Toast.LENGTH_SHORT).show()
        }

        title("4. 使用者字典")
        button("檢視／編輯 注音自建詞") { openDict(DictionaryActivity.MODE_PHRASES) }
        button("檢視／編輯 英文詞（字母＋符號）") { openDict(DictionaryActivity.MODE_WORDS) }
        button("常用短語") { openDict(DictionaryActivity.MODE_QUICK) }
        col.addView(TextView(this).apply {
            text = "匯出／匯入（中文詞、英文詞、常用短語、學習紀錄）"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dpi(10f), 0, dpi(2f))
        })
        button("匯出字典並分享（Google Drive、Dropbox、Email…）") { exportDictionary(share = true) }
        button("匯出字典存到手機檔案…") { exportDictionary(share = false) }
        button("匯入字典（從檔案、Google Drive、Dropbox）…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain",
                    "text/tab-separated-values", "application/vnd.ms-excel", "application/octet-stream"))
            }, REQ_IMPORT)
        }
        button("復原上次匯入前的字典") {
            val store = UserPhraseStore.get(this)
            if (!store.hasImportBackup()) { toast("沒有可復原的匯入紀錄"); return@button }
            android.app.AlertDialog.Builder(this)
                .setMessage("把字典恢復成上次匯入之前的內容？")
                .setPositiveButton("復原") { _, _ -> store.load(); toast(if (store.restoreImportBackup()) "已復原" else "復原失敗") }
                .setNegativeButton("取消", null).show()
        }
        col.addView(TextView(this).apply {
            text = "匯出的是 CSV 檔，可用 Excel 或 Google 試算表編輯，欄位：類型、內容、注音、使用次數。\n" +
                "・類型填「中文詞」「英文詞」「常用短語」；中文詞的注音可留空，匯入時自動判斷。\n" +
                "・Excel 存檔請選「CSV UTF-8（逗號分隔）」；一般 CSV 也能匯入。\n" +
                "・匯入時可選「合併」（只新增）或「取代」（以檔案為準，檔案裡刪掉的詞手機也刪掉）；匯入前會自動保留一份，可復原。\n" +
                "・在這裡修改字典後，把鍵盤收起再打開一次就會生效。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })

        title("5. 剪貼簿")
        switch("記錄剪貼簿歷史", { prefs.clipCapture }) { prefs.clipCapture = it }
        switch("剛複製的內容在候選列顯示「一點即貼」", { prefs.clipChip }) { prefs.clipChip = it }
        col.addView(TextView(this).apply {
            text = "只記錄文字，只存在這支手機；標記為敏感的內容（例如密碼管理器）不會記錄。" +
                "\n開啟方式：扇子滑向「剪」、編輯面板長按「貼上」，或在上方開啟工具列按鈕。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        button("清除所有剪貼簿紀錄（含釘選）") {
            android.app.AlertDialog.Builder(this)
                .setMessage("確定要清除所有剪貼簿紀錄嗎？")
                .setPositiveButton("清除") { _, _ ->
                    val h = tw.hoonsor.ime.app.clipboard.ClipboardHistory.get(this)
                    h.load()
                    h.all().forEach { h.remove(it.text) }
                    toast("已清除")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        title("6. App 更新")
        col.addView(TextView(this).apply {
            text = "目前版本 v${UpdateChecker.currentVersion}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        val updateStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            visibility = android.view.View.GONE
        }
        button("檢查更新") { checkUpdate(updateStatus) }
        col.addView(updateStatus)
        col.addView(TextView(this).apply {
            text = "每天第一次打開這個設定頁時會自動檢查一次。新版從 GitHub 下載後由系統安裝，字典、設定與 API key 都會保留。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })

        val scroll = ScrollView(this).apply {
            addView(col)
            isFillViewport = true
            // Android 15+ 強制全螢幕：上方狀態列、下方手勢列的空間要自己留，否則內容會被蓋住
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = if (android.os.Build.VERSION.SDK_INT >= 30)
                    insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                else null
                @Suppress("DEPRECATION")
                v.setPadding(0, bars?.top ?: insets.systemWindowInsetTop, 0, bars?.bottom ?: insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll)
        autoCheckUpdate()
    }

    // =====================================================================
    // App 更新（update 套件）
    // =====================================================================

    private var updateDialog: UpdateDialog? = null

    override fun onResume() {
        super.onResume()
        updateDialog?.onResume()
    }

    /** 每天最多一次，在背景查 GitHub；有新版才跳對話框，錯誤一律安靜略過 */
    private fun autoCheckUpdate() {
        UpdateInstaller.cleanup(this)
        io.execute {
            val r = runCatching { UpdateChecker.autoCheck(this) }.getOrNull() ?: return@execute
            main.post { showUpdate(r, skippable = true) }
        }
    }

    private fun checkUpdate(status: TextView) {
        status.visibility = android.view.View.VISIBLE
        status.text = "檢查中…"
        io.execute {
            val r = UpdateChecker.check()
            main.post {
                if (isFinishing) return@post
                when (r) {
                    is CheckResult.Available -> { status.visibility = android.view.View.GONE; showUpdate(r.release, skippable = false) }
                    CheckResult.UpToDate -> status.text = "已經是最新版"
                    is CheckResult.Failed -> status.text = "無法檢查更新：${r.reason}"
                }
            }
        }
    }

    private fun showUpdate(r: ReleaseInfo, skippable: Boolean) {
        if (isFinishing || isDestroyed || updateDialog != null) return
        updateDialog = UpdateDialog(
            activity = this,
            release = r,
            onSkip = if (skippable) ({ UpdateChecker.skip(this, r.tag) }) else null,
            onClosed = { updateDialog = null },
        ).also { it.show() }
    }

    @Deprecated("Framework Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_EXPORT -> io.execute {
                val msg = try {
                    val bytes = dictionaryBytes()
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    "已匯出"
                } catch (e: Exception) { "匯出失敗：${e.message}" }
                main.post { toast(msg) }
            }
            REQ_IMPORT -> readImport(uri)
        }
    }

    // =====================================================================
    // 字典匯出／匯入（DictionaryCsv）
    // =====================================================================

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dictionaryBytes(): ByteArray {
        val store = UserPhraseStore.get(this)
        store.load()
        return DictionaryCsv.export(store.csvData())
    }

    private fun exportName(): String =
        "倬注音字典_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.TAIWAN).format(java.util.Date()) + ".csv"

    private fun exportDictionary(share: Boolean) {
        if (!share) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, exportName())
            }, REQ_EXPORT)
            return
        }
        io.execute {
            try {
                val name = exportName()
                val uri = ExportProvider.write(this, name, dictionaryBytes())
                main.post {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, name)
                        putExtra(Intent.EXTRA_TITLE, name)
                        clipData = android.content.ClipData.newRawUri(name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "分享字典到…"))
                }
            } catch (e: Exception) {
                main.post { toast("匯出失敗：${e.message}") }
            }
        }
    }

    /** 讀檔、解析、補注音（背景執行），再讓使用者選合併或取代。 */
    private fun readImport(uri: android.net.Uri) {
        toast("讀取中…")
        io.execute {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("無法讀取檔案")
                val text = DictionaryCsv.decode(bytes)
                if (DictionaryCsv.isLegacyBackup(text)) {
                    // 舊版備份檔（.tsv）：照舊合併
                    val store = UserPhraseStore.get(this)
                    store.load()
                    val n = store.importFrom(text.byteInputStream())
                    main.post { toast("已從舊版備份還原 $n 筆") }
                    return@execute
                }
                val parsed = DictionaryCsv.parse(text)
                val errors = parsed.errors.toMutableList()
                val extra = ArrayList<DictionaryCsv.Phrase>()
                if (parsed.pending.isNotEmpty()) {
                    val lex = tw.hoonsor.ime.app.LexiconHolder.get(this)
                    for (p in parsed.pending) {
                        val r = lex.readingsFor(p.text)
                        if (r == null) errors += "第 ${p.line} 行：找不到「${p.text}」的注音，請手動填寫"
                        else extra += DictionaryCsv.Phrase(p.text, r, p.contact)
                    }
                }
                val d = parsed.data.copy(phrases = parsed.data.phrases + extra)
                main.post { confirmImport(d, errors) }
            } catch (e: Exception) {
                main.post { toast("匯入失敗：${e.message}") }
            }
        }
    }

    private fun confirmImport(d: DictionaryCsv.Data, errors: List<String>) {
        val total = d.phrases.size + d.words.size + d.quick.size
        if (total == 0 && d.usage.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("沒有可匯入的內容")
                .setMessage(errors.take(15).joinToString("\n").ifEmpty { "檔案是空的，或格式不是倬注音匯出的字典。" })
                .setPositiveButton("確定", null).show()
            return
        }
        val msg = buildString {
            append("中文詞 ${d.phrases.size} 筆、英文詞 ${d.words.size} 筆、常用短語 ${d.quick.size} 筆")
            if (d.usage.isNotEmpty()) append("、學習紀錄 ${d.usage.size} 筆")
            append("。\n\n")
            append("合併：只新增或更新，手機上原有的詞都保留。\n")
            append("取代：以檔案為準，檔案裡沒有的詞會從手機刪除。\n")
            append("匯入前會自動保留目前的字典，可用「復原上次匯入前的字典」復原。")
            if (errors.isNotEmpty()) {
                append("\n\n⚠ 有 ${errors.size} 行無法匯入（會略過）：\n")
                append(errors.take(10).joinToString("\n"))
                if (errors.size > 10) append("\n…")
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("匯入字典")
            .setMessage(msg)
            .setPositiveButton("合併") { _, _ -> applyImport(d, replace = false) }
            .setNeutralButton("取代") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setMessage("確定以檔案取代整個字典？檔案裡沒有的詞會被刪除。")
                    .setPositiveButton("取代") { _, _ -> applyImport(d, replace = true) }
                    .setNegativeButton("取消", null).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyImport(d: DictionaryCsv.Data, replace: Boolean) {
        io.execute {
            val msg = try {
                val store = UserPhraseStore.get(this)
                store.load()
                store.applyCsv(d, replace)
                (if (replace) "已取代字典" else "已合併") + "，鍵盤下次開啟時生效"
            } catch (e: Exception) { "匯入失敗：${e.message}" }
            main.post { toast(msg) }
        }
    }

    override fun onDestroy() {
        UpdateInstaller.onInstallError = null // 不讓已關閉的對話框留著這個 Activity
        io.shutdown()
        super.onDestroy()
    }

    private fun openDict(mode: String) =
        startActivity(Intent(this, DictionaryActivity::class.java).putExtra(DictionaryActivity.EXTRA_MODE, mode))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_EXPORT = 1
        private const val REQ_IMPORT = 2
    }
}
