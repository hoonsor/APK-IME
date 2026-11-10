package tw.hoonsor.ime.app

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast

/**
 * 輸入法服務：只負責把系統事件轉給 [ImeController]。
 */
class ImeService : InputMethodService() {

    private lateinit var controller: ImeController

    /**
     * 安全網：任何單一功能出錯只記錄並提示，不讓整個輸入法當掉（穩定優先）。
     * 錯誤可在 Android Studio 的 Logcat 以 tag「ImeService」查看。
     */
    private inline fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "error in $where", e)
            Toast.makeText(this, "倬注音發生錯誤（$where）：${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = ImeController(this)
        controller.loadAsync()
        // 每日自動備份的主要進入點：輸入法常駐，程序每次被系統拉起都會經過這裡（沒有 Application 類別）。
        // 放背景執行緒、吞掉所有錯誤：排程失敗不能影響打字。已排好就不會重排。
        Thread({ try { tw.hoonsor.backup.AutoBackup.ensureScheduled(applicationContext) } catch (t: Throwable) { Log.w(TAG, "auto-backup schedule failed", t) } },
            "auto-backup-schedule").start()
    }

    override fun onCreateInputView(): View = controller.createView()

    /** 不使用橫向全螢幕的擷取模式，維持與直向相同的鍵盤。 */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        safe("onStartInputView") { controller.onStartInputView(info) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        safe("onFinishInputView") { controller.onFinishInputView() }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        safe("onUpdateSelection") { controller.onSelectionChanged(newSelStart, newSelEnd) }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        safe("onComputeInsets") { controller.adjustInsets(outInsets) }
    }

    override fun onDestroy() {
        safe("onDestroy") { controller.release() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ImeService"
    }
}
