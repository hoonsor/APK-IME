package tw.hoonsor.ime.app.keyboard

enum class KeyStyle { NORMAL, DARK }

sealed interface KeyAction {
    /** 注音符號或聲調，交給引擎。 */
    data class Zhuyin(val c: Char) : KeyAction

    /** 直接送出文字（英文、符號）。 */
    data class Commit(val text: String) : KeyAction

    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data object ToEnglish : KeyAction
    data object ToZhuyin : KeyAction

    /** 扇子鍵（K3）：按住滑動選擇。 */
    data object Fan : KeyAction

    /**
     * 四符號手勢鍵（K2a）：點一下 = 左下（粉紅色）；按住往 ↖ ↗ ↘ ↙ 滑放開 = 該角的符號。
     * 鍵面四角顯示對應符號；可以是多個字（例如 .com）。
     */
    data class Punct(
        val downLeft: String,
        val upLeft: String,
        val upRight: String,
        val downRight: String,
    ) : KeyAction {
        /** 依手指位移決定送出哪個角的符號；位移很小視為點一下。 */
        fun pick(dx: Float, dy: Float, tapSlop: Float): String {
            if (kotlin.math.abs(dx) < tapSlop && kotlin.math.abs(dy) < tapSlop) return downLeft
            return if (dy < 0) (if (dx < 0) upLeft else upRight) else (if (dx < 0) downLeft else downRight)
        }
    }

    /** 符號鍵盤圖釘（K4）。 */
    data object Pin : KeyAction

    /** 符號鍵盤翻頁。 */
    data object PageUp : KeyAction
    data object PageDown : KeyAction

    /** 不做事的鍵（頁碼、空格補位）。 */
    data object Nop : KeyAction
}

data class KeyDef(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val textSizeSp: Float = 22f,
)

/** 扇子項目（K3）。顏文字目前不顯示在扇子上（倬：用不到），保留版面以便日後開啟。 */
enum class FanItem(val glyph: String, val title: String) {
    HALF("半", "半形符號"),
    FULL("全", "全形符號"),
    NUM("數", "數字"),
    KAOMOJI("顏", "顏文字"),
    CLIPBOARD("剪", "剪貼簿"),
}

object KeyboardLayouts {

    // 四符號手勢鍵（參數順序：左下＝點一下、左上、右上、右下）
    /** 注音空白鍵左邊：點 = ，、↖？ ↗、 ↘！ */
    val ZHUYIN_PUNCT_LEFT = KeyAction.Punct("，", "？", "、", "！")
    /** 注音空白鍵右邊（倬，2026-10-25）：點 = 。、↖& ↗● ↘（ */
    val ZHUYIN_PUNCT_RIGHT = KeyAction.Punct("。", "&", "●", "（")
    /** 英文空白鍵左邊（倬，2026-10-25）：點 = @、↖, ↗- ↘_ */
    val ENGLISH_PUNCT_LEFT = KeyAction.Punct("@", ",", "-", "_")
    /** 英文空白鍵右邊（倬，2026-10-25）：點 = .com、↖. ↗~ ↘; */
    val ENGLISH_PUNCT_RIGHT = KeyAction.Punct(".com", ".", "~", ";")

    /** 大千式注音（倬的截圖）。 */
    val ZHUYIN: List<List<KeyDef>> by lazy {
        val rows = listOf(
            "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢ",
            "ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ",
            "ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ",
            "ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ",
        ).map { row -> row.map { c -> KeyDef(c.toString(), KeyAction.Zhuyin(c)) } }
        rows + listOf(
            listOf(
                KeyDef("ABC", KeyAction.ToEnglish, style = KeyStyle.DARK, textSizeSp = 15f),
                KeyDef("", KeyAction.Fan, style = KeyStyle.DARK),
                KeyDef("", ZHUYIN_PUNCT_LEFT),
                KeyDef("", KeyAction.Space, weight = 1.7f),
                KeyDef("", ZHUYIN_PUNCT_RIGHT),
                KeyDef("ㄦ", KeyAction.Zhuyin('ㄦ')),
                KeyDef("", KeyAction.Backspace, style = KeyStyle.DARK),
                KeyDef("", KeyAction.Enter, style = KeyStyle.DARK),
            ),
        )
    }

