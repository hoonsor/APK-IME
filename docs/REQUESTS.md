> **已併入 AGENTS.md**（2026-10-13）。以下為歷史紀錄，現行規則以 `AGENTS.md` 為準。

# 契約變更需求（REQUESTS）

格式：`日期｜提出方（A/B）｜想改什麼｜原因｜對另一方的影響｜狀態（待決/接受/拒絕）`

## R1｜2026-10-09｜A → B｜共用「AI 文字改寫」服務｜✅ 已實作（2026-10-10，倬指示由 A 實作語音模組）

- **想改什麼**：在 `core/contracts` 新增介面
  ```kotlin
  interface TextRewriter {
      /** instruction 為改寫指令；回傳改寫後文字。失敗回傳 Result.failure，絕不拋例外。 */
      suspend fun rewrite(text: String, instruction: String, hints: EditorHints): Result<String>
  }
  ```
  並在 `VoiceModule` 新增 `fun createRewriter(context: Context, secrets: SecretStore, terms: VoiceTermProvider): TextRewriter`。
- **原因**：編輯面板 AI 欄（FEATURES 第 6 節）與語音「說話修改」用的是同一套 LLM 呼叫。由 B 的 LLM 用戶端實作，避免兩套程式。
- **對 B 的影響**：LLM provider 需支援 MiniMax M3（OpenAI 相容，不思考模式）與 Groq；`SecretProvider` 需新增 `MINIMAX`。
- **在決定前**：A 在 app 內以 stub 實作（按鈕顯示「AI 模組尚未合併」）。

## R2｜2026-10-09｜A → C｜剪貼簿版面協調｜待決

- 頂列維持**單一列**（工具列與候選列同一列），所以搜尋模式為 resultsStrip → 頂列 → 鍵盤（非「工具列 → strip → 候選列」）。
- 搜尋模式時輸入法暫時長高 strip 的高度。
- 組字區空白時，候選列順序為：特殊 chip → 聯想詞；開始打字兩者都消失。
- 剪貼簿入口（工具列 📋 或扇子）待倬決定；若以扇子為主，剪貼簿面板底列需自備「↩ 回鍵盤」按鈕（呼叫 `host.closePanel()`）。


## R3｜2026-10-10｜A｜VoiceModule 簽章調整｜✅ 已實作

- `createPanelView(context, controller, host: VoicePanelHost)`：面板的標點、游標、回鍵盤需要輸入法配合。
- 新增 `createRewriter`、`addStateListener`、`onPanelShown`。
- 移除 Compose `SettingsScreen`，改由 :app 的 `AiSettingsActivity`（框架元件）讀寫 `AiConfig`；`:feature:voice` 不再依賴 Compose／OkHttp。
- `TextRewriter` 改為阻塞式方法（不依賴 kotlinx.coroutines 的呼叫端）。
