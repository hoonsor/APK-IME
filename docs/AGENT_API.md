# AGENT_API — 倬注音語音動作（規劃中）

> **狀態：規劃中（`"status": "planned"`）。** 程式尚未實作 Hoonsor Agent API v1（`.agent.AgentProvider`）。
> 本文件與 `docs/agent-manifest.json` 內容一致；實作後 `describe` 的回傳必須和 manifest 完全相同（以單元測試比對）。

## 協定（Hoonsor Agent API v1）

- Manifest 宣告權限 `tw.hoonsor.permission.AGENT`（protectionLevel signature）；exported provider `.agent.AgentProvider`，authorities `${applicationId}.agent`，intent-filter action `tw.hoonsor.agent.PROVIDER`。
- `ContentProvider.call()`：`describe`、`execute`（extras `json`）、`health`；結果放在回傳 Bundle 的 `json`。
- 請求：`{api, requestId, action, params, dryRun, confirmToken, utterance}`。
- 成功：`{ok:true, requestId, summary, data, undo:{action, params}}`；失敗：`{ok:false, error:{code, message}}`，code ∈ `INVALID_PARAMS`、`NOT_FOUND`、`AMBIGUOUS`（data.candidates）、`NEEDS_CONFIRMATION`（data.confirmToken）、`NEEDS_UI`（data.intentUri）、`PERMISSION`、`INTERNAL`。
- risk：`read` 直接執行；`reversible` 直接執行並回 `undo`；`destructive` 一律先回 `NEEDS_CONFIRMATION`。
- 動作一律走既有資料入口：字典 → `UserPhraseStore`；剪貼簿 → `ClipboardHistory`；AI 設定 → `AiConfig`；鍵盤設定 → `Prefs`。背景執行緒、逾時 5 秒。
- **不開放**：替其他 App 輸入文字、讀取輸入框內容、讀取或回傳 API key（隱私與安全）。

## 動作一覽

| 動作 | 名稱 | risk | 對應功能 | 狀態 |
|---|---|---|---|---|
| `dictionary_phrase_list` | 查詢中文自建詞 | read | Z5、D1 | 規劃中 |
| `dictionary_phrase_add` | 新增中文自建詞 | reversible | Z5、D1 | 規劃中 |
| `dictionary_phrase_delete` | 刪除中文自建詞 | destructive | Z6 | 規劃中 |
| `dictionary_word_list` | 查詢英文詞 | read | N7 | 規劃中 |
| `dictionary_word_add` | 新增英文詞 | reversible | N7 | 規劃中 |
| `dictionary_word_delete` | 刪除英文詞 | destructive | N7 | 規劃中 |
| `quick_phrase_list` | 查詢常用短語 | read | D2 | 規劃中 |
| `quick_phrase_add` | 新增常用短語 | reversible | D2 | 規劃中 |
| `quick_phrase_delete` | 刪除常用短語 | destructive | D2 | 規劃中 |
| `clipboard_list` | 查詢剪貼簿紀錄 | read | N13 | 規劃中 |
| `clipboard_pin` | 釘選剪貼簿項目 | reversible | N13 | 規劃中 |
| `clipboard_clear` | 清除剪貼簿紀錄 | destructive | N13 | 規劃中 |
| `ai_model_get` | 查詢 AI 模型設定 | read | V10、A5 | 規劃中 |
| `ai_model_set` | 設定 AI 模型 | reversible | V10、A5 | 規劃中 |
| `ai_model_test` | 測試 AI 模型 | read | V10 | 規劃中 |
| `voice_settings_set` | 設定語音輸入選項 | reversible | V1、V2、V5 | 規劃中 |
| `output_script_set` | 切換繁簡輸出 | reversible | Z11 | 規劃中 |
| `keyboard_settings_set` | 設定鍵盤選項 | reversible | K13、K15、N13 | 規劃中 |
| `backup_export` | 匯出字典備份 | read | D4、D5 | 規劃中 |
| `backup_import` | 匯入字典 | destructive | D4 | 規劃中 |
| `backup_restore_previous` | 復原匯入前的字典 | destructive | D4 | 規劃中 |
| `app_health` | 健康檢查 | read | N16 | 規劃中 |

## 動作細節

### `dictionary_phrase_list` — 查詢中文自建詞（規劃中）

列出注音自建詞，可用關鍵字篩選。

