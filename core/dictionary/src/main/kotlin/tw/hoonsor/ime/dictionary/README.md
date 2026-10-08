# core:dictionary（對話 A 實作）

- Room 實作 `UserDictionary` 與 `VoiceTermProvider`（皆定義於 `:core:contracts`）。
- `voiceTerms(limit)`：取 `useForVoice = true` 的詞，依 frequency、lastUsed 排序。
- 合併前，對話 B 不需要這個模組；sandbox 用 `FakeVoiceTermProvider`。
