# 對話 A 交接：注音鍵盤主程式

> 開頭直接說：「請讀 docs/ZHUYIN_HANDOFF.md，開始開發注音鍵盤」。

## 你的任務

做出功能對齊超注音的 Android 注音輸入法，並在工具列預留語音按鈕，之後接上 `feature:voice`。

- **只能修改** `app/`、`engine/zhuyin/`、`core/dictionary/`、`docs/ZHUYIN_*`、`docs/REQUESTS.md`。
- **必讀**：`docs/ARCHITECTURE.md`、`docs/CONVENTIONS.md`、`docs/FEATURES.md` 第 1–4 節、`core/contracts/`。
- 在 Git 分支 `feat/zhuyin` 上工作。

## 開發順序

1. **系統詞庫**：下載小麥注音（McBopomofo, MIT）詞庫，寫轉換腳本，輸出成 assets 中的二進位索引。
   保留授權聲明於 `THIRD_PARTY_NOTICES.md`。
2. **`engine:zhuyin` 簡拼引擎**（純 Kotlin，先寫單元測試）：
   - 測試案例至少涵蓋：ㄋㄏ→你好、ㄆˊㄧ→朋友、ㄆㄥㄢ→平安、ㄇˇㄧㄐ→馬英九、ㄍㄡㄨㄎㄤ→購物狂、ㄧㄒㄧㄧㄧ→一心一意。
   - 索引設計：每個詞依每字聲母（或首個注音符號）建 key，查詢後再用使用者輸入的完整片段與聲調過濾、排序。
   - 候選排序 = 系統頻率 + 使用者頻率加權；使用者自建詞優先。
3. **`core:dictionary`**：Room 實作 `UserDictionary`、`VoiceTermProvider`；自建詞流程（Z5）、刪除（Z6）。
4. **`app` 最小可用鍵盤**：ImeService、大千式鍵盤 View、底列、候選列、送字。
   → 此時倬就可以在手機上實際打字測試。
5. **扇子鍵（K3）**：按下 → 扇形選單 → 滑向目標放開切換；單圈 4 項：半形符號、全形符號、數字、顏文字；符號鍵盤圖釘鎖定（K4）。
6. 其餘 P0 → P1 → P2（依 FEATURES.md）。

## UI 參考（倬的截圖）

- 工具列：⚙ / 🎤 / 簡(繁) / ← / → / ☰（編輯面板）/ ⌨（收鍵盤）。
- ⚙ 點下 → 鍵盤左上方彈出面板：參數設定／高度調整／常用短語 三個方塊（FEATURES K9a）。
- 扇子：單圈 4 項 半／全／數／顏（FEATURES K3）。
- 設計稿：claude.ai Design 畫布「倬注音 鍵盤 UI」。
- 注音 4 列 × 10 鍵（大千式，第一列含聲調鍵 ˇ ˋ ˊ ˙）。
- 底列：ABC / 扇子 / 標點手勢鍵（點=，、↖？ ↗、 ↘！，見 K2a）/ 空白 / 。/ ㄦ / ⌫ / ↵。
- ☰ 編輯面板：4×3 方塊，隨選取狀態變化（K10）。
- 深色主題、圓角灰色按鍵、淺藍色字。

## 語音按鈕預留

工具列 🎤 先放著，點擊時顯示「語音模組尚未合併」。合併方式見 ARCHITECTURE.md 第 5 節。
`ImeService` 中把鍵盤區域做成可替換的容器（FrameLayout），方便之後放入語音面板。

## 驗收標準（MVP）

- 側載安裝後可在系統設定啟用，在 LINE、Chrome 等 App 打字正常。
- 簡拼測試案例全部通過；常用詞第一頁就出現。
- 扇子鍵可切換半形/全形符號與數字。
- 自建詞、刪詞、備份/還原詞庫可用。
- 底部不被系統手勢列遮擋（Android 15+）。

## 每次結束時

更新 `docs/STATUS_ZHUYIN.md`。
