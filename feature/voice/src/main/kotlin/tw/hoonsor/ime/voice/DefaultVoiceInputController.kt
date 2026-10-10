package tw.hoonsor.ime.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tw.hoonsor.ime.contracts.FieldType
import tw.hoonsor.ime.contracts.TextTarget
import tw.hoonsor.ime.contracts.VoiceErrorCode
import tw.hoonsor.ime.contracts.VoiceInputController
import tw.hoonsor.ime.contracts.VoiceMode
import tw.hoonsor.ime.contracts.VoiceResult
import tw.hoonsor.ime.contracts.VoiceState
import tw.hoonsor.ime.voice.ai.AiException
import tw.hoonsor.ime.voice.ai.AiRole
import tw.hoonsor.ime.voice.ai.AiRunner
import tw.hoonsor.ime.voice.ai.Prompts
import tw.hoonsor.ime.voice.ai.Sanitizer
import tw.hoonsor.ime.voice.audio.AudioRules
import tw.hoonsor.ime.voice.audio.Recorder
import tw.hoonsor.ime.voice.text.Hallucination
import tw.hoonsor.ime.voice.text.S2tConverter
import tw.hoonsor.ime.voice.text.TextRules
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 語音輸入狀態機（MOBILE_VOICE_HANDOFF §3）。
 *
 * - 所有公開方法在主執行緒呼叫；網路與錄音停止在背景執行緒。
 * - 每次 start 產生新的 session；送字前檢查 session 仍有效，確保取消後絕不送字、也不會送兩次（§9.3）。
 * - 潤飾失敗時照樣送出 STT 原文並提示（§2.2、§9.6）。
 * - 所有例外都轉成 [VoiceState.Error]，不會拋到輸入法。
 */
