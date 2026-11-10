# 倬注音（APK-IME）— 專案狀態

> 目前版本：v0.9.0 ｜ 最後更新：2026/11/10
> 專案規則與架構見 `AGENTS.md`；每日開發細節見 `docs/STATUS_ZHUYIN.md`。

## 📌 專案概述
個人自用的 Android 輸入法：超注音式簡拼注音鍵盤，加上 Typeless 等級的 AI 語音輸入與 AI 改寫，兩者共用同一份個人詞庫。側載 debug APK 使用（OPPO Reno 14F），穩定性第一。

## 🚀 發布與手機端更新
- 發新版：改 `app/build.gradle.kts` 的 `versionCode`／`versionName` → 在「版本歷程」表格最上面新增 `| vX.Y.Z | … |` 一列（「變更內容」「為什麼」會成為 Release 更新說明）→ 推送 main 與同名 `vX.Y.Z` tag。CI 會檢查 tag 與 versionName 一致、跑單元測試、編譯並發布 `HoonsorIME-vX.Y.Z.apk`。
- 推送 main 會自動更新 `latest` 預先發行版（測試用，App 內更新與 Obtainium 不會追它）。
- 手機端：App 內更新（每天第一次開設定頁自動檢查、設定頁「6. App 更新」手動檢查）或 Obtainium（來源 `https://github.com/hoonsor/APK-IME`，不要勾 prereleases）。詳細步驟見 README「7. 手機端更新」。
- 簽章：共用金鑰 `hoonsor-release.jks`，Secrets 已寫入（2026/10/18）。憑證 SHA-256 `514ec00eea070dd3e67b4f6a0c722c9c779497bb0797e7a3446fcca4e4548825`。
- 第一次從 debug 版換成正式簽章版：先匯出字典、記下 API key → 解除安裝 → 安裝正式版 → 匯入字典、重貼 API key（只需一次）。

## ✅ 任務進度
- [x] 系統詞庫轉換與簡拼引擎（2026/10/09）
- [x] 可安裝的鍵盤 App：自繪鍵盤、扇子、標點手勢、候選列、編輯面板（2026/10/09）
- [x] 實機回饋 14 項：剪貼簿、字典編輯、繁簡、常用短語、撥號數字鍵盤、大寫鎖定（2026/10/10）
- [x] 語音輸入、AI 潤飾、AI 模型設定（Groq／MiniMax／OpenAI，各用途備援）（2026/10/10）
- [x] 英文詞加入字典不中斷、候選字放大（2026/10/11）
- [x] 編輯面板選取判斷、AI 全文改寫、候選列提示與上下滑、成對符號、語音面板版面（2026/10/11）
- [x] 符號表擴充與翻頁、備用 API key、額度用完提醒（2026/10/11）
- [x] 推上 GitHub（hoonsor/APK-IME）、README、CLAUDE.md、PROJECT_STATUS.md 版本歷程（2026/10/11）
- [x] 半形符號第一頁重排、字典匯出／分享／匯入（2026/10/12）
- [x] 補齊交接文件：AGENTS.md、README 使用說明、DESIGN.md、AGENT_API.md、agent-manifest.json（2026/10/13）
- [x] 四符號鍵：注音「。」鍵、英文「,」「.」鍵改為四符號手勢鍵，英文空白鍵縮窄（2026/10/25）
- [x] 空白鍵改為四符號鍵、設定頁可自訂六顆四符號鍵（2026/11/02）
- [ ] Hoonsor Agent API v1 實作（目前只有規劃，見 `docs/AGENT_API.md`）
- [x] 共用簽章、GitHub Actions 自動編譯（tag → Release、main → latest）、App 內更新（2026/10/18）
- [x] 每日自動備份 zip 到 Documents/HoonsorBackup/ZhuyinIME/（共用模組 android-auto-backup）＋從備份檔還原（2026/11/10）
- [ ] 流程標準補齊：健康檢查頁、LAB 版
- [ ] 聯絡人匯入（FEATURES D3；權限已宣告、功能未實作）