    /** 扇子顯示的項目（依序）。 */
    val FAN_ITEMS = listOf(FanItem.HALF, FanItem.FULL, FanItem.NUM, FanItem.CLIPBOARD)

    /**
     * 數字鍵盤：仿手機撥號鍵盤（倬：超注音的數字鍵盤是撥號盤樣式）。
     * 連續輸入不會跳回，按左下角鍵才回到原本的鍵盤（注音或英文）。
     */
    fun dialer(backLabel: String): List<List<KeyDef>> {
        fun d(t: String) = KeyDef(t, KeyAction.Commit(t), weight = 1.3f, textSizeSp = 28f)
        fun side(t: String) = KeyDef(t, KeyAction.Commit(t), style = KeyStyle.DARK, textSizeSp = 22f)
        return listOf(
            listOf(d("1"), d("2"), d("3"), side("-")),
            listOf(d("4"), d("5"), d("6"), side(".")),
            listOf(d("7"), d("8"), d("9"), KeyDef("", KeyAction.Backspace, style = KeyStyle.DARK)),
            listOf(d("*"), d("0"), d("#"), KeyDef("", KeyAction.Enter, style = KeyStyle.DARK)),
            listOf(
                KeyDef(backLabel, KeyAction.ToZhuyin, weight = 1.3f, style = KeyStyle.DARK, textSizeSp = 16f),
                KeyDef(",", KeyAction.Commit(","), weight = 0.9f),
                KeyDef("", KeyAction.Space, weight = 1.7f),
                KeyDef("+", KeyAction.Commit("+"), weight = 0.9f),
                KeyDef("/", KeyAction.Commit("/"), weight = 0.9f),
            ),
        )
    }

    /** 英文鍵盤；numberRow = K5「上排固定顯示數字」。 */
    fun english(upper: Boolean, numberRow: Boolean): List<List<KeyDef>> {
        fun letters(s: String) = s.map { c ->
            val t = if (upper) c.uppercaseChar().toString() else c.toString()
            KeyDef(t, KeyAction.Commit(t))
        }
        val rows = ArrayList<List<KeyDef>>()
        if (numberRow) rows += "1234567890".map { KeyDef(it.toString(), KeyAction.Commit(it.toString())) }
        rows += letters("qwertyuiop")
        rows += letters("asdfghjkl")
        rows += listOf(KeyDef("⇧", KeyAction.Shift, weight = 1.5f, style = KeyStyle.DARK)) +
            letters("zxcvbnm") +
            listOf(KeyDef("", KeyAction.Backspace, weight = 1.5f, style = KeyStyle.DARK))
        rows += listOf(
            KeyDef("注", KeyAction.ToZhuyin, weight = 1.2f, style = KeyStyle.DARK, textSizeSp = 18f),
            KeyDef("", KeyAction.Fan, style = KeyStyle.DARK),
            // 空白鍵縮窄，讓兩側四符號鍵寬一點（右鍵有「.com」）
            KeyDef("", ENGLISH_PUNCT_LEFT, weight = 1.4f),
            KeyDef("", KeyAction.Space, weight = 2.8f),
            KeyDef("", ENGLISH_PUNCT_RIGHT, weight = 1.6f),
            KeyDef("", KeyAction.Enter, weight = 1.5f, style = KeyStyle.DARK),
        )
        return rows
    }

