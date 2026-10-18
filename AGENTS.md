# AGENTS.md — 倬注音（APK-IME）

> 給接手的 AI（其他 Claude 對話、Antigravity、Codex、DeepSeek Harness、另一台電腦）。**不看對話紀錄也能接手**。
> 目前進度、版本歷程、交接紀錄 → `PROJECT_STATUS.md`；使用說明 → `README.md`；功能清單 → `docs/FEATURES.md`；
> 版面規則 → `docs/DESIGN.md`；語音動作 → `docs/AGENT_API.md`、`docs/agent-manifest.json`。
> 本檔合併自：舊版 `CLAUDE.md`（全文版）、`docs/CONVENTIONS.md`、`docs/ARCHITECTURE.md`、`docs/ZHUYIN_HANDOFF.md`、`docs/VOICE_HANDOFF.md`、`docs/REQUESTS.md`，以及 2026-10-08～10-13 的開發對話。

---

## 1. 專案目的

- **倬注音**：倬個人自用的 Android 輸入法（OPPO Reno 14F，ColorOS），側載安裝（v0.7.0 起由 GitHub Releases 發布共用簽章正式版，手機用 App 內更新或 Obtainium），不上架、不商用。
- 打字沿用**超注音**的操作（大千鍵盤、簡拼、逐字選字建詞、扇子、編輯面板、剪貼簿），UI 盡量還原超注音，讓倬不用重新學。
- 語音做到 **Typeless 等級**：雲端 STT ＋ LLM 潤飾（去贅字、改口、標點、繁體台灣用語）、說話修改、翻譯。
- 痛點：Typeless 潤飾好，但自訂慣用詞常辨識錯；超注音的語音沒有潤飾與改寫。→ 注音建立的自建詞、英文詞會自動注入 STT prompt 與潤飾提示。
- **穩定性第一優先**：鍵盤絕不能崩潰或卡住（IME 掛了就無法打字）。

## 2. 技術棧（以程式碼為準）

| 項目 | 實際使用 | 備註 |
|---|---|---|
| 語言 | Kotlin 2.1.0，jvmTarget 17 | `compilerOptions.jvmTarget`，**不用** `jvmToolchain(17)` |
| 平台 | minSdk 29、compileSdk 36、targetSdk 36；`InputMethodService` | 套件 `tw.hoonsor.ime`（applicationId），namespace `tw.hoonsor.ime.app` |
| 建置 | Gradle Wrapper 8.9、AGP 8.7.3、KSP 2.1.0-1.0.29；版本集中在 `gradle/libs.versions.toml` | Gradle JDK 必須 17 或 21（Android Studio 選 jbr-21） |
| UI | Android 框架元件＋自繪 View（Canvas）；**程式碼沒有任何 androidx／Compose import** | `app/build.gradle.kts` 仍宣告 Compose、core-ktx、DataStore、coroutines 依賴，是骨架遺留、未使用（見 §8 技術債） |
| 網路 | `HttpURLConnection`＋`org.json` | OpenAI 相容 API：Groq、MiniMax、OpenAI |
| 資料 | 系統詞庫 `app/src/main/assets/zhuyin_dict.tsv`（McBopomofo 轉換，約 17 萬筆）；使用者詞庫單一文字檔 `files/user_dict.tsv`；設定 SharedPreferences；API key Android Keystore AES-GCM | `core/dictionary`（Room）保留但未使用 |
| 繁簡 | OpenCC 字表：`app/src/main/assets/opencc_ts_*`（繁→簡輸出）、`feature/voice/src/main/assets/opencc_st_*`、`opencc_tw_variants.txt`（語音結果簡→繁台灣） | 授權見 `THIRD_PARTY_NOTICES.md` |
| 權限 | RECORD_AUDIO、INTERNET、VIBRATE、READ_CONTACTS | READ_CONTACTS 已宣告但**聯絡人匯入尚未實作** |

## 3. 建置與安裝

