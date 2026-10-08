package tw.hoonsor.ime.voice

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import tw.hoonsor.ime.contracts.SecretStore
import tw.hoonsor.ime.contracts.VoiceInputController
import tw.hoonsor.ime.contracts.VoiceTermProvider

/**
 * 語音模組對外唯一入口。:app 只呼叫這裡的三個函式。
 *
 * ⚠️ 這三個函式的「簽章」是對話 A、B 的合併約定，不可更改；
 * 內部實作由對話 B 自由發揮。若真的需要改簽章，依 docs/CONVENTIONS.md 的契約變更流程。
 */
object VoiceModule {

    /** 建立控制器。IME 生命週期內建立一次，onDestroy 時呼叫 controller.release()。 */
    fun createController(
        context: Context,
        terms: VoiceTermProvider,
        secrets: SecretStore,
    ): VoiceInputController {
        TODO("對話 B 實作")
    }

    /**
     * 語音面板：波形、計時、狀態文字、取消鍵、完成鍵、「還原原文」鍵。
     * app 會把它放在鍵盤容器中，寬度 match_parent，高度 = 鍵盤高度。
     * 必須支援深色主題（倬使用深色鍵盤）。
     */
    fun createPanelView(context: Context, controller: VoiceInputController): View {
        TODO("對話 B 實作")
    }

    /**
     * 語音設定頁（模型選擇、API key、錄音方式、潤飾風格、依 App 語氣、翻譯目標語言）。
     * app 的設定 Activity 會在「語音輸入設定」項目中顯示它。
     */
    @Composable
    fun SettingsScreen(secrets: SecretStore, onBack: () -> Unit) {
        TODO("對話 B 實作")
    }
}
