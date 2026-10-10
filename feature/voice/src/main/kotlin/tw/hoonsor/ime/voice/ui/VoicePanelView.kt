package tw.hoonsor.ime.voice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import tw.hoonsor.ime.contracts.TextTarget
import tw.hoonsor.ime.contracts.VoiceErrorCode
import tw.hoonsor.ime.contracts.VoiceInputController
import tw.hoonsor.ime.contracts.VoiceMode
import tw.hoonsor.ime.contracts.VoiceState
import tw.hoonsor.ime.voice.ai.AiConfig

/** 語音面板需要輸入法幫忙做的事（送字、游標、回鍵盤）。由 :app 實作。 */
interface VoicePanelHost {
    fun target(): TextTarget
    fun insert(text: String)
    fun backspace()
    fun enter()
    fun cursorLeft()
    fun cursorRight()
    fun backToKeyboard()
    fun openSettings()
}

/**
 * 語音面板（V8，配置沿用超注音，倬指定）：
 *   上：模式（聽寫／修改選取／翻譯）、波形、狀態；錄音中「取消」、完成後「還原原文」
 *   中：快速標點「， ？ 、 ！ 空白 。 ← →」——說完話馬上補標點
 *   下：「↩ 回注音」「按我開始說」「⌫」「↵」
 */
