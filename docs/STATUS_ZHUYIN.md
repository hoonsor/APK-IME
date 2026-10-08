# 注音鍵盤進度（對話 A）

## 2026-10-09

### 完成
- **系統詞庫**：`tools/build_dict.py` 把小麥注音（MIT）原始資料轉成 `app/src/main/assets/zhuyin_dict.tsv`（172,097 筆，5.8 MB）。
  只用 Python 標準函式庫，不執行 McBopomofo 的程式；授權見 `THIRD_PARTY_NOTICES.md`。
- **`engine:zhuyin` 簡拼引擎**（純 Kotlin）：
  - `Segmenter`：把輸入切成每字片段（聲調結束片段、每個聲母開始新片段、片段內符號依聲母→介音→韻母遞增）。
  - `Lexicon`：以「每字首符號」為索引鍵；支援前綴（延長找詞）與文字前綴（聯想）查詢。
  - `DefaultZhuyinEngine`：完整吻合 → 延長找詞 → 前段吻合 三組候選；頻率學習；多段選字自動建詞（Z5）；刪除使用者詞（Z6）；Backspace 可撤銷部分選字；聯想詞（Z8）。
  - `EngineListener`：建詞、使用、刪詞事件交給 app 寫入 Room。
- **自建詞規則對齊超注音**（倬確認）：依序選單字 → 自動建詞；直接選整詞 → 不建詞。部分候選改為只列第一個字的單字（去除「只吃掉部分注音的詞」雜訊）。
- **測試**：`AbbreviationTest` 21 項全部通過（含組字分段）（超注音官方範例、全拼、空白一聲、自建詞、學習、撤銷、聯想、效能）。
  桌機 JVM 上逐鍵輸入約 5 ms／鍵。
- **UI 設計稿**：claude.ai Design 畫布「倬注音 鍵盤 UI」（注音鍵盤、單圈扇子、全形符號鍵盤、語音面板）。
  扇子為單圈 4 項：半／全／數／顏。

### 驗證方式說明
此環境無法連到 Maven，所以測試是用本機 kotlinc 2.1.0 + 自製最小 JUnit 替身執行，**尚未用 Gradle 跑過**。
第一次在 Android Studio 開啟後，請執行 `./gradlew :engine:zhuyin:test` 確認。

### 已知問題 / 待辦
- 系統詞頻來自書面語料，口語詞（如「你好」）初始排名偏後（ㄋㄏ 時排第 12）；選過一次後即排第一。
  可考慮：加入口語常用詞加權表，或匯入倬的超注音使用者詞庫（需研究 `user_words_phrases.db` 格式）。
- 詞庫載入＋聯想索引首次建立約 0.6 秒（桌機）；手機上要在背景執行緒於 IME 建立時預先載入。之後可改為二進位格式加速。
- Z10「延長找詞」的實際行為是推測，需對照超注音。
- Z9 智慧鄰近修正尚未實作。

### 下一步
1. `core:dictionary`：Room 實作 `UserDictionary`、`VoiceTermProvider`，接上 `EngineListener`。
2. `app`：ImeService + 大千式鍵盤 View + 候選列 + 組字列（依設計稿），可實際安裝打字。
3. 扇子鍵、符號鍵盤、圖釘鎖定。
