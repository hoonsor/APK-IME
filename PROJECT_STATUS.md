# 倬注音（APK-IME）— 專案狀態

> 目前版本：v0.6.0 ｜ 最後更新：2026/10/12 21:40

## 📌 專案概述
個人自用的 Android 輸入法：超注音式簡拼注音鍵盤，加上 Typeless 等級的 AI 語音輸入與 AI 改寫，兩者共用同一份個人詞庫。側載 debug APK 使用（OPPO Reno 14F），穩定性第一。詳細每日紀錄見 `docs/STATUS_ZHUYIN.md`。

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
- [ ] 實機驗證：字典匯出分享到 Google Drive、Excel 編輯後匯入
- [ ] 實機驗證：錄音與真實 API 呼叫、靜音門檻 `SILENCE_RMS` 校正、MiniMax 延遲
- [ ] 實機驗證：符號翻頁、成對符號、額度提醒視窗、AI 全文改寫

## 🐛 已知問題 / 踩坑
| 問題 | 狀態 | 處理方式 |
|---|---|---|
| Android Studio 用 Java 25 跑 Gradle 8.9 失敗 | 已解決 | Gradle JDK 選 jbr-21 |
| `jvmToolchain(17)` 找不到 JDK | 已解決 | 改用 `compilerOptions.jvmTarget = 17` |
| 專案放中文路徑 Gradle 出錯 | 已規避 | 專案放 `D:\dev\APK-IME` |
| Groq 模型 HTTP 403「blocked at the project level」 | 待倬處理 | 到 Groq 後台開放模型，或改用 `openai/gpt-oss-120b` |
| ColorOS 不顯示輸入法 Toast | 已解決 | 提示改顯示在候選列 |
| 用多個 Groq 帳號繞過額度違反 Groq 條款 | 規範 | 備用 key 只放同帳號其他專案或付費 key |

## 🕘 版本歷程
回滾方式：`git revert <commit>` 撤銷單一變更；`git checkout <commit>` 檢視舊版（Android Studio：Git → Log，在該筆提交按右鍵 → Checkout Revision）。版本 tag 目前只存在雲端工作副本（雲端環境無法推送 tag），以 Commit 欄為準。

| 版本 | 日期 | Commit | 變更內容 |
|---|---|---|---|
| v0.6.0 | 2026/10/12 | （本筆提交） | feat(dict): 字典匯出 CSV（Excel／Google 試算表可編輯）、分享到 Google Drive／Dropbox、匯入（合併／取代、自動補注音、匯入前自動備份可復原） |
| v0.5.3 | 2026/10/12 | 9fabb75 | feat(keyboard): 半形符號第一頁改為鍵盤數字列順序（! @ # $ % ^ & * ( )）與常用標點 |
| v0.5.2 | 2026/10/11 | d7f97fe | docs: 版本歷程補上 v0.5.1 commit、回滾說明改用 commit hash |
| v0.5.1 | 2026/10/11 | 33ba0ed | docs: 新增 CLAUDE.md、PROJECT_STATUS.md，補上歷史版本 tag |
| v0.5.0 | 2026/10/11 | 391aaec | feat: 符號表擴充與翻頁、備用 API key、額度用完提醒（README 於 1f3ed7b 更新） |
| v0.4.2 | 2026/10/11 | 8c7c00a | feat: 編輯面板選取判斷、AI 全文、候選列提示與上下滑、成對符號、語音面板版面 |
| v0.4.1 | 2026/10/11 | 04501d7 | fix: 符號打完回到原鍵盤（英文詞加入字典不中斷）、候選字放大 |
| v0.4.0 | 2026/10/10 | d14c2c1 | feat: 語音輸入、AI 潤飾、AI 模型設定 |
| v0.3.0 | 2026/10/10 | 34b6d74 | feat: 實機回饋 14 項（剪貼簿、字典編輯、繁簡、常用短語等） |
| v0.2.1 | 2026/10/10 | fabb210 | fix: 鍵盤無法顯示、設定頁被標題列蓋住；Gradle wrapper 與 JDK 設定 |
| v0.2.0 | 2026/10/09 | d83dcd2 | feat: 可安裝的鍵盤 App |
| v0.1.0 | 2026/10/09 | 70a0444 | feat: 系統詞庫轉換與簡拼引擎 |

## ➡️ 下一步
1. 安裝最新版，實機測試語音輸入、符號翻頁、額度提醒，回報問題。
2. 依實測校正靜音門檻與 AI 模型選擇（潤飾建議改用 Groq `openai/gpt-oss-120b`）。
3. 視需要加入 emoji 符號頁。
