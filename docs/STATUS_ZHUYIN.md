# 注音鍵盤進度（對話 A）

## 2026-10-09（第二階段：可安裝的鍵盤 App）

### 完成
- **`app` 鍵盤主體**（約 2,300 行，只用 Android 框架、不依賴 AndroidX，保持輸入法輕量穩定）：
  - `ImeService` → `ImeController`：所有按鍵、面板、送字邏輯集中在 controller。
  - **送字規則**：一律經過 `ImeController.currentTarget`（預設 `ImeTextTarget`），為剪貼簿搜尋模式預留替換點。
  - `PanelController` + `PanelMode { KEYBOARD, VOICE, CLIPBOARD }`：面板只建立一次、切換只改 visibility、高度固定。
  - 畫面結構依 ARCHITECTURE §5.1：透明組字框區 → SearchStripSlot（GONE）→ 頂列（工具列 ⇄ 候選列）→ 面板區 → 底部 inset 墊片。
  - `KeyboardView`（自繪）：大千注音、英文（含上排數字、Shift）、符號（半／全／數／顏，圖釘鎖定）；
    第二根手指按下時先送出前一鍵；刪除鍵按住連續刪除；**標點手勢鍵**（點=，↖？↗、↘！）；**扇子鍵**（按住滑動或點一下停留）。
  - `FanOverlayView`：單圈扇形＋放射線，項目 半／全／數／顏／剪（剪貼簿預留，可在設定關閉）。
  - `ToolbarView`：⚙ 🎤 繁 ← → (📋) ☰ ⌨；📋 預設隱藏。
  - `CandidateBarView`：▼ 展開、首選反白、› 翻頁、U/C 標示、長按自建詞刪除、**特殊 chip 插槽**（剪貼簿預留）。
  - `ComposingBoxView`：浮在 App 上方的組字框，虛線分段（ㄋㄧ ┆ ㄏㄠ），透過 onComputeInsets 不推高 App、不攔截觸控。
  - `CandidateGridView`：下拉候選視窗。
  - 齒輪選單（參數設定／高度調整／常用短語）、高度調整（▲▼，220–400dp）。
  - 編輯面板 5×3（含 AI 欄，選取／未選取兩種狀態），游標、選取、複製剪下貼上、前後刪。
  - `ImeTextTarget`：選取中刪除、emoji 代理字元刪除、讀不到前文時改送 DEL、Enter 依輸入框執行送出／搜尋或換行。
  - 密碼欄位自動切英文且不學習；數字／電話欄位自動切數字鍵盤。
  - Android 15+ 底部手勢列 inset 處理。
  - `UserPhraseStore`：使用者詞與學習次數存成單一文字檔，原子寫入、1 秒合併寫檔；設定頁可備份／還原。
  - `SettingsActivity`：啟用輸入法、切換、試打區、偏好開關、詞庫備份還原。
- **驗證**：整個 app 以 kotlinc 對 **Android API 35 的 android.jar** 編譯通過（61 個類別），引擎 21 項測試通過。

### 尚未驗證（需要倬在電腦上用 Android Studio）
- Gradle 建置、實機安裝與手感。此環境無法連到 Maven／Google Maven，只能做型別檢查，無法產生 APK。
- 第一次開啟專案請依序：`./gradlew :engine:zhuyin:test` → `./gradlew :app:installDebug`。

### 和原規格不同的決定（請倬確認）
- 使用者詞庫先用**文字檔**而非 Room：沒有額外依賴、備份就是複製檔案，穩定性較好。`core:dictionary`（Room）保留，日後需要時再換，引擎不受影響。
- 長按自建詞**直接刪除**並顯示提示（輸入法視窗內不便跳確認對話框）。

### 預留但未實作（合併或後續）
- 🎤 語音、AI 欄、剪貼簿（📋／扇子「剪」／長按貼上）：目前顯示「尚未合併」提示。
- 繁簡切換、常用短語、智慧鄰近修正（Z9）、實體鍵盤（H1–H3）、按鍵聲音。

### 已知問題 / 待辦
- 系統詞頻來自書面語料，口語詞初始排名偏後；選過一次即排第一。可考慮口語加權表或匯入超注音詞庫。
- 詞庫載入在背景執行緒，桌機約 0.6 秒；手機上可能 1–3 秒，期間打注音會提示「詞庫載入中」。之後可改二進位格式加速並降低記憶體（目前加了 largeHeap 保險）。
- Z10「延長找詞」行為是推測。

## 2026-10-09（第一階段：引擎）
- 系統詞庫轉換（McBopomofo, MIT）、簡拼引擎、自建詞規則對齊超注音、組字分段、21 項測試。詳見 git log。
