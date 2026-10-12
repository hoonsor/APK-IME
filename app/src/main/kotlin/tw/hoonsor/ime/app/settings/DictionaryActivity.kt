package tw.hoonsor.ime.app.settings

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tw.hoonsor.ime.app.LexiconHolder
import tw.hoonsor.ime.app.UserPhraseStore
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.zhuyin.Syllable
import tw.hoonsor.ime.zhuyin.Zhuyin

/**
 * 檢視與編輯使用者字典（需求十）：注音自建詞、英文詞、常用短語。
 * 修改會寫入共用的 [UserPhraseStore]，鍵盤下次開啟時自動重新載入。
 */
class DictionaryActivity : Activity() {

    private lateinit var store: UserPhraseStore
    private lateinit var mode: String
    private lateinit var list: LinearLayout
    private lateinit var countView: TextView
    private var filter = ""
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = UserPhraseStore.get(this)
        store.load()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHRASES

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(16f))
        }
        col.addView(TextView(this).apply {
            text = when (mode) {
                MODE_WORDS -> "英文詞（字母＋符號）"
                MODE_QUICK -> "常用短語"
                else -> "注音自建詞"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        })
        col.addView(TextView(this).apply {
            text = when (mode) {
                MODE_WORDS -> "連續輸入英文或符號時，候選列會出現「＋」可加入；打前幾個字母時會出現在候選列。"
                MODE_QUICK -> "從鍵盤 ⚙ → 常用短語 點一下即可送出。可以有多行。"
                else -> "注音欄位每個字一個音節、以空白分隔，例如：ㄒㄧㄠˇ ㄗ ㄋㄢˊ（聲調可省略，一聲不用打）。"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dpi(4f), 0, dpi(8f))
        })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = EditText(this).apply {
            hint = "搜尋"
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { filter = s?.toString()?.trim() ?: ""; refresh() }
            })
        }
        bar.addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply { text = "＋ 新增"; setOnClickListener { edit(null) } })
        col.addView(bar)

        countView = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, dpi(6f), 0, dpi(6f)) }
        col.addView(countView)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)

        setContentView(ScrollView(this).apply {
            addView(col)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30)
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()) else null
                @Suppress("DEPRECATION")
                v.setPadding(0, bars?.top ?: insets.systemWindowInsetTop, 0, bars?.bottom ?: insets.systemWindowInsetBottom)
                insets
            }
        })
        refresh()
    }

    // ---------- 清單 ----------

    private data class Item(val title: String, val sub: String?, val key: Any)

    private fun items(): List<Item> = when (mode) {
        MODE_WORDS -> store.words().map { Item(it, null, it) }
        MODE_QUICK -> store.quickPhrases().mapIndexed { i, q -> Item(q, null, i) }
        else -> store.phrases().reversed().map { Item(it.text, it.readings.joinToString(" "), it) }
    }

    private fun refresh() {
        val all = items()
        val shown = if (filter.isEmpty()) all else all.filter { it.title.contains(filter, true) || (it.sub?.contains(filter) == true) }
        countView.text = "共 ${all.size} 筆" + if (filter.isNotEmpty()) "，符合 ${shown.size} 筆" else ""
        list.removeAllViews()
        if (shown.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (all.isEmpty()) "目前沒有資料，按「＋ 新增」開始。" else "沒有符合的項目"
                setPadding(0, dpi(16f), 0, dpi(16f))
            })
        }
        for (it in shown.take(500)) list.addView(row(it))
    }

    private fun row(item: Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(12f), dpi(8f), dpi(8f), dpi(8f))
            background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = dp(8f) }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = item.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 3
        })
        if (item.sub != null) texts.addView(TextView(this).apply {
            text = item.sub
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            alpha = 0.7f
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "編輯"; setOnClickListener { edit(item) } })
        row.addView(Button(this).apply { text = "刪除"; setOnClickListener { confirmDelete(item) } })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpi(4f), 0, dpi(4f))
            addView(row)
        }
    }

    private fun confirmDelete(item: Item) {
        AlertDialog.Builder(this)
            .setMessage("確定刪除「${item.title.take(30)}」？")
            .setPositiveButton("刪除") { _, _ ->
                when (val k = item.key) {
                    is UserPhraseStore.Phrase -> store.removePhrase(k.text, k.readings, external = true)
                    is String -> store.removeWord(k, external = true)
                    is Int -> store.removeQuickPhrase(k)
                }
                toast("已刪除")
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 新增／編輯 ----------

    private fun edit(item: Item?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(8f), dpi(20f), 0)
        }
        val textField = EditText(this).apply {
            hint = when (mode) { MODE_WORDS -> "例如 abc@gmail.com"; MODE_QUICK -> "短語內容"; else -> "詞，例如 小資男" }
            setText(item?.title ?: "")
            if (mode == MODE_QUICK) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
            } else setSingleLine()
        }
        box.addView(textField)
        var readingField: EditText? = null
        if (mode == MODE_PHRASES) {
            val rf = EditText(this).apply {
                hint = "注音，例如 ㄒㄧㄠˇ ㄗ ㄋㄢˊ"
                setText(item?.sub ?: "")
                setSingleLine()
            }
            readingField = rf
            box.addView(rf)
            box.addView(Button(this).apply {
                text = "自動判斷注音"
                setOnClickListener { autoReading(textField.text.toString().trim(), rf) }
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (item == null) "新增" else "編輯")
            .setView(box)
            .setPositiveButton("儲存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (save(item, textField.text.toString(), readingField?.text?.toString())) {
                    dialog.dismiss()
                    refresh()
                }
            }
        }
        dialog.show()
    }

    private fun autoReading(text: String, target: EditText) {
        if (text.isEmpty()) { toast("請先輸入詞"); return }
        toast("判斷中…")
        Thread {
            val r = try { LexiconHolder.get(this).readingsFor(text) } catch (e: Exception) { null }
            main.post {
                if (r == null) toast("無法自動判斷，請手動輸入注音")
                else target.setText(r.joinToString(" "))
            }
        }.start()
    }

    /** 回傳 true 表示已儲存。 */
    private fun save(old: Item?, rawText: String, rawReading: String?): Boolean {
        when (mode) {
            MODE_WORDS -> {
                val w = rawText.trim()
                if (w.isEmpty() || w.any { it.isWhitespace() }) { toast("英文詞不能空白或含空格"); return false }
                (old?.key as? String)?.let { store.removeWord(it, external = true) }
                store.addWord(w, external = true)
            }
            MODE_QUICK -> {
                if (rawText.isBlank()) { toast("內容不能空白"); return false }
                store.setQuickPhrase((old?.key as? Int) ?: -1, rawText.trimEnd())
            }
            else -> {
                val text = rawText.trim()
                val readings = parseReadings(rawReading ?: "")
                if (text.isEmpty()) { toast("詞不能空白"); return false }
                if (readings == null) { toast("注音格式不正確：只能輸入注音符號與聲調，以空白分隔"); return false }
                if (readings.size != text.length) {
                    toast("注音音節數（${readings.size}）要和字數（${text.length}）相同"); return false
                }
                (old?.key as? UserPhraseStore.Phrase)?.let { store.removePhrase(it.text, it.readings, external = true) }
                store.addPhrase(text, readings, external = true)
            }
        }
        toast("已儲存")
        return true
    }

    /** 驗證並正規化注音（與匯入 CSV 共用同一套規則）。 */
    private fun parseReadings(s: String): List<String>? = DictionaryCsv.parseReadings(s)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PHRASES = "phrases"
        const val MODE_WORDS = "words"
        const val MODE_QUICK = "quick"
    }
}
