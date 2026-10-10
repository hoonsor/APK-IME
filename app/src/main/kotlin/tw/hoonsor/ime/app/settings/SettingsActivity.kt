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

/**
 * 設定頁（參數設定）：啟用輸入法、測試、偏好開關、詞庫備份／還原（D4）。
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
        button("備份字典到檔案…") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/tab-separated-values"
                putExtra(Intent.EXTRA_TITLE, "user_dict_backup.tsv")
            }, REQ_EXPORT)
        }
        button("從檔案還原字典…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQ_IMPORT)
        }
        col.addView(TextView(this).apply {
            text = "在這裡修改字典後，把鍵盤收起再打開一次就會生效。"
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
    }

    @Deprecated("Framework Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            val store = UserPhraseStore.get(this)
            store.load()
            when (requestCode) {
                REQ_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use { store.exportTo(it) }
                    toast("已備份")
                }
                REQ_IMPORT -> {
                    val n = contentResolver.openInputStream(uri)?.use { store.importFrom(it) } ?: 0
                    toast("已還原 $n 筆")
                }
            }
        } catch (e: Exception) {
            toast("失敗：${e.message}")
        }
    }

    private fun openDict(mode: String) =
        startActivity(Intent(this, DictionaryActivity::class.java).putExtra(DictionaryActivity.EXTRA_MODE, mode))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_EXPORT = 1
        private const val REQ_IMPORT = 2
    }
}