- risk：`read`
- 範例句：「我的字典裡有哪些自建詞」；「查一下字典有沒有「拉斐爾」」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "關鍵字（詞或注音），空白則全部"
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `dictionary_phrase_add` — 新增中文自建詞（規劃中）

新增一個中文詞；注音省略時用系統詞庫自動判斷。

- risk：`reversible`
- 範例句：「把「倬注音」加到字典」；「新增詞「拉斐爾」」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "minLength": 1,
      "maxLength": 20
    },
    "reading": {
      "type": "string",
      "description": "注音，每字一個音節、空白分隔，例如「ㄒㄧㄠˇ ㄗ ㄋㄢˊ」；可省略"
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `dictionary_phrase_delete` — 刪除中文自建詞（規劃中）

刪除一個中文自建詞（內建詞不能刪）。

- risk：`destructive`
- 範例句：「從字典刪掉「小資男」」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "minLength": 1
    },
    "reading": {
      "type": "string",
      "description": "同一個詞有多個讀音時指定"
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `dictionary_word_list` — 查詢英文詞（規劃中）

列出英文／數字／符號詞（例如 Email、帳號）。

- risk：`read`
- 範例句：「我存了哪些英文詞」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string"
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 200,
      "default": 50
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `dictionary_word_add` — 新增英文詞（規劃中）

新增英文／數字／符號詞，打前幾個字母時會出現在候選列。

- risk：`reversible`
- 範例句：「把 abc@gmail.com 加到字典」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "word": {
      "type": "string",
      "minLength": 1,
      "maxLength": 64,
      "pattern": "^\\S+$"
    }
  },
  "required": [
    "word"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `dictionary_word_delete` — 刪除英文詞（規劃中）

刪除一個英文詞。

- risk：`destructive`
- 範例句：「刪掉英文詞 test123」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "word": {
      "type": "string",
      "minLength": 1
    }
  },
  "required": [
    "word"
  ],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `quick_phrase_list` — 查詢常用短語（規劃中）

列出常用短語。

- risk：`read`
- 範例句：「我的常用短語有哪些」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `quick_phrase_add` — 新增常用短語（規劃中）

新增一則常用短語（可多行）。