## 🧪 待實機驗證
- [ ] 每日自動備份（v0.9.0）：設定頁「立即備份」後 `Documents/HoonsorBackup/ZhuyinIME/` 出現 `ZhuyinIME-日期-時間.zip`；Dropsync 有同步到 Dropbox；隔天沒開設定頁也有自動備份（ColorOS 可能延後）；超過 7 份會刪最舊的；內容沒變不另存
- [ ] 從自動備份檔還原（v0.9.0）：選 zip → 確認對話框筆數正確 → 字典、鍵盤設定、AI 模型設定都回來；API key 與剪貼簿不受影響；「復原上次匯入前的字典」能反悔
- [ ] 錄音與真實 API 呼叫；靜音門檻 `SILENCE_RMS = 0.008` 校正（Logcat tag `VoiceController` 會印 peakRms／voicedFrames，不含文字）
- [ ] MiniMax-M3 延遲（文字 AI）；語音潤飾改用 Groq 後的速度
- [ ] 符號翻頁、半形第一頁順序、成對符號
- [ ] 空白鍵手勢（v0.8.0）：快打時會不會誤觸符號（門檻 24dp）、鍵面三角與左下圖示是否清楚、預設符號是否合用；設定頁自訂、留空、恢復預設是否正常
- [ ] 四符號鍵（v0.7.1）：四個方向是否好滑、英文右鍵「.com」字是否看得清楚、英文空白鍵寬度是否夠用、注音「（」自動補「）」是否想要
- [ ] 額度提醒視窗、備用 API key 切換
- [ ] AI 全文改寫（潤飾全文、AI 寫作、翻英全文）在 LINE、Messenger 等 App
- [ ] 字典匯出分享到 Google Drive／Dropbox；Excel 編輯後匯入（合併、取代、復原）
- [ ] 編輯面板：未選取時顯示「開選／全選／潤飾全文／AI 寫作／翻英全文」
- [ ] App 內更新：第一次從 debug 版換正式版，之後用「檢查更新」實際更新一次（字典與 API key 是否保留、鍵盤是否自動恢復）

## 🐛 已知問題 / 踩坑
| 問題 | 狀態 | 處理方式 |
|---|---|---|
| Android Studio 用 Java 25 跑 Gradle 8.9 失敗 | 已解決 | Gradle JDK 選 jbr-21 |
| `jvmToolchain(17)` 找不到 JDK | 已解決 | 改用 `compilerOptions.jvmTarget = 17` |
| 專案放中文路徑 Gradle 出錯 | 已規避 | 專案放 dev 底下的英文路徑 |
| Groq 模型 HTTP 403「blocked at the project level」 | 待倬處理 | 到 Groq 後台開放模型，或改用 `openai/gpt-oss-120b` |
| ColorOS 不顯示輸入法 Toast | 已解決 | 提示改顯示在候選列 |
| 用多個 Groq 帳號繞過額度違反 Groq 條款 | 規範 | 備用 key 只放同帳號其他專案或付費 key |
| 倬電腦的專案資料夾沒有追蹤 GitHub 分支，Pull 失敗 | 規避中 | AI 推上 GitHub 後用裝置橋接直接寫檔；或重新 clone 一份到英文路徑的新資料夾 |
| 雲端環境推不了 git tag（HTTP 403） | 規避 | 回滾以 commit hash 為準 |
| `versionCode／versionName` 未跟版號更新 | 已解決 | v0.7.0 起與 tag 一致，CI 會檢查 |
| `app/build.gradle.kts` 有未使用的 Compose／AndroidX 依賴 | 待處理 | 移除後需實機驗證 |

## 🕘 版本歷程
回滾方式：`git revert <commit>` 撤銷單一變更；`git checkout <commit>` 檢視舊版（Android Studio：Git → Log，在該筆提交按右鍵 → Checkout Revision）。版本 tag 目前只存在雲端工作副本（雲端環境無法推送 tag），以 Commit 欄為準。