@SuppressLint("ViewConstructor")
class VoicePanelView(
    context: Context,
    private val controller: VoiceInputController,
    private val host: VoicePanelHost,
    private val note: () -> String?,
    private val hasResult: () -> Boolean,
) : LinearLayout(context) {

    private val config = AiConfig(context)
    private var mode = VoiceMode.DICTATE
    private val modeViews = HashMap<VoiceMode, TextView>()
    private val wave = Wave(context)
    private val status = TextView(context)
    private val actionCancel = chip("取消")
    private val actionRevert = chip("還原原文")
    private val mainButton = TextView(context)
    private var state: VoiceState = VoiceState.Idle

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = (dp(v) + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        setPadding(dpi(6f), 0, dpi(6f), dpi(8f))

        // 模式列
        val modes = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for ((m, label) in listOf(VoiceMode.DICTATE to "聽寫", VoiceMode.EDIT_SELECTION to "修改選取", VoiceMode.TRANSLATE to "翻譯")) {
            val v = chip(label).apply { setOnClickListener { if (!busy()) { mode = m; refresh() } } }
            modeViews[m] = v
            modes.addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(34f)).apply { marginEnd = dpi(6f) })
        }
        addView(modes, LayoutParams(LayoutParams.MATCH_PARENT, dpi(46f)))

        // 波形與狀態
        val center = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        center.addView(wave, LayoutParams(LayoutParams.MATCH_PARENT, dpi(40f)))
        status.apply {
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            maxLines = 2
            setOnClickListener { if ((state as? VoiceState.Error)?.code in setOf(VoiceErrorCode.NO_MIC_PERMISSION, VoiceErrorCode.NO_API_KEY)) host.openSettings() }
        }
        center.addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        actionCancel.setOnClickListener { controller.cancel() }
        actionRevert.setOnClickListener { controller.revertLastToRaw() }
        actions.addView(actionCancel, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(32f)).apply { marginEnd = dpi(8f) })
        actions.addView(actionRevert, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(32f)))
        center.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, dpi(36f)).apply { topMargin = dpi(4f) })
        addView(center, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 快速標點列
        val punct = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (p in listOf("，", "？", "、", "！", "␣", "。")) {
            punct.addView(key(p, 22f) { host.insert(if (p == "␣") " " else p) }, keyLp())
        }
        punct.addView(key("←", 22f) { host.cursorLeft() }, keyLp())
        punct.addView(key("→", 22f) { host.cursorRight() }, keyLp())
        addView(punct, LayoutParams(LayoutParams.MATCH_PARENT, dpi(48f)).apply { bottomMargin = dpi(6f) })

        // 底列
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        bottom.addView(key("↩", 24f) { controller.cancel(); host.backToKeyboard() }.apply { contentDescription = "回注音鍵盤" }, keyLp(1.1f))
        mainButton.apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(TEXT)
            background = round(KEY)
            setOnTouchListener(MainTouch())
        }
        bottom.addView(mainButton, keyLp(3.4f))
        bottom.addView(key("⌫", 22f) { host.backspace() }.apply { contentDescription = "刪除" }, keyLp(1.1f))
        bottom.addView(key("↵", 24f) { host.enter() }.apply { contentDescription = "換行" }, keyLp(1.1f))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dpi(52f)))

        refresh()
    }

    /** 由 VoiceModule 的監聽器在主執行緒呼叫。 */
    fun onState(s: VoiceState) {
        state = s
        refresh()
    }

    /** 面板打開時呼叫（自動開始錄音的設定）。 */
    fun onShown() {
        refresh()
        if (config.autoStart && !busy()) controller.start(mode, host.target())
    }

    private fun busy() = state is VoiceState.Recording || state is VoiceState.Transcribing || state is VoiceState.Polishing

    private fun refresh() {
        for ((m, v) in modeViews) {
            val on = m == mode
            v.setTextColor(if (on) DARK_TEXT else TEXT)
            v.background = round(if (on) TEXT else KEY_DARK, 17f)
        }
        val s = state
        wave.level = (s as? VoiceState.Recording)?.level ?: 0f
        wave.active = s is VoiceState.Recording
        wave.invalidate()
        val hold = config.holdToTalk
        when (s) {
            is VoiceState.Recording -> {
                status.text = "錄音中 " + fmt(s.elapsedMs) + if (hold) " · 放開送出" else " · 再按一次送出"
                status.setTextColor(RED)
                mainButton.text = if (hold) "放開送出" else "說完了，按我送出"
                mainButton.background = round(RED)
                mainButton.setTextColor(DARK_TEXT)
            }
            VoiceState.Transcribing, VoiceState.Polishing -> {
                status.text = if (s == VoiceState.Transcribing) "轉錄中…" else if (mode == VoiceMode.TRANSLATE) "翻譯中…" else "AI 整理中…"
                status.setTextColor(ACCENT)
                mainButton.text = "處理中…"
                mainButton.background = round(KEY)
                mainButton.setTextColor(MUTED)
            }
            is VoiceState.Done -> {
                status.text = note() ?: "已送出（${"%.1f".format(s.result.totalLatencyMs / 1000f)} 秒）"
                status.setTextColor(GREEN)
                idleButton(hold)
            }
            is VoiceState.Error -> {
                status.text = s.message
                status.setTextColor(RED)
                idleButton(hold)
            }
            VoiceState.Idle -> {
                status.text = note() ?: when (mode) {
                    VoiceMode.DICTATE -> "說話 → 自動去贅字、修正口誤、補標點"
                    VoiceMode.EDIT_SELECTION -> "先選取文字，再用說的告訴我怎麼改"
                    VoiceMode.TRANSLATE -> "說中文，輸出${config.translateTo}"
                }
                status.setTextColor(MUTED)
                idleButton(hold)
            }
        }
        actionCancel.visibility = if (busy()) VISIBLE else GONE
        actionRevert.visibility = if (!busy() && hasResult()) VISIBLE else GONE
    }

    private fun idleButton(hold: Boolean) {
        mainButton.text = if (hold) "按住說話" else "按我開始說"
        mainButton.background = round(KEY)
        mainButton.setTextColor(TEXT)
    }

    private fun fmt(ms: Long): String {
        val sec = ms / 1000
        return "${sec / 60}:${"%02d".format(sec % 60)}"
    }

    /** 主按鈕：點按模式（點一下開始、再點一下送出）或按住說話模式。 */
    private inner class MainTouch : OnTouchListener {
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val hold = config.holdToTalk
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.8f
                    if (hold && !busy()) controller.start(mode, host.target())
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    if (hold) {
                        if (state is VoiceState.Recording) controller.stop()
                    } else when (state) {
                        is VoiceState.Recording -> controller.stop()
                        VoiceState.Transcribing, VoiceState.Polishing -> {}
                        else -> controller.start(mode, host.target())
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (hold && state is VoiceState.Recording) controller.stop()
                }
            }
            return true
        }
    }

    // ---------- 小元件 ----------

    private fun keyLp(weight: Float = 1f) = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
        marginStart = dpi(2.5f); marginEnd = dpi(2.5f)
    }

    private fun round(color: Int, r: Float = 9f) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r) }

    private fun key(label: String, sp: Float, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        background = round(KEY)
        setOnClickListener { onClick() }
    }

    private fun chip(label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dpi(14f), 0, dpi(14f), 0)
        background = round(KEY_DARK, 17f)
    }

    /** 簡單波形：依音量畫一排長條。 */
    private class Wave(context: Context) : View(context) {
        var level = 0f
        var active = false
        private val history = FloatArray(28)
        private var idx = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            history[idx % history.size] = if (active) level else 0f
            idx++
            val d = resources.displayMetrics.density
            val barW = 5 * d
            val gap = 4 * d
            val total = history.size * (barW + gap) - gap
            var x = (width - total) / 2
            paint.color = if (active) TEXT else 0xFF3E5560.toInt()
            for (i in history.indices) {
                val v = history[(idx + i) % history.size]
                val h = (6 * d) + v * (height - 6 * d)
                val top = (height - h) / 2
                c.drawRoundRect(x, top, x + barW, top + h, barW / 2, barW / 2, paint)
                x += barW + gap
            }
        }
    }

    companion object {
        private const val BG = 0xFF161616.toInt()
        private const val KEY = 0xFF565656.toInt()
        private const val KEY_DARK = 0xFF2A2A2A.toInt()
        private const val TEXT = 0xFFA8D8EA.toInt()
        private const val DARK_TEXT = 0xFF10232B.toInt()
        private const val MUTED = 0xFF8A8A8A.toInt()
        private const val ACCENT = 0xFFFFD27A.toInt()
        private const val RED = 0xFFFF8A8A.toInt()
        private const val GREEN = 0xFF8FDCA8.toInt()
    }
}