1. Windows 上專案放**英文路徑**（倬的電腦放在 D 槽的 dev 資料夾底下，資料夾名稱用英文）。中文路徑會讓 Gradle 出錯。
2. Android Studio → Settings → Build Tools → Gradle → **Gradle JDK = jbr-21**（或 17）。
3. Sync 後：
   ```bash
   ./gradlew :engine:zhuyin:test     # 注音引擎單元測試（22 項）
   ./gradlew :feature:voice:test     # 語音文字規則測試（VoiceTextTest、HallucinationCorpusTest）
   ./gradlew :app:testDebugUnitTest  # App 內更新的版本比較、Release 解析
   ./gradlew :app:assembleDebug
   ./gradlew :app:installDebug       # 或 Android Studio ▶ Run
   ```
4. 覆蓋安裝會保留字典與設定；**解除安裝會清掉字典、API key（Keystore）**——先用設定頁「匯出字典」。
5. 雲端（Claude Code 沙盒）無法連 Maven／Google Maven：只能用 kotlinc 對 `android.jar`（API 35）做型別檢查與純邏輯測試，產生不了 APK。
6. 倬電腦上的專案資料夾（`D:/dev/zhuyin-voice-ime`）已是 `main` 追蹤 `origin/main` 的正常 clone（2026/10/18 確認）。
7. **發新版**：改 `app/build.gradle.kts` 的 `versionCode`／`versionName`，在 `PROJECT_STATUS.md` 版本歷程表最上面加一列，推送 main 與同名 `vX.Y.Z` tag；GitHub Actions（`.github/workflows/build.yml`）用共用簽章編譯並發布 Release。細節見 README「8. 如何發新版」。
8. **簽章**：release 從環境變數 `SIGNING_KEYSTORE_PATH`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 讀共用金鑰，沒有時退回 debug 簽章（無法覆蓋安裝正式版）。不要把金鑰或密碼放進 repo。
9. **App 內更新**（`app/.../update/`）：只在設定頁檢查與下載，鍵盤服務不連網；UI 用框架 AlertDialog＋執行緒，不要改成 Compose／coroutines。

## 4. 目錄與架構

```
app/                     輸入法主體
  ImeService → ImeController   所有按鍵、面板、送字邏輯集中在 ImeController
  ImeTextTarget / ImeEditor    包 InputConnection；ImeEditor 處理游標、選取、複製貼上
  UserPhraseStore              使用者詞庫（全 App 單一實例，鍵盤與設定頁共用）
  LexiconHolder                系統詞庫只載入一次（反查注音也用它）
  keyboard/  KeyboardLayouts（版面資料）、KeyboardView（自繪）、FanOverlayView（扇子）
  topbar/    ToolbarView、CandidateBarView、ComposingBoxView
  panel/     PanelController、TileGridView（編輯面板、齒輪）、CandidateGridView、RowListView
  clipboard/ ClipboardHistory、ClipSearch（SearchTarget）
  settings/  SettingsActivity、DictionaryActivity、DictionaryCsv（匯出入格式）、ExportProvider（分享）、Prefs
  ai/        AiSettingsActivity、KeystoreSecretStore、AppVoiceTerms
  text/      ChineseConverter（繁→簡）
core/contracts/          模組間介面（純 Kotlin）：TextTarget、VoiceInput、SecretStore、TextRewriter、Dictionary
core/dictionary/         Room 詞庫（保留，未使用）
engine/zhuyin/           純 Kotlin 注音引擎（Segmenter、Lexicon、DefaultZhuyinEngine），JVM 測試
feature/voice/           語音與 AI；對外入口只有 VoiceModule
  ai/    AiConfig（供應商×用途）、AiHttp、AiRunner（key 輪替＋備援＋額度通知）、Prompts、Sanitizer
  audio/ Recorder（AudioRecord 16 kHz、靜音判斷、WAV）
  text/  TextRules、Hallucination、S2tConverter
  ui/    VoicePanelView
voice-sandbox/           語音獨立測試 App（空殼，保留）
tools/build_dict.py      由 McBopomofo 產生系統詞庫（過濾只有聲調的條目）
docs/                    功能、設計、語音 API、歷史文件
```

依賴方向只能往下：`app → engine:zhuyin / feature:voice / core:dictionary → core:contracts`。`feature:voice` 不依賴 `app`。

