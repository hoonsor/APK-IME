package tw.hoonsor.ime.app.settings

import android.content.Context

/** 輸入法設定（SharedPreferences）。IME 與設定頁在同一個 process，共用同一份。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ime", Context.MODE_PRIVATE)

    /** 面板區高度（K15 高度調整）。 */
    var panelHeightDp: Int
        get() = sp.getInt("panelHeightDp", DEFAULT_HEIGHT_DP)
        set(v) = sp.edit().putInt("panelHeightDp", v).apply()

    /** 按鍵振動（倬目前設定：不振動）。 */
    var haptics: Boolean
        get() = sp.getBoolean("haptics", false)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** K5 英文鍵盤上排固定顯示數字（倬目前設定：開）。 */
    var numberRow: Boolean
        get() = sp.getBoolean("numberRow", true)
        set(v) = sp.edit().putBoolean("numberRow", v).apply()

    /** Z8 顯示聯想字詞（倬目前設定：開）。 */
    var associations: Boolean
        get() = sp.getBoolean("associations", true)
        set(v) = sp.edit().putBoolean("associations", v).apply()

    /** 工具列顯示 📋（剪貼簿入口待倬決定，預設隱藏）。 */
    var toolbarClipboard: Boolean
        get() = sp.getBoolean("toolbarClipboard", false)
        set(v) = sp.edit().putBoolean("toolbarClipboard", v).apply()

    /** 扇子顯示「剪」項目。 */
    var fanClipboard: Boolean
        get() = sp.getBoolean("fanClipboard", true)
        set(v) = sp.edit().putBoolean("fanClipboard", v).apply()

    /** 需求八：簡體輸出。 */
    var simplified: Boolean
        get() = sp.getBoolean("simplified", false)
        set(v) = sp.edit().putBoolean("simplified", v).apply()

    /** 需求十三：記錄剪貼簿歷史。 */
    var clipCapture: Boolean
        get() = sp.getBoolean("clipCapture", true)
        set(v) = sp.edit().putBoolean("clipCapture", v).apply()

    /** 剪貼簿歷史保留筆數（釘選的不算）。 */
    var clipMax: Int
        get() = sp.getInt("clipMax", 100)
        set(v) = sp.edit().putInt("clipMax", v).apply()

    /** 剛複製的內容在候選列顯示「一點即貼」。 */
    var clipChip: Boolean
        get() = sp.getBoolean("clipChip", true)
        set(v) = sp.edit().putBoolean("clipChip", v).apply()

    companion object {
        // 倬實測：舊預設 290 按「較低」6 次（-60）最合適
        const val DEFAULT_HEIGHT_DP = 230
        const val MIN_HEIGHT_DP = 180
        const val MAX_HEIGHT_DP = 400
    }
}
