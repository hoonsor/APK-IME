# 注音鍵盤進度（對話 A）

> 2026-10-13 起：版本、下一步、交接紀錄以 `PROJECT_STATUS.md` 為準；本檔保留每日開發細節。

## 2026-10-13（補齊交接文件）
- 新增 `AGENTS.md`（唯一規則來源）、`docs/DESIGN.md`、`docs/AGENT_API.md`、`docs/agent-manifest.json`（22 個動作，全部規劃中）；README 改為完整使用說明；FEATURES 補 N1～N18 與語音動作對照。
- `CLAUDE.md` 改為 `@AGENTS.md`；CONVENTIONS、兩份 HANDOFF、REQUESTS 標「已併入 AGENTS.md」保留；ARCHITECTURE 標部分過時。

## 2026-10-12（符號第一頁、字典匯出匯入）
- 半形符號第一頁：! @ # $ % ^ & / * ( ) - _ = + / , . ? : ; ' " / / \ | ~ ` [ ]；{ } < > 與箭頭等移到第二頁起。
- 字典匯出：`DictionaryCsv` 產生 CSV（UTF-8 BOM、CRLF；欄位 類型／內容／注音／使用次數），Excel 會吃掉開頭 0 的內容寫成 ="…"。
  分享：自寫 `ExportProvider`（cache/export、唯讀、exported=false、暫時授權），不引入 AndroidX FileProvider。
- 字典匯入：ACTION_OPEN_DOCUMENT（可選 Google Drive／Dropbox）；UTF-8 失敗改用 Big5 解碼；舊版 TSV 備份自動辨識。
  中文詞注音留空 → 用系統詞庫反查；錯誤逐行列出、略過。合併或取代（取代需再確認）；匯入前保存 `user_dict.before-import.tsv`，可「復原上次匯入」。

## 2026-10-11 上午（符號表、翻頁、備用 API key、額度提醒）
- 半形符號：ASCII 標點＋箭頭、核取方塊（☐☑☒✓✗）、圖形、數學、貨幣單位、雜項，約 8 頁。
  全形符號：全形標點＋直書括號、希臘字母、數學、特殊圖形、框線、方塊、線段（分類參考新酷音符號表，內容自行整理），約 10 頁。
- 符號鍵盤最右欄：▲ 上一頁／▼ 下一頁（最後一頁再按回第一頁）／頁碼／📌 鎖定；換符號種類回第一頁。
- `SecretProvider` 新增 GROQ_2、MINIMAX_2、OPENAI_2（備用 key）；`VoiceErrorCode` 新增 QUOTA（HTTP 429／402）。
- `AiRunner`：主要 key 額度用完 → 備用 key → 備援模型（同樣兩把 key）；用完的 key×模型記 10 分鐘直接跳過。
  全部因額度失敗 → `VoiceModule.setQuotaListener` 通知，鍵盤跳出「AI 免費額度已達上限」視窗（同一則 30 分鐘內只跳一次）。
  語音潤飾、文字 AI 額度用完時輸出原文；語音轉文字用完則無法語音輸入。
- AI 模型設定頁每家供應商多一組「備用 API key」（儲存／清除／測試）。

## 2026-10-11（實機回饋：英文詞加入字典、候選字大小）
- 英文鍵盤 → 扇子「半」打一個符號後，回到**英文鍵盤**（原本會跳回注音，導致「123456@hotmail.com ＋」中斷）。
  符號／數字鍵盤的返回鍵依來源顯示「ABC」或「ㄅㄆㄇ」；從注音鍵盤進入的符號累積 2 個以上也會顯示「＋」。
- 候選列字體 22→28sp（左右留白 12→6dp，一排詞數大致不變）；展開的候選格 22→27dp。
- 編輯面板：換輸入框時以 EditorInfo 的游標位置初始化，並以實際選取文字判斷「有無選取」（修正空白輸入框卻顯示「停選／潤飾／翻英」）。
- AI 全文：改用游標前後文字（更多 App 支援），等待期間文字被改過就不覆蓋。
- 提示訊息改顯示在候選列 3 秒（ColorOS 常不顯示輸入法 Toast，造成「按了沒反應」）。
- 候選列上下滑 → 收起聯想詞／英文詞／提示，回到工具列。
- 英文候選（如「abc@gmail.com ＋」）改用 19sp。
- 成對符號自動補後符號、游標置中；打後符號時若後面正是同一符號則跳過。半形 < > " ' 不配對。
- 語音面板：取消／還原原文移到模式列右側（原位置被按鍵列遮住）。
- Groq 回 403「model is blocked at the project level」：Groq 後台 project 的模型權限設定，非 App 問題。

## 2026-10-10 晚（語音輸入、AI 潤飾、AI 模型設定）

倬指示：依 `docs/MOBILE_VOICE_HANDOFF.md`（桌面版嘴炮輸入法實測結果）在本對話一併實作語音模組與 AI 功能。

### `:feature:voice`（只依賴 contracts＋Android 框架，無第三方函式庫）
- `ai/AiConfig`：供應商（Groq／MiniMax／OpenAI，皆 OpenAI 相容）× 用途（語音轉文字、語音潤飾、文字 AI，各有備援）；模型 ID 可編輯。
  預設依實測：STT Groq whisper-large-v3（備援 turbo）、潤飾 Groq qwen/qwen3.8-27b（備援 gpt-oss-120b）、文字 AI MiniMax-M3（備援 Groq qwen）。