**畫面結構**（由上到下）：透明組字框區（`onComputeInsets` 不推高 App）→ SearchStripSlot（剪貼簿搜尋時出現）→ 頂列（工具列 ⇄ 候選列，同一列）→ 面板區（固定高度；KEYBOARD／VOICE／CLIPBOARD，只建立一次、切換 visibility）→ 浮層（齒輪、編輯面板、扇子、候選格、額度提醒）→ 底部 inset 墊片。

**送字規則**：一律經過 `ImeController.currentTarget`（預設 `ImeTextTarget`，剪貼簿搜尋時換成 `SearchTarget`）。禁止直接呼叫 `currentInputConnection.commitText`。編輯面板的游標／選取功能例外，走 `ImeEditor`。

**語音流程**：🎤 → VoicePanelView → Recorder → `AiRunner.transcribe`（STT，帶個人詞）→ 幻覺過濾、簡轉繁 → `AiRunner.chat`（潤飾）→ `Sanitizer`、`TextRules` → `TextTarget.commitText`。潤飾失敗送原文並提示；可「還原原文」。

**AI 呼叫**（`AiRunner`）：主要模型×主要 key →（429／402 額度用完）主要模型×備用 key → 備援模型（同樣兩把 key）→ 全部額度用完 → `VoiceModule.setQuotaListener` 通知鍵盤跳出提醒，之後輸出原文。用完的 key×模型記 10 分鐘直接跳過。

## 5. 關鍵設計決策＋原因

