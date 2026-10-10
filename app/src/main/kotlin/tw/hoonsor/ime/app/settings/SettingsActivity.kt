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
import java.io.File

/**
 * 設定頁（參數設定）：啟用輸入法、測試、偏好開關、詞庫備份／還原（D4）。
 * 只用 Android 框架元件，不依賴 AndroidX。語音／剪貼簿設定在合併後加入。
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

        title("4. 使用者詞庫")
        button("備份詞庫到檔案…") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/tab-separated-values"
                putExtra(Intent.EXTRA_TITLE, "user_dict_backup.tsv")
            }, REQ_EXPORT)
        }
        button("從檔案還原詞庫…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQ_IMPORT)
        }
        col.addView(TextView(this).apply {
            text = "還原後請把鍵盤收起再打開一次，新詞才會載入。"
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
    }

    @Deprecated("Framework Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            val file = File(filesDir, "user_dict.tsv")
            when (requestCode) {
                REQ_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        if (file.exists()) file.inputStream().use { it.copyTo(out) }
                    }
                    toast("已備份")
                }
                REQ_IMPORT -> {
                    val store = UserPhraseStore(this)
                    store.load()
                    val n = contentResolver.openInputStream(uri)?.use { store.importFrom(it) } ?: 0
                    toast("已還原 $n 筆")
                }
            }
        } catch (e: Exception) {
            toast("失敗：${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_EXPORT = 1
        private const val REQ_IMPORT = 2
    }
}
