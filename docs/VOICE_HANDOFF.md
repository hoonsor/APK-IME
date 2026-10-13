> **已併入 AGENTS.md**（2026-10-13）。以下為歷史紀錄，現行規則以 `AGENTS.md` 為準。

> **2026-10-10 更新**：語音模組已由對話 A 依 `docs/MOBILE_VOICE_HANDOFF.md` 實作完成（見 `docs/STATUS_ZHUYIN.md`、REQUESTS R1／R3）。以下為原始交接內容，僅供參考。

# 對話 B 交接：語音模組（Typeless 等級）

> 把這份文件連同專案一起交給新對話。開頭直接說：「請讀 docs/VOICE_HANDOFF.md，開始開發語音模組」。

## 你的任務

在 `feature/voice` 與 `voice-sandbox` 中，做出 Typeless 等級的語音輸入模組。
它最後會被嵌進一個超注音風格的注音鍵盤（由另一個對話開發），所以：

- **只能修改** `feature/voice/`、`voice-sandbox/`、`docs/VOICE_*`、`docs/REQUESTS.md`。
- **必讀**：`docs/ARCHITECTURE.md`、`docs/CONVENTIONS.md`、`docs/FEATURES.md` 第 5 節、`core/contracts/`。
- 對外入口是 `VoiceModule.kt` 的三個函式，**簽章不可改**。
- 在 Git 分支 `feat/voice` 上工作。

## 使用者背景

- 倬，個人自用，穩定性優先。
- 日常語言：繁體中文（台灣），常夾雜英文技術名詞。
- 目前使用 Typeless，覺得潤飾效果好；但自訂慣用詞在 Typeless 常辨識錯，所以本模組的**個人字典注入**是重點。
- 超注音目前的語音功能（我們要超越的基準）：OpenAI gpt-4o-mini-transcribe / gpt-4o-transcribe / gpt-transcribe、
  「開始/停止」與「按住即說」錄音方式、關鍵詞、風格範例與情境提示。**它沒有 LLM 潤飾、說話修改、翻譯。**

## 必做功能（依序）

1. **V1 錄音 + STT**：AudioRecord → 壓縮（如 Opus/AAC，減少上傳時間）→ 雲端 STT。模型可在設定中選擇。
   錄音方式：點按開始/停止、按住即說。自動裁掉開頭靜音。
2. **V3 個人字典注入**：從 `VoiceTermProvider.voiceTerms()` 取詞，放進 STT 的 prompt / keywords，也放進 LLM 潤飾的 system prompt。
3. **V2 AI 潤飾**：去贅字、處理口誤自我修正、標點、必要時條列化、繁體台灣用語、英文術語保留英文。
   - 輸出只能是最終文字，不可有「以下是潤飾後的內容」之類前言。
   - 太短的輸入（如 3 個字以內）可跳過潤飾以降低延遲。
4. **V7 取消 / 還原原文**、**V8 語音面板**（深色主題）。面板配置沿用超注音，倬特別指定：
   - 第一排：快速標點與游標「， ？ 、 ！ 空白 。 ← →」——說完話後可立刻補標點，這是常用功能。
   - 第二排：「↩ 回注音鍵盤」「按我開始說」（大按鍵，錄音中變成「說完了，按我送出」）「⌫」「↵」。
   - 上方：波形、狀態文字；錄音／處理中顯示「取消」，完成後顯示「還原原文」。
   - 參考設計稿：claude.ai Design 畫布「倬注音 鍵盤 UI」的「④ 語音面板」。
5. **V11 中英混說**正確輸出。
6. **V4 說話修改**：`VoiceMode.EDIT_SELECTION`，讀 `selectedText()`，把原文 + 語音指令交給 LLM，`replaceSelection()`。沒有選取時回報 `NO_SELECTION`。
7. **V10 備援**：主要模型失敗 → 備援模型；逾時要明確提示。
8. **V5 翻譯**、**V6 依 App 語氣**（用 `EditorHints.packageName` 對應設定好的風格）。
9. **V9 歷史紀錄**（可選）。

## 技術建議（可自行判斷調整，但需在 STATUS 中說明理由）

- STT 預設 OpenAI `gpt-4o-transcribe`（中文準確度高、支援 prompt）；備援 `gpt-4o-mini-transcribe` 或 Groq Whisper。開工前先上網確認各模型目前名稱與 API 格式。
- 潤飾 LLM：選速度快的小模型（如 Claude Haiku 系列或 OpenAI mini 系列），開工前確認目前可用模型名稱。
- Provider 用介面抽象（`SttProvider`、`PolishProvider`），方便日後換模型或加入本機 whisper.cpp。
- 所有網路呼叫設逾時，例外一律轉為 `VoiceState.Error`。
- 密碼欄位（`hints.isPassword`）直接回 `PASSWORD_FIELD`，不錄音。

## Sandbox App

`voice-sandbox` 是讓你不依賴鍵盤就能完整測試的 App：
- 一個多行 EditText（用它實作 `TextTarget`）。
- 下方放 `VoiceModule.createPanelView(...)`，加上模式切換（聽寫 / 修改選取 / 翻譯）。
- 一個入口打開 `VoiceModule.SettingsScreen`。
- `FakeVoiceTermProvider`：寫死幾個測試詞（例如「倬」、「Antigravity」、「拉斐爾」、「dotenvx」），驗證個人字典有效。
- `SecretStore` 的 sandbox 實作也放在 sandbox 內。

## 驗收標準

- Sandbox 中：說一段含贅字、口誤、中英夾雜、含測試詞的話，輸出乾淨正確。
- 一般長度句子：停止錄音到文字出現 < 3 秒（網路正常時）。
- 關閉網路、填錯 API key、拒絕麥克風權限，都會顯示明確錯誤且 App 不崩潰。
- `VoiceModule` 簽章未更動；沒有修改負責範圍以外的檔案。

## 每次結束時

更新 `docs/STATUS_VOICE.md`（完成項目、下一步、已知問題、做過的技術決策）。
