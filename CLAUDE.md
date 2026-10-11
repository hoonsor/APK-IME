# CLAUDE.md

> 本文件是本專案給 Claude 的最高指導原則（Source of Truth）。Claude 每次開工或開啟新對話時必須先讀取，並嚴格遵守以下約定。
> 進度與版本歷程不寫在這裡，請見 `PROJECT_STATUS.md` 與 `docs/STATUS_ZHUYIN.md`。
> 詳細設計見 `docs/ARCHITECTURE.md`、`docs/FEATURES.md`、`docs/CONVENTIONS.md`；最新進度見 `docs/STATUS_ZHUYIN.md`。

---

## 📖 專案基本資訊 (Project Info)
*   **專案名稱**：倬注音（倉庫 `hoonsor/APK-IME`，套件 `tw.hoonsor.ime`）
*   **專案目標**：超注音式簡拼注音鍵盤 + Typeless 等級 AI 語音輸入與 AI 改寫，兩者共用同一份個人詞庫。UI 盡量還原超注音的操作。
*   **目標客群 / 使用場景**：倬個人自用（OPPO Reno 14F，ColorOS），側載 debug APK，不上架、不商用。**穩定性第一優先**。

---

## 🛠 技術棧與環境要求 (Tech Stack & Environment)
*   **開發語言**：Kotlin 2.1.0（jvmTarget 17）
*   **平台**：Android minSdk 29、compileSdk 36；`InputMethodService`
*   **建置**：Gradle Wrapper 8.9、AGP 8.7.3、KSP 2.1.0-1.0.29；版本集中在 `gradle/libs.versions.toml`
*   **Gradle JDK**：17 或 21（Android Studio 選 jbr-21）。**Gradle 8.9 不支援 Java 25**。
*   **UI**：只用 Android 框架元件與自繪 View（不用 Compose、AndroidX），保持輸入法輕量穩定。
*   **AI 串接**：OpenAI 相容 API（Groq、MiniMax、OpenAI），`HttpURLConnection`，不加第三方 HTTP 函式庫。
*   **資料**：系統詞庫 `app/src/main/assets/zhuyin_dict.tsv`（McBopomofo 轉換，約 17 萬筆）、使用者詞庫文字檔、SharedPreferences。
*   **機密**：App 執行時的 API key 由使用者在「AI 模型設定」輸入，`KeystoreSecretStore` 以 Android Keystore AES-GCM 加密保存。開發用 `.env` 一律用 dotenvx 加密，`.env.keys` 不可進 Git。
*   **開發路徑**：Windows 上專案資料夾必須是**英文路徑**（例如 `D:\dev\APK-IME`），中文路徑會讓 Gradle 出錯。

---

## ⌨️ 常用指令
```bash
./gradlew :engine:zhuyin:test     # 注音引擎單元測試
./gradlew :app:assembleDebug      # 建置 debug APK
./gradlew :app:installDebug       # 安裝到已連接的手機
```

---

## 📁 專案目錄結構規範 (Directory Structure)
*   `app/`：輸入法主體。`ImeService` → `ImeController`（按鍵、面板、送字集中在這裡）；`keyboard/`、`topbar/`、`panel/`、`clipboard/`、`settings/`、`ai/`（AI 模型設定、Keystore）、`text/`（繁簡轉換）。
*   `core/contracts/`：模組間共用介面（`TextTarget`、`VoiceInput`、`SecretStore`、`TextRewriter`、`Dictionary`）。純 Kotlin。
*   `core/dictionary/`：Room 詞庫（保留，目前未使用）。
*   `engine/zhuyin/`：純 Kotlin 注音引擎（簡拼、組字分段、候選、自建詞），含單元測試。
*   `feature/voice/`：語音與 AI（錄音、STT、潤飾、改寫、備援、額度處理、語音面板）。只依賴 contracts＋Android 框架。對外入口只有 `VoiceModule`。
*   `voice-sandbox/`：語音模組獨立測試 App（保留）。
*   `tools/build_dict.py`：產生系統詞庫。
*   `docs/`：架構、功能、規範、交接與進度文件。

---

## 🛡️ 開發守則與代碼風格 (Development & Coding Standards)
*   **命名規範**：Kotlin 官方風格；類別 PascalCase、函式與變數 camelCase；套件 `tw.hoonsor.ime.*`。
*   **註解規範**：公共 API 與複雜邏輯用中文註解，並註明對應的需求來源（例如「需求七」「MOBILE_VOICE_HANDOFF §8.1」）。
*   **送字規則**：所有輸出一律經過 `ImeController.currentTarget`（`TextTarget`），剪貼簿搜尋模式會換成 `SearchTarget`。
*   **穩定性**：IME 主執行緒不做網路、磁碟 I/O、重運算；所有可能失敗的操作都要攔截例外並回報，**鍵盤絕不能崩潰或卡住**。
*   **隱私**：log 不可印出 API key、使用者輸入內容、錄音內容；錯誤訊息不得含 key。
*   **測試**：`./gradlew :engine:zhuyin:test`（引擎）、`feature/voice` 的 `VoiceTextTest`（文字規則）；改動核心邏輯要補測試。
*   **進度紀錄**：每次工作結束更新 `PROJECT_STATUS.md` 與 `docs/STATUS_ZHUYIN.md`（流水帳不寫進本文件）。
*   **版本與提交**：SemVer `vX.Y.Z`；Conventional Commits（`feat:`、`fix:`、`docs:`…），訊息用繁體中文。
*   **Git 分支管理**：
    *   主要分支：`main`（GitHub 預設分支，倬在 Android Studio 用 Git → Pull 取得更新）。
    *   開發：單人開發，一般直接在 `main` 小步提交；大型或高風險改動另開 `feat/<名稱>` 分支，確認後合併。
    *   **commit / push 直接執行**，每次推送記入 `PROJECT_STATUS.md` 版本歷程，版本號變動時打 `vX.Y.Z` tag；部署與破壞性操作（force push、刪分支、改寫歷史）先經倬確認。

---

## ⚠️ 絕對禁止事項 (Never Do)
1.  **嚴禁提交敏感資訊**：嚴禁將 API Keys、Tokens、密碼或個人隱私資訊寫死在程式碼中或提交至 Git。
2.  **嚴禁直接破壞主分支**：未經測試與確認前，禁止對 main 進行 `--force` 推送。
3.  **嚴禁覆蓋既有功能**：修改前必須確認不會破壞既有功能，必要時先跑現有測試。
4.  **嚴禁引入重量級依賴**：鍵盤主體不加 Compose、AndroidX、OkHttp 等第三方函式庫（穩定性與啟動速度優先）；確有必要時先和倬討論。
5.  **嚴禁違反供應商條款**：例如不可用多個 Groq 帳號繞過免費額度（Groq Acceptable Use Policy 明文禁止）。
6.  **嚴禁在 IME 中阻塞主執行緒或吞掉錯誤不提示**。