| 決定 | 原因 |
|---|---|
| 鍵盤用自繪 View，不用 Compose／AndroidX | IME 對延遲敏感，Compose 在 IME 視窗易有首次顯示延遲與生命週期問題；穩定優先 |
| 設定頁也改用框架元件（原規劃 Compose） | 與鍵盤一致、不引入依賴；語音的 Compose `SettingsScreen` 已移除（REQUESTS R3） |
| 使用者詞庫用單一文字檔，不用 Room | 沒有額外依賴；原子寫入（暫存檔改名）；備份就是複製檔案。Room 模組保留以便日後換用 |
| 網路用 HttpURLConnection，不用 OkHttp | 減少依賴、穩定優先 |
| 語音模組由同一對話實作（放棄 A／B 兩個對話平行開發） | 倬指示依 `docs/MOBILE_VOICE_HANDOFF.md`（桌面版實測結果）直接實作 |
| 剪貼簿直接做在 app（放棄另做 `feature:clipboard`） | 倬指示「與現有架構衝突時以現有架構為主」 |
| 預設模型：STT Groq `whisper-large-v3`（備援 turbo）；語音潤飾 Groq；文字 AI MiniMax-M3 | 依 MOBILE_VOICE_HANDOFF 實測；MiniMax 是倬的月訂制、額度多 |
| 語音潤飾**不要**用 MiniMax-M3 | 倬實測太慢（推理模型會先思考）；只適合不趕時間的文字 AI |
| Whisper 不能拿來潤飾 | Whisper 只做語音轉文字，不會依指令改寫；潤飾改用 Groq 上的文字模型 |
| 備用 API key 只放同帳號其他專案或付費 key | Groq Acceptable Use Policy 明文禁止用多個帳號繞過額度；放棄「第二個 Groq 帳號」方案 |
| 提示訊息放候選列 3 秒，不靠 Toast | ColorOS 常不顯示輸入法的 Toast，造成「按了沒反應」 |
| 額度提醒用鍵盤內的覆蓋視窗，不用 AlertDialog | 輸入法視窗沒有一般 Activity 的 window token，AlertDialog 不穩 |
| 預設鍵盤高度 230dp（可調 180～400） | 倬要求比原本 290dp 低 |
| 扇子單圈、項目「半／全／數／剪」 | 倬：「只要一圈就好」；「顏」依倬實機回饋移除（原因待倬確認），FanItem.KAOMOJI 保留未顯示；「剪」是剪貼簿入口 |
| 數字鍵盤為撥號盤樣式、連續輸入不跳回 | 倬：超注音的數字鍵盤是撥號盤 |
| 長按刪自建詞需 0.8 秒＋候選列確認 | 原本長按直接刪，倬要求避免誤刪 |
| 符號打一個後回到「進入前」的鍵盤（英文→符號→英文） | 原本跳回注音，會中斷「abc@gmail.com ＋」的英文詞累積 |
| 半形符號第一頁依鍵盤數字列順序（! @ # $ % ^ & …）＋常用標點 | 倬：& 常用要在第一頁 |
| 符號鍵盤翻頁鍵放最右欄（▲ ▼ 頁碼 📌） | 倬要求；符號太多 |
| 成對符號自動補後符號、游標置中；半形 `< > " '` 不配對 | `< >` 常當比較符號、引號前後同字，配對容易多出字（倬未要求配對這幾個） |
| 候選字 28sp、英文類候選 19sp，左右留白縮小 | 倬要求放大但每排詞數不變；英文字形較寬 |
| 換輸入框時用 `EditorInfo.initialSelStart/End` 初始化選取、以實際選取文字判斷 | 空白輸入框不一定會再通知選取變化，曾顯示錯的「已選取」面板 |
| AI 全文改寫用游標前後文字組成，等待中文字被改過就不覆蓋 | `getExtractedText` 不少 App 不支援；避免吃掉使用者新打的字 |
| 字典匯出用 CSV（UTF-8 BOM、CRLF），欄位 類型／內容／注音／使用次數 | 倬要在電腦用 Excel／Google 試算表編輯 |
| 開頭是 0 或很長的數字寫成 `="…"`；匯入 UTF-8 失敗改用 Big5 | Excel 會吃掉開頭 0；繁中 Windows 的 Excel「CSV」會存成 Big5 |
| 匯入分「合併／取代」，匯入前自動保存 `user_dict.before-import.tsv` 可復原 | 電腦上刪掉的詞要能同步刪除，又要能反悔 |
| 分享用自寫 `ExportProvider`，不用 AndroidX FileProvider | 維持不依賴 AndroidX；只開放 `cache/export/`、唯讀、`exported=false` |
| commit／push 直接執行，留版本紀錄可回滾 | 倬授權（2026-10-11） |
| 版本 tag 只存在雲端副本 | 雲端 Git proxy 拒絕推送 tag（HTTP 403），不繞過；回滾以 commit hash 為準 |
| 專案指導文件以 `AGENTS.md` 為主，`CLAUDE.md` 只放 `@AGENTS.md` | 讓不同 AI 讀同一份；`ANTIGRAVITY.md` 留給 Antigravity，Claude 只讀不改 |
| minSdk 29 | 原因待倬確認（骨架時決定；Android 10+ API 較乾淨）。流程標準為 minSdk 30／targetSdk 35，尚未調整 |

## 6. 絕對不要重踩的坑

1. **Gradle 8.9 不支援 Java 25**：Gradle JDK 選 jbr-21。不要為了 Java 25 升級 Gradle 9（AGP、Kotlin 要一起升，風險高）。
2. **`jvmToolchain(17)` 會 NoToolchainAvailableException**：用 `kotlin { compilerOptions { jvmTarget.set(JVM_17) } }`。
3. **中文路徑**會讓 Gradle 出錯。
4. **FanOverlayView 在寬度 0 時 `coerceIn` 當掉**，導致整個鍵盤不顯示：版面計算前檢查寬高；ImeService 用 `safe()` 包住所有回呼。
5. **Android 15 強制 edge-to-edge**：設定頁內容被狀態列蓋住 → 用 NoActionBar＋自行套 inset padding；鍵盤底部要留手勢列 inset。
6. **詞庫裡只有聲調的條目（ˊ 等）會讓引擎當掉**：`build_dict.py` 與 `Lexicon` 都要過濾。
7. **`Hallucination` 的 regex 要宣告在 PHRASES 之前**，否則 ExceptionInInitializerError（Kotlin object 初始化順序）。
8. **ColorOS 不顯示輸入法 Toast**：提示一律放候選列或鍵盤內視窗。
9. **Groq HTTP 403「model is blocked at the project level」**：是 Groq 後台 project 的模型權限，不是 App 錯誤。
10. **不可用多個 Groq 帳號繞額度**（違反條款）。
11. **Excel CSV**：開頭 0 被吃掉、存成 Big5 —— 已處理，改格式時不要破壞 `="…"` 與 Big5 回退。
12. **單例不要持有 Activity context**（`UserPhraseStore` 只拿 `applicationContext` 建檔案路徑）。
13. **自寫 ContentProvider 要擋路徑穿越**（`ExportProvider.fileFor` 比對 canonical path）。
14. **分享檔案要帶 ClipData＋`FLAG_GRANT_READ_URI_PERMISSION`**，否則雲端 App 讀不到。
15. **雲端推不了 tag**（HTTP 403），不要嘗試其他方式繞過。
16. **倬的本機資料夾沒有 upstream**：不要叫倬 Pull；改用裝置橋接寫檔，或請倬重新 clone。
17. **MiniMax-M3 是推理模型**：輸出可能含 `<think>`（`Sanitizer` 會清掉），延遲長，不適合語音潤飾。
18. **解除安裝會清掉 Keystore 的 key 與字典**；換簽章前先匯出字典。

