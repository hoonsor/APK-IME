package tw.hoonsor.ime.app.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import tw.hoonsor.ime.app.ui.dp
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.voice.ai.AiConfig
import tw.hoonsor.ime.voice.ai.AiException
import tw.hoonsor.ime.voice.ai.AiHttp
import tw.hoonsor.ime.voice.ai.AiProvider
import tw.hoonsor.ime.voice.ai.AiRole
import tw.hoonsor.ime.voice.audio.AudioRules
import java.util.concurrent.Executors

/**
 * AI 模型設定：API key、各用途的模型（下拉選單＋可自行輸入）、測試按鈕、語音選項。
 * 從鍵盤 ⚙ →「AI 模型設定」或設定頁進入。
 */
class AiSettingsActivity : Activity() {

    private lateinit var config: AiConfig
    private lateinit var secrets: KeystoreSecretStore
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val fetched = HashMap<AiProvider, List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AiConfig(this)
        secrets = KeystoreSecretStore(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(24f))
        }
        col.addView(text("AI 模型設定", 24f))
        col.addView(text("語音輸入與 AI 潤飾可以分別使用不同的模型。主要模型失敗時會自動改用備援。", 13f, alpha = 0.75f))

        // ---------- API key ----------
        col.addView(section("1. API Key"))
        col.addView(text("只存在這支手機（Android Keystore 加密），不會顯示完整內容。", 13f, alpha = 0.75f))
        for (p in AiProvider.entries) col.addView(keyRow(p))

        // ---------- 模型 ----------
        col.addView(section("2. 模型"))
        col.addView(text("可從下拉選單選，也可以直接輸入模型名稱（供應商常會更新或下架模型）。按「測試」確認能用。", 13f, alpha = 0.75f))
        for (role in AiRole.entries) col.addView(roleRow(role))

        // ---------- 語音選項 ----------
        col.addView(section("3. 語音輸入"))
        col.addView(micRow())
        col.addView(switchRow("用 AI 潤飾聽寫內容（去贅字、改口、標點）", config.polishEnabled) { config.polishEnabled = it })
        col.addView(spinnerRow("說話語言", listOf("中文" to "zh", "英文" to "en", "自動偵測" to "auto"), config.language) { config.language = it })
        col.addView(editRow("翻譯模式的目標語言", config.translateTo) { config.translateTo = it.ifBlank { "英文 (English)" } })
        col.addView(switchRow("按住說話（放開就送出）", config.holdToTalk) { config.holdToTalk = it })
        col.addView(switchRow("打開語音面板就自動開始錄音", config.autoStart) { config.autoStart = it })

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
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    // =====================================================================
    // API key
    // =====================================================================

    private fun keyRow(p: AiProvider): View {
        val box = card()
        val status = text("", 13f, alpha = 0.8f)
        fun refresh() { status.text = secrets.masked(p.secret)?.let { "已設定 $it" } ?: "尚未設定" }
        refresh()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(p.label, 18f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(status)
        box.addView(head)
        val input = EditText(this).apply {
            hint = "貼上 ${p.label} API key"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(input)
        val base = EditText(this).apply {
            hint = "API 網址（通常不用改）"
            setText(config.baseUrl(p))
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        if (p == AiProvider.MINIMAX) {
            box.addView(text("中國大陸帳號請改成 https://api.minimaxi.com/v1", 12f, alpha = 0.7f))
        }
        box.addView(base)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "儲存"
            setOnClickListener {
                if (input.text.isNotBlank()) {
                    secrets.setBlocking(p.secret, input.text.toString())
                    tw.hoonsor.ime.voice.ai.AiRunner.resetQuota()
                }
                config.setBaseUrl(p, base.text.toString().takeIf { it != p.defaultBase })
                input.setText("")
                refresh()
                toast("已儲存")
            }
        })
        buttons.addView(Button(this).apply {
            text = "清除"
            setOnClickListener {
                AlertDialog.Builder(this@AiSettingsActivity)
                    .setMessage("清除 ${p.label} 的 API key？")
                    .setPositiveButton("清除") { _, _ -> secrets.setBlocking(p.secret, null); refresh() }
                    .setNegativeButton("取消", null).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "測試連線"
            setOnClickListener { testConnection(p, status) }
        })
        box.addView(buttons)
        box.addView(backupKeyBlock(p))
        return wrap(box)
    }

    /** 備用 key（倬：Groq 註冊了兩個帳號）：主要 key 免費額度用完時自動改用。 */
    private fun backupKeyBlock(p: AiProvider): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(10f), 0, 0) }
        val status = text("", 13f, alpha = 0.8f)
        fun refresh() { status.text = secrets.masked(p.backupSecret)?.let { "已設定 $it" } ?: "未設定（選填）" }
        refresh()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("備用 API key", 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(status)
        col.addView(head)
        col.addView(text("主要 key 的免費額度用完時自動改用（例如第二個帳號的 key）。", 12f, alpha = 0.7f))
        val input = EditText(this).apply {
            hint = "貼上備用的 ${p.label} API key"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        col.addView(input)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "儲存"
            setOnClickListener {
                if (input.text.isNotBlank()) {
                    secrets.setBlocking(p.backupSecret, input.text.toString())
                    tw.hoonsor.ime.voice.ai.AiRunner.resetQuota()
                    toast("已儲存備用 key")
                }
                input.setText("")
                refresh()
            }
        })
        buttons.addView(Button(this).apply {
            text = "清除"
            setOnClickListener {
                AlertDialog.Builder(this@AiSettingsActivity)
                    .setMessage("清除 ${p.label} 的備用 API key？")
                    .setPositiveButton("清除") { _, _ -> secrets.setBlocking(p.backupSecret, null); refresh() }
                    .setNegativeButton("取消", null).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "測試"
            setOnClickListener { testConnection(p, status, backup = true) }
        })
        col.addView(buttons)
        return col
    }

    private fun testConnection(p: AiProvider, out: TextView, backup: Boolean = false) {
        val key = secrets.getBlocking(if (backup) p.backupSecret else p.secret)
        if (key.isNullOrBlank()) { toast("請先儲存 API key"); return }
        out.text = "測試中…"
        val base = config.baseUrl(p)
        io.execute {
            val msg = try {
                val t0 = System.currentTimeMillis()
                val models = AiHttp.listModels(base, key)
                fetched[p] = models
                "連線成功（${System.currentTimeMillis() - t0} ms，${models.size} 個模型）"
            } catch (e: AiException) {
                "失敗：${e.message}"
            }
            main.post { out.text = msg }
        }
    }

    // =====================================================================
    // 模型（每個用途一列）
    // =====================================================================

    private fun roleRow(role: AiRole): View {
        val box = card()
        box.addView(text(role.label, 17f))
        val providers: List<AiProvider?> =
            (if (role.optional) listOf<AiProvider?>(null) else emptyList()) +
                AiProvider.entries.filter { if (role.isStt) it.sttModels.isNotEmpty() else true }
        val labels = providers.map { it?.label ?: "不使用" }
        val current = config.choice(role)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(providers.indexOf(current?.provider).coerceAtLeast(0))
        box.addView(spinner)

        val modelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val model = EditText(this).apply {
            hint = "模型名稱"
            setSingleLine()
            setText(current?.model ?: "")
        }
        modelRow.addView(model, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val pick = Button(this).apply { text = "選擇 ▼" }
        modelRow.addView(pick)
        box.addView(modelRow)

        val result = text("", 13f, alpha = 0.8f)
        val test = Button(this).apply { text = "測試" }
        box.addView(test)
        box.addView(result)

        fun selected(): AiProvider? = providers.getOrNull(spinner.selectedItemPosition)
        fun save() = config.setChoice(role, selected(), model.text.toString())
        fun syncEnabled() {
            val on = selected() != null
            model.isEnabled = on; pick.isEnabled = on; test.isEnabled = on
        }
        syncEnabled()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                val p = selected()
                // 換供應商時，模型自動換成該供應商的第一個建議值
                model.setText(p?.let { (if (role.isStt) it.sttModels else it.chatModels).firstOrNull() } ?: "")
                syncEnabled()
                save()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        model.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = save()
        })
        pick.setOnClickListener {
            val p = selected() ?: return@setOnClickListener
            val known = if (role.isStt) p.sttModels else p.chatModels
            val fromApi = fetched[p].orEmpty().filter { id ->
                val isWhisper = id.contains("whisper") || id.contains("transcribe")
                if (role.isStt) isWhisper else !isWhisper && !id.contains("tts") && !id.contains("embed")
            }
            val all = (known + fromApi).distinct()
            if (all.isEmpty()) { toast("沒有建議的模型，請直接輸入名稱"); return@setOnClickListener }
            AlertDialog.Builder(this)
                .setTitle("${p.label} 模型" + if (fromApi.isEmpty()) "（按 API Key 的「測試連線」可取得完整清單）" else "")
                .setItems(all.toTypedArray()) { _, i -> model.setText(all[i]) }
                .show()
        }
        test.setOnClickListener { testRole(role, result) }
        return wrap(box)
    }

    private fun testRole(role: AiRole, out: TextView) {
        val c = config.choice(role) ?: return
        val key = secrets.getBlocking(c.provider.secret)
        if (key.isNullOrBlank()) { out.text = "尚未設定 ${c.provider.label} 的 API key"; return }
        out.text = "測試中…"
        val call = AiHttp.Call(config.baseUrl(c.provider), key, c.model)
        io.execute {
            val msg = try {
                val t0 = System.currentTimeMillis()
                if (role.isStt) {
                    // 0.5 秒的靜音：只確認模型與 key 可用
                    AiHttp.transcribe(call, AudioRules.wav(ByteArray(AudioRules.SAMPLE_RATE)), "zh", null)
                    "可以使用（${System.currentTimeMillis() - t0} ms）"
                } else {
                    val r = AiHttp.chat(call, "只回覆「OK」兩個字母，不要其他內容。", "<Draft>測試</Draft>", 30_000, maxTokens = 512)
                    val clean = r.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                    "可以使用（${System.currentTimeMillis() - t0} ms）回覆：${clean.take(20)}"
                }
            } catch (e: AiException) {
                "失敗：${e.message}"
            }
            main.post { out.text = msg }
        }
    }

    // =====================================================================
    // 語音選項
    // =====================================================================

    private fun micRow(): View {
        val box = card()
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        box.addView(text(if (granted) "麥克風：已授權 ✓" else "麥克風：尚未授權（語音輸入需要）", 16f))
        if (!granted) box.addView(Button(this).apply {
            text = "授權麥克風"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
        })
        return wrap(box)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            toast(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "已授權麥克風" else "未授權麥克風")
            recreate()
        }
    }

    private fun switchRow(label: String, value: Boolean, set: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        isChecked = value
        setPadding(0, dpi(10f), 0, dpi(10f))
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun spinnerRow(label: String, options: List<Pair<String, String>>, value: String, set: (String) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(label, 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options.map { it.first })
        sp.setSelection(options.indexOfFirst { it.second == value }.coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = set(options[position].second)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        row.addView(sp)
        return row
    }

    private fun editRow(label: String, value: String, set: (String) -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(label, 15f))
        col.addView(EditText(this).apply {
            setText(value)
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = set(s?.toString()?.trim() ?: "")
            })
        })
        return col
    }

    // ---------- 小元件 ----------

    private fun text(s: String, sp: Float, alpha: Float = 1f) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        this.alpha = alpha
    }

    private fun section(s: String) = text(s, 19f).apply { setPadding(0, dpi(20f), 0, dpi(6f)) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
        background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = dp(10f) }
    }

    private fun wrap(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpi(5f), 0, dpi(5f))
        addView(v)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_MIC = 7
    }
}
