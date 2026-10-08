# 開發約定（CONVENTIONS）

兩個對話（或兩個 AI）平行開發時必須遵守的規則。

## 1. 分支與負責範圍

| 分支 | 負責 | 可以修改的路徑 |
|------|------|----------------|
| `main` | 只接受合併 | — |
| `feat/zhuyin` | 對話 A | `app/`、`engine/zhuyin/`、`core/dictionary/`、`docs/ZHUYIN_*` |
| `feat/voice` | 對話 B | `feature/voice/`、`voice-sandbox/`、`docs/VOICE_*` |

- **不可修改對方負責的路徑。** 需要對方配合的事，寫進 `docs/REQUESTS.md`（見第 3 節）。
- 兩邊都可以讀取全部檔案。
- `gradle/libs.versions.toml` 可以**新增**依賴；**升級既有版本**需寫進 REQUESTS.md，合併時統一處理。

## 2. 凍結的契約

以下檔案是合併約定，任一分支都**不可直接修改**：

- `core/contracts/**`
- `feature/voice/.../VoiceModule.kt` 的**函式簽章**（函式內容 B 可自由實作）
- `settings.gradle.kts` 的模組清單

## 3. 契約變更流程

真的需要改契約時：

1. 在 `docs/REQUESTS.md` 新增一筆：日期、提出方、想改什麼、為什麼、對另一方的影響。
2. 先用變通方式繼續開發（例如在自己的模組內做 adapter）。
3. 由倬決定是否接受；接受後在 `main` 上修改契約，兩個分支各自 rebase / merge `main`。

## 4. 程式規則

- Kotlin 官方風格；套件名稱 `tw.hoonsor.ime.*`。
- 不可在 IME 主執行緒做網路、磁碟 I/O 或重運算。
- 所有可能失敗的操作（網路、麥克風、資料庫）必須攔截例外並回報給使用者，**鍵盤絕不能因為錯誤而崩潰或卡住**。
- 不在 log 中印出 API key、使用者輸入內容或錄音內容（Release 版完全關閉輸入內容 log）。
- 每個模組的核心邏輯要有單元測試；`engine:zhuyin` 必須能用 `./gradlew :engine:zhuyin:test` 在電腦上跑。

## 5. API Key 與機密

- App 執行時的 API key：使用者在設定頁輸入，由 `SecretStore` 以 Android Keystore 加密保存。
- 開發時若要讓 sandbox 的 debug 版自動帶入 key：使用 dotenvx 加密的 `.env`，
  以 `dotenvx run -- gradlew :voice-sandbox:installDebug` 啟動建置，build script 只在 **debug** 變體讀取環境變數注入 `BuildConfig`。
  Release 版絕不注入。
- `.env.keys` 已列入 `.gitignore`，不可出現在專案資料夾。

## 6. 每次工作階段結束

- 更新 `docs/STATUS_ZHUYIN.md` 或 `docs/STATUS_VOICE.md`：完成了什麼、下一步、已知問題。
- Commit 訊息用繁體中文，前綴 `zhuyin:` / `voice:`。
- 先和倬確認再 push。