- `ai/AiHttp`：HttpURLConnection 的 chat/completions、audio/transcriptions（multipart WAV）、GET /models；錯誤訊息不含 key。
- `ai/AiRunner`：主要失敗自動改用備援（V10）。
- `ai/Prompts`：§5 STT prompt、§7.2 核心規則（原文）、英文版、§7.3 App 語氣／輸出語言／個人詞典、§7.4 說話修改、AI 寫作。
- `ai/Sanitizer`（§8.1）、`text/TextRules`（§8.2）、`text/Hallucination`（§6.1 整句比對＋重複偵測）、`text/S2tConverter`（§6.2 s2tw，自帶 OpenCC 字表）。
- `audio/Recorder`：AudioRecord VOICE_RECOGNITION 16 kHz，50 ms 框 RMS，§4 靜音判斷、頭尾裁切、WAV。
- `DefaultVoiceInputController`：§3 狀態機；session 防重複送字／取消後不送字；潤飾失敗送原文並提示；還原原文。
- `DefaultTextRewriter`（REQUESTS R1）：編輯面板 AI 欄。
- `ui/VoicePanelView`：模式（聽寫／修改選取／翻譯）、波形、狀態、取消／還原原文、快速標點列、「按我開始說」；支援按住說話。
- 測試 `VoiceTextTest` 13 項通過（§4、§5、§6.1、§6.2、§8.1、§8.2 的預期輸出）。

### `:app`
- ⚙ 選單新增「AI模型設定」→ `AiSettingsActivity`：三家 API key（Keystore 加密）、各用途供應商下拉選單＋模型（建議清單／API 取得的清單／自行輸入）、測試連線、測試模型、麥克風授權、語音選項。
- 🎤 開啟語音面板；鍵盤收起時一定停止錄音。
- 編輯面板 AI 欄可用：潤飾／潤飾全文、AI 寫作、翻英／翻英全文；完成後候選列「↶ 還原原文」。
- `AppVoiceTerms`：自建詞（2 字以上）＋英文詞 → 語音個人詞典。

### 契約變更（REQUESTS R1 已實作）
- `SecretProvider` 新增 MINIMAX；新增 `TextRewriter`（阻塞式，背景執行緒呼叫）。
- `VoiceModule.createPanelView` 多 host 參數；新增 `createRewriter`、`addStateListener`、`onPanelShown`；移除 Compose 的 SettingsScreen。

### 尚未驗證
- 全部對 Android API 35 編譯通過、純邏輯測試通過；**錄音、真實 API 呼叫都尚未實機驗證**。
- 靜音門檻 `SILENCE_RMS = 0.008` 需用倬的手機校正（Logcat tag `VoiceController` 會印 peakRms／voicedFrames，不含文字）。
- MiniMax 官方 API 是否能關閉推理（思考）未確認；輸出中的 <think> 會被清洗，但延遲可能較長。

## 2026-10-10（實機測試回饋 14 項）

倬在 OPPO Reno 14F 實機安裝成功後回報 14 項，全部處理：
1. 編輯面板：全選→複製→貼上後回到「未選取」狀態（全選不再進入「開選」模式；複製／剪下／貼上／刪除會結束開選）。
2. 大寫鍵：點一下＝下一字大寫；快速點兩下＝大寫鎖定（鍵下方黃色底線）；再點一下解除。
3. 扇子：改為依「方向」選擇，手指離圓心很近也能選。
4. 數字鍵盤：改為手機撥號盤樣式，連續輸入不跳回注音。
5. 扇子移除「顏」，現為 半／全／數／剪。
6. 長按自建詞需 0.8 秒，候選列出現「刪除「詞」／取消」確認；內建詞會提示無法刪除。
7. 英文／半形符號連續輸入時，候選列顯示「abc@gmail.com ＋」可加入字典；之後打前幾個字母會出現在候選列。
8. 工具列「繁／簡」切換簡體輸出（OpenCC 轉換表，Apache-2.0）。
9. 編輯面板（有選取時）中間右側改為「納入字典」：中文自動反查注音（含多音字詞內讀音）、英文加入英文詞。
10. 設定頁新增字典檢視／編輯（注音自建詞、英文詞、常用短語），可搜尋、新增、編輯、刪除，可自動判斷注音。
11. 常用短語：⚙ → 常用短語 開啟清單，點一下送出；「管理」進設定頁編輯。
12. 工具列圖示放大約 30%（按鈕數不變）。
13. 剪貼簿：完整實作（見下）。
14. 預設鍵盤高度 290 → 230dp，最低可調到 180dp。

### 剪貼簿（需求十三，依現有架構實作在 app 內）
- `ClipboardHistory`：輸入法使用中時擷取剪貼簿（含開啟鍵盤時、剪貼簿變動時），只記文字、只存本機；
  Android 13+ 標記為敏感的內容（密碼管理器）不記錄；最多 100 筆，釘選不會被清除。
- 面板（PanelMode.CLIPBOARD）：↩ 鍵盤／🔍 搜尋／清除（兩段確認）；每筆可 📌 釘選、✕ 刪除，點一下貼上並回到鍵盤。
- 入口：扇子「剪」、編輯面板長按「貼上」、工具列 📋（設定中開啟）。
- 剛複製的內容 2 分鐘內在候選列顯示「📋 …」一點即貼。
- 搜尋模式：currentTarget 換成 `SearchTarget`，用自己的注音鍵盤打關鍵字，上方 SearchStripSlot 顯示結果，點選即貼到原輸入框；Enter 貼第一筆。
- 原 R2 規格的「對話 C 另做 feature:clipboard」改為由 app 直接實作（倬指示以現有架構為主）。

### 其他
- `UserPhraseStore`、`ClipboardHistory` 改為全 App 單一實例，鍵盤與設定頁共用；設定頁修改後鍵盤下次開啟自動重新載入。
- 引擎新增 `Lexicon.readingsFor()`（反查注音）、`clearUserPhrases()`；測試 22 項通過。
- 全部對 Android API 35 編譯通過；**尚未實機驗證**。

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
