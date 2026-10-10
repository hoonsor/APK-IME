# Third-Party Notices

## McBopomofo（小麥注音）詞庫資料

`app/src/main/assets/zhuyin_dict.tsv` 由 `tools/build_dict.py` 從 McBopomofo 的
`BPMFBase.txt`、`BPMFMappings.txt`、`phrase.occ`、`heterophony*.list` 轉換而來。

https://github.com/openvanilla/McBopomofo

MIT License — Copyright (c) 2011-2026 Mengjuei Hsieh et al.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## OpenCC 繁簡轉換表

`app/src/main/assets/opencc_ts_chars.txt`、`opencc_ts_phrases.txt`（繁→簡）與
`feature/voice/src/main/assets/opencc_st_phrases.txt`、`opencc_st_chars.txt`、`opencc_tw_variants.txt`（簡→台灣繁體，s2tw）取自 OpenCC
（https://github.com/BYVoid/OpenCC，`data/dictionary/` 下的 TSCharacters、TSPhrases、STPhrases、STCharacters、TWVariants）。

Apache License 2.0 — Copyright (c) 2010-2026 Carbo Kuo and contributors.
全文：http://www.apache.org/licenses/LICENSE-2.0