| 版本 | 日期 | Commit | 變更內容 | 為什麼 |
|---|---|---|---|---|
| v0.9.0 | 2026/11/10 | （本筆提交） | feat(backup): 每日自動備份：每天把字典（user_dict.tsv）、鍵盤設定、AI 模型設定打包成 zip 存到 `Documents/HoonsorBackup/ZhuyinIME/`（Dropsync 同步到 Dropbox），內容沒變不另存、只留 7 份；設定頁新增開關、狀態、「立即備份」與「從自動備份檔還原」（還原前自動保留目前字典，可復原）。不含 API key 與剪貼簿 | 倬要所有 APP 每天自動備份到 Dropbox，換機或誤刪都救得回來 |
| v0.8.0 | 2026/11/02 | （本筆提交） | feat(keyboard): 注音與英文空白鍵改為四符號鍵（點一下仍是空白／一聲；注音 ↖： ↗； ↘「、英文 ↖? ↗! ↘:）；設定頁新增「自訂四符號鍵」，六顆鍵都能改內容（空白鍵左下除外）、留空用預設、可恢復預設 | 倬要更多符號一滑就到，並能自己決定放什麼 |
| v0.7.1 | 2026/10/25 | （本筆提交） | feat(keyboard): 注音空白鍵右邊「。」改為四符號鍵（點。↖& ↗● ↘（）；英文空白鍵左右改為四符號鍵（點@ ↖, ↗- ↘_／點.com ↖. ↗~ ↘;），空白鍵縮窄；`.com` 也會累積進英文詞 | 倬要常用的 & ● （ 與 email 符號一鍵就到 |
| v0.7.0 | 2026/10/18 | （本筆提交） | feat: 免接線更新：GitHub Actions 用共用簽章編譯發布 Release（main 另發 latest 測試版）；設定頁「App 更新」可檢查、下載並用系統安裝新版，每天第一次開設定頁自動檢查。fix(voice): 幻聽過濾加強（黏在一起的重複套話、整段都是套話的子句） | 倬要手機不接線就能更新；debug 版每次簽章不同，無法覆蓋安裝 |
| v0.6.1 | 2026/10/13 | （本筆提交） | docs: 補齊交接文件與使用說明（AGENTS.md、README、DESIGN、AGENT_API、manifest、FEATURES 補項） | 讓其他 AI 不看對話紀錄也能接手；讓 AI 大腦知道能用語音呼叫哪些動作 |
| v0.6.0 | 2026/10/12 | 6caec39 | feat(dict): 字典匯出 CSV（Excel／Google 試算表可編輯）、分享到 Google Drive／Dropbox、匯入（合併／取代、自動補注音、匯入前自動備份可復原） | 倬要在電腦編輯字典再匯回手機，並定期備份到雲端 |
| v0.5.3 | 2026/10/12 | 9fabb75 | feat(keyboard): 半形符號第一頁改為鍵盤數字列順序（! @ # $ % ^ & * ( )）與常用標點 | 倬：& 常用要在第一頁，較少用的往後放 |
| v0.5.2 | 2026/10/11 | d7f97fe | docs: 版本歷程補上 v0.5.1 commit、回滾說明改用 commit hash | tag 推不上 GitHub，改以 commit hash 回滾 |
| v0.5.1 | 2026/10/11 | 33ba0ed | docs: 新增 CLAUDE.md、PROJECT_STATUS.md，補上歷史版本 tag | 倬改為「直接推送但保留版本紀錄以便回滾」 |
| v0.5.0 | 2026/10/11 | 391aaec | feat: 符號表擴充與翻頁、備用 API key、額度用完提醒（README 於 1f3ed7b 更新） | 倬：符號太少、要翻頁；希望額度用完能自動切換並提醒 |
| v0.4.2 | 2026/10/11 | 8c7c00a | feat: 編輯面板選取判斷、AI 全文、候選列提示與上下滑、成對符號、語音面板版面 | 倬實機回報：面板狀態錯、AI 按了沒反應、想快速回工具列、括號要成對、「還原原文」被遮 |
| v0.4.1 | 2026/10/11 | 04501d7 | fix: 符號打完回到原鍵盤（英文詞加入字典不中斷）、候選字放大 | 倬回報打 @ 後無法加入「123456@hotmail.com」；候選字太小 |
| v0.4.0 | 2026/10/10 | d14c2c1 | feat: 語音輸入、AI 潤飾、AI 模型設定 | 倬要求依 MOBILE_VOICE_HANDOFF 實作，且語音與潤飾可用不同模型（Groq、MiniMax） |
| v0.3.0 | 2026/10/10 | 34b6d74 | feat: 實機回饋 14 項（剪貼簿、字典編輯、繁簡、常用短語等） | 倬第一次實機安裝成功後的 14 項回饋 |
| v0.2.1 | 2026/10/10 | fabb210 | fix: 鍵盤無法顯示、設定頁被標題列蓋住；Gradle wrapper 與 JDK 設定 | 倬回報 Sync 失敗、鍵盤不出現、設定頁看不到上方 |
| v0.2.0 | 2026/10/09 | d83dcd2 | feat: 可安裝的鍵盤 App | 讓倬能實機試用並對照超注音 |
| v0.1.0 | 2026/10/09 | 70a0444 | feat: 系統詞庫轉換與簡拼引擎 | 倬指示先做注音引擎；簡拼是必備功能 |

