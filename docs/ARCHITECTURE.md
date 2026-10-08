# 架構（ARCHITECTURE）

個人自用 Android 輸入法：超注音式簡拼注音 + Typeless 等級 AI 語音輸入。
側載 APK 使用，不上架。

## 1. 技術選型

| 項目 | 選擇 | 理由 |
|------|------|------|
| 語言 | Kotlin | Android 標準 |
| minSdk / targetSdk | 29 / 36 | 個人使用不需支援舊機；Android 10+ API 較乾淨 |
| 鍵盤 UI | **自繪 Custom View（Canvas）** | IME 對延遲極敏感；Compose 在 IME 視窗中可行但較易出現首次顯示延遲與生命週期問題，穩定優先 |
| 設定畫面 | Jetpack Compose（一般 Activity） | 設定頁沒有延遲壓力，開發快 |
| 資料庫 | Room（SQLite） | 使用者詞庫、常用短語、語音歷史 |
| 設定值 | DataStore Preferences | |
| API Key 儲存 | Android Keystore 加密後存 DataStore | 不寫死在 APK |
| 網路 | OkHttp | STT / LLM 呼叫 |
| 非同步 | Kotlin Coroutines + Flow | |
| 系統詞庫 | 小麥注音（McBopomofo, MIT）詞庫為基礎，建置時轉成二進位索引放 assets | 授權乾淨；不使用超注音詞庫或程式碼 |

## 2. 模組結構與負責人

```
zhuyin-voice-ime/
├── core/
│   ├── contracts/     ← 共用介面與資料模型（純 Kotlin）【凍結，改動需走變更流程】
│   └── dictionary/    ← 使用者詞庫 Room 實作（Android library）【A 實作，B 只讀】
├── engine/
│   └── zhuyin/        ← 簡拼引擎（純 Kotlin，可在 JVM 單元測試）【A】
├── feature/
│   └── voice/         ← 語音模組：錄音、STT、LLM 潤飾、語音面板 View【B】
├── voice-sandbox/     ← 語音模組獨立測試 App（有一個文字框可測）【B】
├── app/               ← IME Service、鍵盤 View、扇子、設定頁、組裝各模組【A】
└── docs/
```

依賴方向（只能往下依賴，禁止反向）：

```
app ──► engine:zhuyin ──► core:contracts
 │  └─► core:dictionary ──► core:contracts
 └────► feature:voice ──► core:contracts
voice-sandbox ──► feature:voice
```

**關鍵規則：`feature:voice` 不依賴 `core:dictionary` 實作、也不依賴 `app`。**
它只透過 `core:contracts` 裡的介面拿詞彙、送出文字。這讓兩個對話可以完全平行開發。

## 3. 核心介面（`core/contracts`）

| 介面 | 誰實作 | 誰使用 | 用途 |
|------|--------|--------|------|
| `TextTarget` | app（包 `InputConnection`）；sandbox（包 EditText） | voice、app | 讀取選取文字/游標前後文、送出或取代文字 |
| `VoiceInputController` | feature:voice | app | 開始/停止/取消語音、狀態 Flow |
| `VoiceModule`（位於 feature:voice，非介面）| feature:voice | app | 建立 controller、語音面板 View、語音設定頁；簽章已在 stub 中固定 |
| `VoiceTermProvider` | core:dictionary；sandbox 用假資料 | voice | 提供「給語音用」的個人詞彙 |
| `SecretStore` | app；sandbox 各自實作 | voice | 讀取 API key |
| `UserDictionary` | core:dictionary | app、engine | 使用者詞的增刪查 |

完整程式碼見 `core/contracts/src/main/kotlin/`。

## 4. 主要流程

### 4.1 注音打字

```
按鍵 → KeyboardView → ImeService → ZhuyinEngine.onKey()
     → EngineState（組字區、候選詞）→ CandidateBar 顯示
     → 選字 → ZhuyinEngine.select() → TextTarget.commitText() → UserDictionary 學習頻率
```

### 4.2 語音輸入（整合後）

```
工具列 🎤 → app 呼叫 VoiceInputController.start(mode, textTarget)
          → app 把 VoicePanelView 換到鍵盤區域
          → [錄音] → STT（帶 VoiceTermProvider 的詞當關鍵詞）
          → LLM 潤飾（帶詞彙、App 類型、模式）
          → controller 透過 TextTarget 送出文字 → 狀態回到 Idle → app 換回鍵盤
```

說話修改（V4）：`mode = EDIT_SELECTION`，controller 先 `textTarget.selectedText()`，
把原文 + 語音指令送 LLM，再 `replaceSelection()`。

## 5. 合併點（對話 A、B 會合時唯一需要做的事）

1. `settings.gradle.kts` 已包含所有模組，無需修改。
2. 在 `app` 的 `ImeService` 中：
   - 呼叫 `VoiceModule.createController(...)`。
   - 工具列麥克風鍵 → `controller.start(...)`，把 `VoiceModule.createPanelView(...)` 放到鍵盤容器。
   - 觀察 `controller.state`，回到 `Idle` 時換回鍵盤。
3. 把 `core:dictionary` 的 `VoiceTermProvider` 實作與 app 的 `SecretStore` 傳給 `VoiceModule.createController`。
4. 設定頁加入「語音輸入設定」入口（B 提供 `VoiceSettingsScreen` Composable）。

預計合併工作量：約 1 個工作階段。

## 5.1 鍵盤畫面結構（app，倬確認 2026-10-09）

```
RootLayout（直向；統一處理 Android 15 底部 inset）
├ 組字框區（透明，浮在 App 內容上方；以 onComputeInsets 讓 App 不被推高）
├ SearchStripSlot（預設 GONE；剪貼簿搜尋模式放 resultsStrip）
├ 頂列（單一列）：工具列 ⇄ 候選列（組字時候選列取代工具列，同超注音）
│   候選列最前面可顯示「特殊 chip」（剪貼簿最近內容），之後才是聯想詞
├ PanelContainer（固定高度 = 鍵盤高度 K15）
│   KEYBOARD（注音／英文／符號子版面）、VOICE、CLIPBOARD
│   → 每個面板只建立一次，由 PanelController.showPanel(mode) 切換
└ 浮層：齒輪選單、編輯面板、扇子
```

- **送字規則**：所有送字（commitText、刪除、選字）一律經過 `ImeController.currentTarget: TextTarget`，
  預設為包 InputConnection 的 `ImeTextTarget`；剪貼簿搜尋模式會暫時替換。禁止直接呼叫 `currentInputConnection.commitText`。
- **編輯面板例外**：行首、上下移、開選、全選等需要 InputConnection 專屬功能，經由 `ImeEditor`；搜尋模式下停用編輯面板與 AI 欄。
- **剪貼簿搜尋模式**：由上到下 resultsStrip → 頂列 → 注音鍵盤；**輸入法整體暫時長高** resultsStrip 的高度（倬確認），按鍵大小不變。

## 6. 非功能需求（穩定優先）

- 按鍵到畫面回應 < 16ms；候選詞計算 < 30ms（主執行緒以外計算）。
- 語音：放開/停止到文字出現，目標 < 3 秒（一般長度句子）。
- 網路錯誤、逾時、API key 錯誤都必須有明確的使用者提示，絕不靜默失敗或讓鍵盤卡死。
- IME 程序崩潰會讓使用者無法打字 → 所有語音、網路例外都要在 `feature:voice` 內部攔截。
- 密碼欄位（`TextTarget.hints.isPassword`）自動停用語音與學習。
