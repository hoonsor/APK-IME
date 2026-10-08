package tw.hoonsor.ime.app

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * 輸入法服務：只負責把系統事件轉給 [ImeController]。
 */
class ImeService : InputMethodService() {

    private lateinit var controller: ImeController

    override fun onCreate() {
        super.onCreate()
        controller = ImeController(this)
        controller.loadAsync()
    }

    override fun onCreateInputView(): View = controller.createView()

    /** 不使用橫向全螢幕的擷取模式，維持與直向相同的鍵盤。 */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.onStartInputView(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.onFinishInputView()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        controller.adjustInsets(outInsets)
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