    private fun chars(s: String): List<String> {
        // 以 code point 切開（避免把代理字元拆壞），去掉空白
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            if (!Character.isWhitespace(cp)) out += s.substring(i, i + n)
            i += n
        }
        return out
    }

    /**
     * 半形與常用符號（倬：加入箭頭、核取方塊等）。
     * 第一頁是常用的 ASCII 符號與標點（依鍵盤數字列順序），之後依類別排列；分類參考新酷音符號表，內容自行整理。
     */
    private val HALF: List<String> by lazy {
        // 第一頁（7×4）：依實體鍵盤數字列 1234567890 的上檔符號排列，再放常用標點（倬：& 常用要在第一頁）
        (chars("! @ # $ % ^ &") +          // Shift + 1～7
            chars("* ( ) - _ = +") +       // Shift + 8 9 0、- _ = +
            chars(", . ? : ; ' \"") +      // 常用標點
            chars("/ \\ | ~ ` [ ]") +
            // 第二頁起：較少用的 ASCII、箭頭、核取方塊、圖形…
            chars("{ } < >") +
            chars("← ↑ → ↓ ↖ ↗ ↘ ↙ ↔ ↕ ⇐ ⇑ ⇒ ⇓ ⇔ ⇕ ➜ ➔ ↩ ↪ ⤴ ⤵ ↺ ↻ ⇄ ⇅ ▶ ◀") +
            chars("☐ ☑ ☒ ✓ ✔ ✗ ✘ ✕ ✖ ⭕ ❌ ❎ • ◦ ‣ ⁃ ○ ● ◎ ◉ □ ■ ◇ ◆ △ ▲ ▽ ▼ ▷ ◁ ☆ ★ ♡ ♥") +
            chars("± × ÷ ≠ ≈ ≒ ≤ ≥ ≦ ≧ ∞ √ ∑ ∏ ∫ ∆ ∂ ∵ ∴ ∈ ∉ ⊂ ⊃ ∩ ∪ ∀ ∃ ¬ ∧ ∨ ⊕ ° ′ ″ ‰ ½ ⅓ ¼ ¾ ² ³ ¹ ₂ µ π") +
            chars("¢ £ ¥ € ₩ ฿ ₹ ₿ ℃ ℉ ㎎ ㎏ ㎝ ㎜ ㎞ ㎡ ㏄ ℓ № ℡ © ® ™ § ¶ † ‡ ※ … ‧ ·") +
            chars("♀ ♂ ♠ ♣ ♦ ♪ ♫ ☀ ☁ ☂ ☃ ☎ ✉ ✈ ✂ ✏ ✒ ⚠ ⚡ ☕ ⌘ ⌥ ⇧ ⏎ ⌫ ⏰ ⌛ ☯ ♻ ⚑ ⚐")).distinct()
    }

    /** 全形標點與符號：常用標點在前，其後依新酷音的分類（括號、直書括號、希臘字母、數學、圖形、框線、方塊、線段）。 */
    private val FULL: List<String> by lazy {
        (chars("，。、；：？！…「」『』（）【】《》〈〉—～＋－＊／＝％＠＃＆＿｜＼‧·") +
            chars("‥﹐﹒˙‘’“”〝〞‵′〃＄") +
            chars("〔〕｛｝［］〖〗﹙﹚﹝﹞﹛﹜") +
            chars("︵︶﹁﹂︹︺︷︸︿﹀﹃﹄︽︾︻︼") +
            chars("αβγδεζηθικλμνξοπρστυφχψωΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ") +
            chars("×÷≠≒∞±√＜＞≦≧∩∪⊥∠∟⊿㏒㏑∫∮∵∴") +
            chars("↑↓←→↖↗↙↘㊣◎○●⊕⊙△▲☆★◇◆□■▽▼§￥〒￠￡※♀♂") +
            chars("♨☀☁☂☃♠♥♣♦♩♪♫♬☺☻") +
            chars("├─┼┴┬┤┌┐╞═╪╡│▕└┘╭╮╰╯") +
            chars("╔╦╗╠╬╣╓╥╖╒╤╕║╚╩╝╟╫╢╙╨╜╘╧╛") +
            chars("▁▂▃▄▅▆▇█▏▎▍▌▋▊▉◢◣◥◤") +
            chars("﹣﹦≡｜∣∥–︱︳╴¯￣﹉﹊﹍﹎﹋﹌﹏︴∕﹨╱╲")).distinct()
    }

    private val NUM = "1 2 3 4 5 6 7 8 9 0 . , + - * / = % ( ) $ # : ①".split(' ')
    private val KAOMOJI = listOf(
        "(*´∀`)~♥", "(｡◕∀◕｡)", "(´・ω・`)", "(╯°□°)╯︵ ┻━┻", "ヽ(✿ﾟ▽ﾟ)ノ", "(｡ŏ_ŏ)",
        "(/≧▽≦)/", "(눈‸눈)", "(・∀・)", "(>_<)", "m(_ _)m", "(T▽T)",
        "( ˘•ω•˘ )", "(ﾟ∀ﾟ)", "(≧∇≦)/", "┐(´д`)┌", "(๑•̀ㅂ•́)و✧", "(〃∀〃)",
    )

    private const val SYM_COLS = 7
    private const val SYM_ROWS = 4

    private fun symbolItems(item: FanItem): Triple<List<String>, Int, Float> = when (item) {
        FanItem.HALF -> Triple(HALF, SYM_COLS, 21f)
        FanItem.FULL -> Triple(FULL, SYM_COLS, 21f)
        FanItem.NUM -> Triple(NUM, 6, 24f)
        FanItem.KAOMOJI -> Triple(KAOMOJI, 3, 15f)
        FanItem.CLIPBOARD -> Triple(emptyList(), 1, 16f)
    }

    /** 符號共有幾頁。 */
    fun symbolPages(item: FanItem): Int {
        val (items, perRow, _) = symbolItems(item)
        return maxOf(1, (items.size + perRow * SYM_ROWS - 1) / (perRow * SYM_ROWS))
    }

    /**
     * 符號鍵盤：左邊 4 排符號，最右一欄是 ▲ 上一頁／▼ 下一頁／頁碼／圖釘（倬：符號太多要能翻頁）；
     * 最下排是返回、空白、刪除、換行。
     * backLabel：返回鍵顯示「ㄅㄆㄇ」或「ABC」，依進入符號鍵盤前的鍵盤而定。
     */
    fun symbols(item: FanItem, pinned: Boolean, backLabel: String = "ㄅㄆㄇ", page: Int = 0): List<List<KeyDef>> {
        val (items, perRow, size) = symbolItems(item)
        val pages = symbolPages(item)
        val p = page.coerceIn(0, pages - 1)
        val slice = items.drop(p * perRow * SYM_ROWS).take(perRow * SYM_ROWS)
        val side = listOf(
            KeyDef("▲", KeyAction.PageUp, style = KeyStyle.DARK, textSizeSp = 18f),
            KeyDef("▼", KeyAction.PageDown, style = KeyStyle.DARK, textSizeSp = 18f),
            KeyDef("${p + 1}/$pages", KeyAction.Nop, style = KeyStyle.DARK, textSizeSp = 14f),
            KeyDef(if (pinned) "🔒" else "📌", KeyAction.Pin, style = KeyStyle.DARK, textSizeSp = 17f),
        )
        val rows = (0 until SYM_ROWS).map { r ->
            val cells = (0 until perRow).map { c ->
                val t = slice.getOrNull(r * perRow + c)
                if (t == null) KeyDef("", KeyAction.Nop, textSizeSp = size)
                else KeyDef(t, KeyAction.Commit(t), textSizeSp = size)
            }
            // 右欄比一般鍵寬一點；顏文字每排 3 個，右欄權重跟著調整
            cells + side[r].copy(weight = if (perRow <= 3) 0.6f else 1.1f)
        }.toMutableList()
        rows += listOf(
            KeyDef(backLabel, KeyAction.ToZhuyin, weight = 1.4f, style = KeyStyle.DARK, textSizeSp = 15f),
            KeyDef("", KeyAction.Space, weight = 3.6f),
            KeyDef("", KeyAction.Backspace, weight = 1.2f, style = KeyStyle.DARK),
            KeyDef("", KeyAction.Enter, weight = 1.2f, style = KeyStyle.DARK),
        )
        return rows
    }
}