internal class DefaultVoiceInputController(
    context: Context,
    private val runner: AiRunner,
    private val s2t: () -> S2tConverter?,
) : VoiceInputController {

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state

    val listeners = CopyOnWriteArrayList<(VoiceState) -> Unit>()

    /** 最後一次的提示（例如「潤飾失敗，已輸出原文」），面板顯示用。 */
    @Volatile
    var note: String? = null
        private set

    private var session = 0
    private var recorder: Recorder? = null
    private var mode = VoiceMode.DICTATE
    private var target: TextTarget? = null
    private var selection: String? = null
    private var startedAt = 0L

    var lastResult: VoiceResult? = null
        private set
    private var lastTarget: TextTarget? = null
    private var lastSelection: String? = null

    private fun set(s: VoiceState) {
        _state.value = s
        for (l in listeners) try { l(s) } catch (e: Exception) { Log.w(TAG, "listener", e) }
    }

    private val busy get() = state.value.let { it is VoiceState.Recording || it is VoiceState.Transcribing || it is VoiceState.Polishing }

    override fun start(mode: VoiceMode, target: TextTarget) {
        if (busy) return
        note = null
        if (target.hints.isPassword) return set(VoiceState.Error(VoiceErrorCode.PASSWORD_FIELD, "密碼欄位不使用語音輸入"))
        val sel = if (mode == VoiceMode.EDIT_SELECTION) target.selectedText()?.toString() else null
        if (mode == VoiceMode.EDIT_SELECTION && sel.isNullOrBlank()) {
            return set(VoiceState.Error(VoiceErrorCode.NO_SELECTION, "請先選取要修改的文字"))
        }
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return set(VoiceState.Error(VoiceErrorCode.NO_MIC_PERMISSION, "尚未授權麥克風，點這裡到設定頁授權"))
        }
        val s = ++session
        this.mode = mode
        this.target = target
        this.selection = sel
        val rec = Recorder { elapsed, level ->
            main.post { if (session == s && state.value is VoiceState.Recording) set(VoiceState.Recording(elapsed, level)) }
        }
        try {
            rec.start()
        } catch (e: Exception) {
            return set(VoiceState.Error(VoiceErrorCode.UNKNOWN, e.message ?: "麥克風無法啟動"))
        }
        recorder = rec
        startedAt = System.currentTimeMillis()
        set(VoiceState.Recording(0, 0f))
    }

    override fun stop() {
        if (state.value !is VoiceState.Recording) return
        val rec = recorder ?: return
        recorder = null
        val s = session
        set(VoiceState.Transcribing)
        worker.execute { process(s, rec) }
    }

    override fun cancel() {
        session++
        recorder?.cancel()
        recorder = null
        if (state.value !is VoiceState.Idle) set(VoiceState.Idle)
    }

    override fun revertLastToRaw() {
        val r = lastResult ?: return
        val t = lastTarget ?: return
        try {
            val before = t.textBeforeCursor(r.finalText.length)?.toString()
            if (before != r.finalText) {
                note = "游標前的文字已改變，無法還原"
                set(state.value)
                return
            }
            t.deleteSurroundingText(r.finalText.length, 0)
            t.commitText(if (r.mode == VoiceMode.EDIT_SELECTION) lastSelection ?: "" else r.rawTranscript)
            lastResult = null
            note = "已還原為原文"
            set(VoiceState.Idle)
        } catch (e: Exception) {
            Log.w(TAG, "revert", e)
        }
    }

    override fun release() {
        cancel()
        worker.shutdown()
    }

    // ---------------------------------------------------------------------

    private fun fail(s: Int, code: VoiceErrorCode, msg: String) {
        main.post { if (session == s) set(VoiceState.Error(code, msg)) }
    }

    private fun process(s: Int, rec: Recorder) {
        try {
            val r = rec.stop()
            if (session != s) return
            val voiced = r.frames.count { it >= AudioRules.SILENCE_RMS }
            Log.d(TAG, "peakRms=${"%.4f".format(r.peakRms)} voicedFrames=$voiced frames=${r.frames.size}")
            if (r.peakRms == 0f && r.frames.size > 10) return fail(s, VoiceErrorCode.EMPTY_AUDIO, "沒有收到聲音（麥克風可能被耳機或其他 App 佔用）")
            val range = AudioRules.voicedRange(r.frames) ?: return fail(s, VoiceErrorCode.EMPTY_AUDIO, "沒有聽到說話")

            val frameBytes = AudioRules.FRAME_SAMPLES * 2
            val from = (range.first * frameBytes).coerceAtMost(r.pcm.size)
            val to = ((range.last + 1) * frameBytes).coerceAtMost(r.pcm.size)
            val wav = AudioRules.wav(r.pcm.copyOfRange(from, to))

            val cfg = runner.config
            val lang = cfg.language
            val english = lang == "en"
            val terms = runner.voiceTerms(60)
            val (stt, _) = runner.transcribe(wav, if (lang == "zh" || lang == "en") lang else null, Prompts.sttPrompt(terms, english))
            if (session != s) return

            var raw = stt.trim()
            if (raw.isEmpty() || Hallucination.isHallucination(raw)) return fail(s, VoiceErrorCode.EMPTY_AUDIO, "沒有辨識到內容")
            if (!english) raw = s2t()?.convert(raw) ?: raw
            main.post { if (session == s) set(VoiceState.Polishing) }

            val hints = target?.hints
            var final: String? = when (mode) {
                VoiceMode.EDIT_SELECTION -> {
                    val sel = selection ?: ""
                    val system = Prompts.join(Prompts.editSystem(sel), Prompts.dictionary(terms))
                    val (out, _) = runner.chat(AiRole.POLISH, AiRole.POLISH_BACKUP, system, Prompts.draft(raw), POLISH_TIMEOUT)
                    Sanitizer.clean(out, sel, guardAnswer = false)
                        ?: throw AiException(VoiceErrorCode.PROVIDER_ERROR, "AI 沒有回傳內容")
                }
                VoiceMode.DICTATE, VoiceMode.TRANSLATE -> polish(raw, terms, english, hints)
            }
            if (session != s) return
            if (final.isNullOrEmpty()) {
                main.post { if (session == s) { note = "沒有可輸出的內容"; set(VoiceState.Idle) } }
                return
            }
            final = TextRules.finalizeText(final)
            val result = VoiceResult(mode, raw, final, System.currentTimeMillis() - startedAt)
            main.post { commit(s, result) }
        } catch (e: AiException) {
            fail(s, e.code, e.message ?: "AI 錯誤")
        } catch (e: Exception) {
            Log.e(TAG, "process", e)
            fail(s, VoiceErrorCode.UNKNOWN, e.message ?: e.javaClass.simpleName)
        }
    }

    /** 聽寫／翻譯：LLM 潤飾；失敗改用原文（翻譯模式失敗則報錯，因為原文不是使用者要的語言）。 */
    private fun polish(raw: String, terms: List<String>, english: Boolean, hints: tw.hoonsor.ime.contracts.EditorHints?): String? {
        val cfg = runner.config
        val translate = mode == VoiceMode.TRANSLATE
        if (!translate) {
            if (!cfg.polishEnabled || raw.length <= 3 || hints?.fieldType == FieldType.SEARCH) return raw
        }
        val system = Prompts.join(
            if (english && !translate) Prompts.POLISH_EN else Prompts.POLISH_ZH,
            hints?.let { Prompts.appTone(it) },
            if (english && !translate) null else Prompts.outputLanguage(if (translate) cfg.translateTo else null),
            Prompts.dictionary(terms),
        )
        return try {
            val (out, _) = runner.chat(AiRole.POLISH, AiRole.POLISH_BACKUP, system, Prompts.draft(raw), POLISH_TIMEOUT)
            Sanitizer.clean(out, raw, guardAnswer = !translate) ?: raw
        } catch (e: AiException) {
            if (translate) throw e
            note = "潤飾失敗，已輸出原文（${e.message}）"
            raw
        }
    }

    private fun commit(s: Int, r: VoiceResult) {
        if (session != s || state.value !is VoiceState.Polishing) return
        val t = target ?: return
        try {
            if (r.mode == VoiceMode.EDIT_SELECTION) t.replaceSelection(r.finalText) else t.commitText(r.finalText)
        } catch (e: Exception) {
            return set(VoiceState.Error(VoiceErrorCode.UNKNOWN, "無法送出文字"))
        }
        lastResult = r
        lastTarget = t
        lastSelection = selection
        set(VoiceState.Done(r))
        main.postDelayed({ if (session == s && state.value is VoiceState.Done) set(VoiceState.Idle) }, 4_000)
    }

    companion object {
        private const val TAG = "VoiceController"
        private const val POLISH_TIMEOUT = 15_000
    }
}
