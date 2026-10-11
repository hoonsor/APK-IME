package tw.hoonsor.ime.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import tw.hoonsor.ime.contracts.SecretStore
import tw.hoonsor.ime.contracts.TextRewriter
import tw.hoonsor.ime.contracts.VoiceInputController
import tw.hoonsor.ime.contracts.VoiceState
import tw.hoonsor.ime.contracts.VoiceTermProvider
import tw.hoonsor.ime.voice.ai.AiRole
import tw.hoonsor.ime.voice.ai.AiRunner
import tw.hoonsor.ime.voice.text.S2tConverter
import tw.hoonsor.ime.voice.ui.VoicePanelHost
import tw.hoonsor.ime.voice.ui.VoicePanelView
import java.util.concurrent.Executors

/**
 * 語音模組對外入口。:app 只呼叫這裡的函式。
 *
 * 變更紀錄（倬指示由對話 A 一併實作語音，2026-10-10）：
 * - createPanelView 多了 host 參數（標點、游標、回鍵盤需要輸入法配合）。
 * - 新增 createRewriter（REQUESTS R1）、addStateListener（不需要 coroutines 也能觀察狀態）。
 * - 移除 Compose 的 SettingsScreen；設定頁改由 :app 的「AI 模型設定」用框架元件實作，讀寫 [tw.hoonsor.ime.voice.ai.AiConfig]。
 */
object VoiceModule {

    @Volatile private var s2t: S2tConverter? = null
    private val loader = Executors.newSingleThreadExecutor()

    /** 簡轉繁字表約 5 萬筆，在背景預先載入；載入完成前的語音結果暫不轉換。 */
    private fun warmS2t(context: Context) {
        if (s2t != null) return
        val app = context.applicationContext
        loader.execute {
            if (s2t != null) return@execute
            try {
                val a = app.assets
                s2t = S2tConverter(
                    a.open("opencc_st_phrases.txt").bufferedReader(),
                    a.open("opencc_st_chars.txt").bufferedReader(),
                    a.open("opencc_tw_variants.txt").bufferedReader(),
                )
            } catch (e: Exception) {
                Log.e("VoiceModule", "s2t load failed", e)
            }
        }
    }

    /** 建立控制器。輸入法生命週期內建立一次，onDestroy 時呼叫 controller.release()。 */
    fun createController(context: Context, terms: VoiceTermProvider, secrets: SecretStore): VoiceInputController {
        warmS2t(context)
        return DefaultVoiceInputController(context, AiRunner(context, secrets, terms)) { s2t }
    }

    /** 編輯面板 AI 欄用的改寫器（使用「文字 AI」模型）。 */
    fun createRewriter(context: Context, secrets: SecretStore, terms: VoiceTermProvider?): TextRewriter =
        DefaultTextRewriter(AiRunner(context, secrets, terms))

    /** 語音面板（深色），寬度 match_parent、高度 = 鍵盤高度。 */
    fun createPanelView(context: Context, controller: VoiceInputController, host: VoicePanelHost): View {
        val c = controller as? DefaultVoiceInputController
        val view = VoicePanelView(context, controller, host,
            note = { c?.note },
            hasResult = { c?.lastResult != null })
        addStateListener(controller) { view.onState(it) }
        return view
    }

    /** 面板顯示時呼叫（處理「打開就自動錄音」設定）。 */
    fun onPanelShown(panel: View) {
        (panel as? VoicePanelView)?.onShown()
    }

    /**
     * AI 免費額度用完（主要與備用 key、主要與備援模型都失敗）時，在主執行緒收到提醒文字。
     * 鍵盤用這個跳出提醒視窗。
     */
    fun setQuotaListener(listener: ((String) -> Unit)?) {
        AiRunner.quotaListener = listener?.let { l ->
            { role: AiRole ->
                l(when (role) {
                    AiRole.STT, AiRole.STT_BACKUP ->
                        "語音轉文字的免費額度已經達到上限（主要與備用 API key 都用完了），暫時無法使用語音輸入。"
                    AiRole.POLISH, AiRole.POLISH_BACKUP ->
                        "語音潤飾的免費額度已經達到上限，後續會使用原文輸出（直接送出語音辨識的文字，不潤飾）。"
                    AiRole.REWRITE, AiRole.REWRITE_BACKUP ->
                        "文字 AI 的免費額度已經達到上限，後續會使用原文輸出（文字維持原樣，不改寫）。"
                })
            }
        }
    }

    /** 在主執行緒收到狀態變化。 */
    fun addStateListener(controller: VoiceInputController, listener: (VoiceState) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        (controller as? DefaultVoiceInputController)?.listeners?.add { s ->
            if (Looper.myLooper() == Looper.getMainLooper()) listener(s) else main.post { listener(s) }
        }
    }
}
