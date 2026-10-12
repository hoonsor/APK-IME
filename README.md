# 倬注音（APK-IME）

> 個人自用的 Android 輸入法：超注音式的簡拼注音鍵盤，加上 Typeless 等級的 AI 語音輸入與 AI 改寫，兩者共用同一份個人詞庫。

## 📖 專案簡介 (Overview)

倬注音是一套裝在 Android 手機上的中文輸入法。打字的部分沿用超注音的操作習慣：大千注音鍵盤、簡拼（只打聲母就能出詞，例如「ㄋㄏ」出「你好」）、逐字選字自動建立自建詞、扇形切換符號與數字、編輯面板與剪貼簿歷史，盡量讓用慣超注音的人不用重新學。

說話的部分則補上超注音沒有的 AI 能力：按下麥克風說話，語音先由雲端模型轉成文字，再由大型語言模型去掉贅字、處理口誤改口、補上標點，轉成台灣繁體用語後直接送進輸入框。除了聽寫，也能用說的修改選取的文字、直接翻譯；在編輯面板還能一鍵「潤飾全文」、「AI 寫作」、「翻英全文」，不滿意可以還原原文。

開發的出發點是：Typeless 的潤飾效果很好，但自訂的慣用詞常常辨識錯，而超注音的語音功能又沒有潤飾與改寫。倬注音把注音打出來的自建詞、英文詞自動注入語音辨識與 AI 潤飾的提示裡，常用的人名、專有名詞、技術名詞用說的也能正確輸出。

這個專案為個人使用而設計，以穩定為第一優先：鍵盤主體只用 Android 框架、不依賴第三方 UI 函式庫；API key 以 Android Keystore 加密只存在手機；AI 失敗時自動改用備援模型，額度用完時會提醒並改輸出原文，不會讓打字卡住。

## ✨ 核心功能 (Key Features)
- **簡拼注音**：聲母簡拼、組字框分段顯示、候選列（▼ 展開、左右滑動）、聯想詞；內建約 17 萬詞（McBopomofo 詞庫）。
- **個人字典**：逐字選字自動成詞、英文／符號詞一鍵「＋」加入字典、選取文字「納入字典」自動反查注音；設定頁可檢視、搜尋、編輯；可匯出 CSV 到電腦用 Excel／Google 試算表編輯後再匯入，或分享到 Google Drive、Dropbox 備份。
- **AI 語音輸入**：聽寫／修改選取／翻譯三種模式，點按或按住說話，快速標點列；靜音偵測、幻覺過濾、簡轉繁（OpenCC s2tw）。
- **AI 改寫**：編輯面板的潤飾（選取／全文）、AI 寫作、翻英，完成後可「還原原文」。
- **AI 模型設定**：Groq／MiniMax／OpenAI，語音轉文字、語音潤飾、文字 AI 各自選模型並有備援；可設定備用 API key；額度用完自動提醒並改用原文輸出。
- **符號與數字**：扇形切換「半／全／數／剪」，符號鍵盤可翻頁（箭頭、核取方塊、希臘字母、數學、框線等），撥號盤式數字鍵盤，成對符號自動補齊並把游標放中間。
- **剪貼簿**：輸入法使用中自動記錄文字（敏感內容不記錄、只存本機），可釘選、搜尋、一點即貼；剛複製的內容在候選列直接貼上。
- **其他**：繁／簡輸出切換、常用短語、編輯面板（開選、全選、游標移動、前後刪）、大寫鎖定、鍵盤高度調整、密碼欄位自動停用 AI 與學習。

## 🛠 技術棧 (Tech Stack)
- **語言 / 平台**：Kotlin 2.1、Android（minSdk 29、compileSdk 36），InputMethodService
- **UI**：Android 框架元件與自繪 View（不使用 Compose / AndroidX，保持輸入法輕量穩定）
- **AI 串接**：OpenAI 相容 API（Groq、MiniMax、OpenAI），HttpURLConnection，無第三方 HTTP 函式庫
- **資料**：詞庫 TSV 資產、使用者詞庫文字檔（原子寫入）、SharedPreferences、Android Keystore（AES-GCM）
- **建置工具**：Gradle 8.9、Android Gradle Plugin 8.7.3、KSP

## 🚀 快速開始 (Quick Start)

### 環境要求
- Android Studio（Gradle JDK 請選 17 或 21，例如內建的 jbr-21；Gradle 8.9 不支援 Java 25）
- Android 10 以上的手機，開啟 USB 偵錯

### 安裝與運行
```bash
# 1. 複製專案
git clone https://github.com/hoonsor/APK-IME.git

# 2. 用 Android Studio 開啟資料夾，等待 Gradle Sync 完成

# 3. （可選）執行注音引擎測試
./gradlew :engine:zhuyin:test

# 4. 手機接上電腦後按 ▶ Run，或：
./gradlew :app:installDebug
```

### 第一次使用
1. 開啟「倬注音」App →「開啟系統輸入法設定」啟用倬注音 →「切換輸入法」。
2. 要用語音與 AI：⚙ →「AI 模型設定」貼上 API key、按「測試」，並授權麥克風。

## 📁 專案結構 (Project Structure)
```text
app/                 # 輸入法主體：鍵盤、候選列、面板、剪貼簿、設定頁、AI 模型設定
core/contracts/      # 模組之間的共用介面（TextTarget、VoiceInput、SecretStore、TextRewriter）
core/dictionary/     # Room 詞庫（保留，目前未使用）
engine/zhuyin/       # 純 Kotlin 注音引擎：簡拼、組字分段、候選排序、自建詞（含單元測試）
feature/voice/       # 語音與 AI：錄音、STT、潤飾、改寫、備援與額度處理、語音面板
voice-sandbox/       # 語音模組獨立測試用 App（保留）
tools/build_dict.py  # 由 McBopomofo 資料產生 zhuyin_dict.tsv
docs/                # 架構、功能清單、開發規範、進度紀錄（STATUS_ZHUYIN.md）
```

## 🔄 最新更新 (Recent Updates)
目前版本：**v0.6.0**（開發中，個人實機測試階段）

- **2026-10-12**：字典匯出（CSV，電腦可編輯）、分享到 Google Drive／Dropbox、匯入（合併／取代、可復原）；半形符號第一頁依鍵盤數字列順序重排。
- **2026-10-11**：符號表擴充（箭頭、核取方塊、新酷音分類）並可翻頁；AI 備用 API key 與額度用完提醒；編輯面板選取判斷修正、AI 全文改寫、候選列上下滑回工具列、成對符號自動補齊、語音面板版面調整、候選字放大。
- **2026-10-10**：語音輸入、AI 潤飾與 AI 模型設定（Groq／MiniMax／OpenAI，各用途分開設定與備援）；實機回饋 14 項（剪貼簿、字典編輯、繁簡切換、常用短語、撥號式數字鍵盤、大寫鎖定等）。
- **2026-10-09**：可安裝的鍵盤 App 與簡拼注音引擎第一版。

詳細進度見 `docs/STATUS_ZHUYIN.md`；第三方授權見 `THIRD_PARTY_NOTICES.md`。