## 7. 倬的偏好

- 一律繁體中文，技術名詞保留英文；討論規劃要完整說明理由與替代方案，完成回報要精簡。
- 穩定性第一；做不到就直說原因。
- UI 盡量還原超注音的操作；深色、按鈕有對比、不要廉價感。
- 改程式前先看既有架構，不要覆蓋已完成的功能；倬實機回報用編號，逐項回「已修／不修＋原因」。
- 檔案交付：推 GitHub，再寫進倬電腦的專案資料夾（倬不擅長處理 Git 衝突）。

## 8. 技術債（尚未處理）

- `app/build.gradle.kts` 宣告了未使用的 Compose、core-ktx、DataStore、coroutines 依賴；`core/dictionary` 的 Room＋KSP 未使用。拿掉可加快建置，但要實機驗證。
- `versionCode = 1`、`versionName = "0.1.0"` 沒有跟著版號更新（實際版本見 `PROJECT_STATUS.md`）。
- READ_CONTACTS 已宣告但聯絡人匯入（FEATURES D3）未實作。
- 尚未符合倬的 APP 流程標準：Agent API（`docs/AGENT_API.md` 為規劃）、備份 zip、健康檢查頁、共用簽章 hoonsor-release.jks、LAB 版、GitHub Actions 自動編譯。

## 9. Always／Ask／Never

**Always**
- 先讀 `PROJECT_STATUS.md` 的「下一步」與交接紀錄。
- 改動前確認不破壞既有功能；核心邏輯改動補測試（`engine:zhuyin`、`feature/voice` 的 VoiceTextTest）。
- 所有送字走 `currentTarget`；網路、錄音、檔案 I/O 一律背景執行緒並攔截例外，失敗要有可見提示。
- 推送前掃描敏感資料；只 stage 本次相關檔案；Conventional Commits、繁體中文；每次推送在 `PROJECT_STATUS.md` 版本歷程記一列（含 commit 與原因）。
- 功能改變更新 `README.md`；架構、規則、踩坑更新本檔；動作改變更新 `docs/AGENT_API.md` 與 `docs/agent-manifest.json`。
- 只寫相對路徑。

**Ask（先問倬）**
- 新增第三方依賴、升級 Gradle／AGP／Kotlin。
- 改 `core/contracts` 介面、`VoiceModule` 對外函式。
- 改套件名、簽章、minSdk。
- 會清除或改寫使用者資料的變更（字典格式、Keystore）。
- force push、刪分支、改寫已推送歷史、部署。

**Never**
- 把 API key、token、密碼寫進程式碼、log、錯誤訊息或 Git。
- 在 IME 主執行緒做網路或重運算；吞掉錯誤不提示。
- 用多個帳號繞過 AI 供應商的額度。
- 修改 `ANTIGRAVITY.md`（若存在；屬於 Antigravity）。
- 編造不知道的原因（寫「原因待倬確認」）。