- risk：`reversible`
- 範例句：「新增常用短語：會議改到下午三點」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "content": {
      "type": "string",
      "minLength": 1,
      "maxLength": 2000
    }
  },
  "required": [
    "content"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `quick_phrase_delete` — 刪除常用短語（規劃中）

依內容或序號刪除一則常用短語。

- risk：`destructive`
- 範例句：「刪掉第二個常用短語」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "content": {
      "type": "string"
    },
    "index": {
      "type": "integer",
      "minimum": 0
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `clipboard_list` — 查詢剪貼簿紀錄（規劃中）

列出剪貼簿歷史（本機），可搜尋；內容可能含個資，回傳時截短。

- risk：`read`
- 範例句：「剪貼簿最近複製了什麼」；「找剪貼簿裡有「地址」的那筆」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string"
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 100,
      "default": 20
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `clipboard_pin` — 釘選剪貼簿項目（規劃中）

釘選或取消釘選一筆剪貼簿紀錄（釘選的不會被清除）。

- risk：`reversible`
- 範例句：「把剛剛複製的那筆釘選起來」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "text": {
      "type": "string",
      "minLength": 1
    },
    "pinned": {
      "type": "boolean",
      "default": true
    }
  },
  "required": [
    "text"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `clipboard_clear` — 清除剪貼簿紀錄（規劃中）

清除剪貼簿歷史；預設保留釘選項目。

- risk：`destructive`
- 範例句：「清除剪貼簿紀錄」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "includePinned": {
      "type": "boolean",
      "default": false
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `ai_model_get` — 查詢 AI 模型設定（規劃中）

列出各用途（語音轉文字、語音潤飾、文字 AI 及備援）目前的供應商與模型；不回傳 API key。

- risk：`read`
- 範例句：「語音潤飾現在用哪個模型」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `ai_model_set` — 設定 AI 模型（規劃中）

設定某個用途的供應商與模型。

- risk：`reversible`
- 範例句：「語音潤飾改用 Groq 的 gpt-oss-120b」；「文字 AI 改用 MiniMax」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "role": {
      "type": "string",
      "enum": [
        "STT",
        "STT_BACKUP",
        "POLISH",
        "POLISH_BACKUP",
        "REWRITE",
        "REWRITE_BACKUP"
      ]
    },
    "provider": {
      "type": "string",
      "enum": [
        "GROQ",
        "MINIMAX",
        "OPENAI",
        "NONE"
      ],
      "description": "NONE 只限備援用途"
    },
    "model": {
      "type": "string",
      "description": "模型 ID，例如 openai/gpt-oss-120b"
    }
  },
  "required": [
    "role",
    "provider"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `ai_model_test` — 測試 AI 模型（規劃中）

對某個用途的模型發一次測試請求，回傳是否可用與延遲。

- risk：`read`
- 範例句：「測試一下語音潤飾的模型能不能用」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "role": {
      "type": "string",
      "enum": [
        "STT",
        "STT_BACKUP",
        "POLISH",
        "POLISH_BACKUP",
        "REWRITE",
        "REWRITE_BACKUP"
      ]
    }
  },
  "required": [
    "role"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `voice_settings_set` — 設定語音輸入選項（規劃中）

設定是否潤飾、說話語言、翻譯目標語言、按住說話、開面板自動錄音。

- risk：`reversible`
- 範例句：「語音輸入改成按住說話」；「翻譯目標改成日文」；「關掉語音潤飾」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "polishEnabled": {
      "type": "boolean"
    },
    "language": {
      "type": "string",
      "enum": [
        "zh",
        "en",
        "auto"
      ]
    },
    "translateTo": {
      "type": "string"
    },
    "holdToTalk": {
      "type": "boolean"
    },
    "autoStart": {
      "type": "boolean"
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `output_script_set` — 切換繁簡輸出（規劃中）

設定輸入法輸出繁體或簡體中文。

- risk：`reversible`
- 範例句：「切換成簡體輸出」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "script": {
      "type": "string",
      "enum": [
        "traditional",
        "simplified"
      ]
    }
  },
  "required": [
    "script"
  ],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `keyboard_settings_set` — 設定鍵盤選項（規劃中）

設定鍵盤高度與偏好開關。

- risk：`reversible`
- 範例句：「鍵盤高度調到 250」；「關掉按鍵振動」；「不要記錄剪貼簿」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "heightDp": {
      "type": "integer",
      "minimum": 180,
      "maximum": 400
    },
    "haptics": {
      "type": "boolean"
    },
    "numberRow": {
      "type": "boolean"
    },
    "associations": {
      "type": "boolean"
    },
    "toolbarClipboard": {
      "type": "boolean"
    },
    "fanClipboard": {
      "type": "boolean"
    },
    "clipCapture": {
      "type": "boolean"
    },
    "clipChip": {
      "type": "boolean"
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為變更後的項目；`undo` 為反向動作（例如新增 → 刪除、設定 → 設回原值）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `backup_export` — 匯出字典備份（規劃中）

匯出字典 CSV（中文詞、英文詞、常用短語、學習紀錄）；回傳 content:// 網址供分享或存檔。

- risk：`read`
- 範例句：「備份我的字典」；「把字典分享到 Google Drive」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "share": {
      "type": "boolean",
      "default": false,
      "description": "true 時開啟分享選單（需要畫面，會回 NEEDS_UI）"
    }
  },
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `backup_import` — 匯入字典（規劃中）

從 CSV 匯入字典，合併或取代；匯入前自動保存一份可復原。

- risk：`destructive`
- 範例句：「從剛剛那個檔案匯入字典（合併）」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {
    "uri": {
      "type": "string",
      "description": "content:// 檔案網址"
    },
    "mode": {
      "type": "string",
      "enum": [
        "merge",
        "replace"
      ],
      "default": "merge"
    }
  },
  "required": [
    "uri"
  ],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `backup_restore_previous` — 復原匯入前的字典（規劃中）

把字典恢復成上次匯入之前的內容。

- risk：`destructive`
- 範例句：「復原上次匯入」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

- 回傳：第一次呼叫回 `NEEDS_CONFIRMATION`；帶 `confirmToken` 再呼叫才執行。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。

### `app_health` — 健康檢查（規劃中）

回傳輸入法是否已啟用／選用、麥克風權限、各用途是否設定 API key（只回 true/false）、詞庫是否載入。

- risk：`read`
- 範例句：「倬注音設定都正常嗎」
- 參數（JSON Schema）：

```json
{
  "type": "object",
  "properties": {},
  "required": [],
  "additionalProperties": false
}
```

- 回傳：`data` 為查詢結果（陣列或物件）。
- 錯誤：參數不合 → `INVALID_PARAMS`；找不到 → `NOT_FOUND`；多筆符合 → `AMBIGUOUS`。