## ➡️ 下一步
1. 倬實機驗證上面「待實機驗證」清單，用編號回報。
2. 實作 Hoonsor Agent API v1（`describe`／`execute`／`health`），先做 read 類動作（`dictionary_phrase_list`、`clipboard_list`、`ai_model_get`、`app_health`），並在設定頁加「API 測試台」。
3. 健康檢查頁：輸入法啟用與選用、麥克風權限、各用途 API key 是否設定、詞庫載入。
4. 推 `v0.7.0` tag 發布第一個正式簽章版；倬依 README「第一次切換到正式簽章版」換裝（先匯出字典）。之後評估 LAB 版。
5. 清掉未使用的 Compose／AndroidX／Room 依賴（需實機驗證）。
6. 倬實機驗證 v0.9.0 自動備份與還原後，推 `v0.9.0` tag 發版。`allowBackup=false` 維持不變（自動備份已改走 Documents＋Dropsync；是否另開 Android 系統備份待倬決定）。
7. 視需要：emoji 符號頁、聯絡人匯入（D3）、語音歷史（V9）。

## 🤝 交接紀錄
| 日期 | 電腦 | 完成 | 決定＋原因 | 放棄＋原因 | 未完成 | 下一步 | 環境注意事項 |
|---|---|---|---|---|---|---|---|
| 2026/10/08～10/09 | 未記錄（雲端對話） | 骨架、功能清單、詞庫轉換、簡拼引擎、可安裝的鍵盤 | 鍵盤自繪 View（IME 穩定）；使用者詞庫用文字檔（無依賴、好備份） | A／B 兩對話平行開發（後改單一對話，倬指示）；扇子兩圈（倬：一圈就好） | 語音模組 | 實機安裝 | 雲端無法連 Maven，只能型別檢查 |
| 2026/10/10 | 未記錄（雲端對話） | Gradle wrapper、鍵盤顯示修正、14 項回饋、語音與 AI | 剪貼簿直接做在 app（倬：以現有架構為主）；預設模型依 MOBILE_VOICE_HANDOFF | `feature:clipboard` 模組；`jvmToolchain(17)`（找不到 JDK） | 錄音與 API 實機驗證 | 倬實機測試 | 倬手機 OPPO Reno 14F（ColorOS） |
| 2026/10/11 | 未記錄（雲端對話） | 實機回饋修正、符號翻頁、備用 key、額度提醒、推上 GitHub、CLAUDE.md、技能改用 CLAUDE.md 與直接推送 | 直接 commit／push 並留版本紀錄（倬授權）；備用 key 只放同帳號或付費 key | 第二個 Groq 帳號（違反條款）；語音潤飾用 MiniMax（太慢）；Whisper 潤飾（做不到） | tag 推不上 GitHub | 實機驗證 | Gradle JDK 要選 jbr-21；專案要放英文路徑 |
| 2026/10/12 | 未記錄（雲端對話） | 半形第一頁重排、字典匯出／分享／匯入；用裝置橋接把 v0.6.0 寫進倬電腦的專案資料夾（`zhuyin-voice-ime`） | 字典用 CSV（倬要用 Excel 編輯）；自寫 ExportProvider（不依賴 AndroidX） | AndroidX FileProvider；叫倬在本機 Pull（本機分支無 upstream，試多次失敗） | 實機驗證匯出入 | 倬實機測試 | 本機資料夾不是 GitHub 的乾淨 clone |
| 2026/10/13 | A（家裡；依 Project 名稱推測，待倬確認） | 補齊交接文件（AGENTS.md 等） | 以 AGENTS.md 為唯一規則來源，舊文件標「已併入」保留 | — | Agent API 實作、流程標準補齊 | 見「下一步」 | — |
| 2026/11/02 | 未確認 | 空白鍵四符號化、`PunctSlot`＋`PunctKeysActivity` 自訂介面、PunctKeyTest 增至 9 項 | 空白鍵左下鎖定（一聲不能被改掉）；空白鍵手勢門檻 24dp 防誤觸；欄位留空＝預設；空白鍵預設符號由 AI 暫定，倬可自改 | 把自訂內容寫死在版面（倬要自訂）；讓空白鍵點一下也可改（會打不出一聲） | 實機驗證、推 v0.8.0 tag | 倬實機回饋 | 寫暫存腳本放 session scratchpad，不要用共用的 `$TMPDIR`（曾誤跑別專案留下的同名檔，未造成損害）；Git 工作副本可能是 CRLF，多行取代要處理換行 |
| 2026/11/10 | A（家裡；由 42 號對話派工的子代理，在 worktree `D:/dev/zhuyin-voice-ime-backup`、分支 `feat/auto-backup`） | 套用共用模組 android-auto-backup：`tw/hoonsor/backup/` 兩個 Java 檔、`settings/ImeBackup.kt`（zip 匯出入＋`ZhuyinBackupSource`）、`UserPhraseStore.replaceAllFromTsv`、設定頁自動備份區塊、ImeBackupTest／AutoBackupTest | zip 內容：manifest.json、user_dict.tsv（原格式）、prefs/ime.json、prefs/ai_config.json（帶型別 JSON，照語音總控 SettingsBackup）；偏好用白名單，名稱像金鑰的鍵一律排除（ai_config 目前沒有金鑰，key 都在 secrets）；zip 項目用固定時間，內容沒變位元組相同才能去重；排程進入點選 `ImeService.onCreate`（輸入法常駐，背景執行緒、錯誤吞掉）＋設定頁 onCreate；資料夾名 `ZhuyinIME`（沒有 lab flavor） | 備份 clip_history（可能有密碼）、secrets（Keystore 換機解不開）、app_update（無意義）；新增 Application 類別（ImeService 已足夠，少一個改動點）；還原用「合併」（自動備份還原應以備份為準） | 實機驗證、未 push、未發版 | 倬實機驗證後合併 main、推 v0.9.0 tag | 命令列建置 `JAVA_HOME=C:/Users/hoonsor/.jdks/jbr-21.0.11`；worktree 要自己複製 `local.properties`（不進 Git） |
| 2026/10/25 | 未確認（電腦名稱 DESKTOP-5DM4EDT） | 四符號鍵（注音右鍵、英文左右鍵）、`Punct` 改為帶四角符號的 data class、新增 PunctKeyTest | 四符號鍵沿用一般符號鍵流程（成對符號、英文詞累積、大寫一次性解除）；寬文字（.com）自動內縮或縮小字級 | — | 實機驗證、推 v0.7.1 tag | 倬實機回饋 | Android Studio 內建 JBR 是 Java 25（Gradle 8.9 不能用），命令列建置要設 `JAVA_HOME=C:/Users/hoonsor/.jdks/jbr-21.0.11` |
| 2026/10/18 | A（家裡） | 幻聽過濾提交、共用簽章、GitHub Actions、App 內更新（框架元件版，移植自鬧鐘 APP） | 更新 UI 用 AlertDialog＋執行緒（專案不用 AndroidX／coroutines）；只在設定頁檢查，鍵盤不連網 | — | 推 tag、實機驗證換簽章 | 見「下一步」 | 本機建置用 `C:/Users/hoonsor/.jdks/jbr-21.0.11` |
