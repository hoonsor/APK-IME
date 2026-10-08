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

    /** 標點手勢鍵（K2a）：點 = ，、↖？ ↗、 ↘！ ↙， */
    data object Punct : KeyAction

    /** 符號鍵盤圖釘（K4）。 */
    data object Pin : KeyAction
}

data class KeyDef(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val textSizeSp: Float = 22f,
)

/** 扇子項目（K3）。CLIPBOARD 為剪貼簿預留（R2，待倬決定入口）。 */
enum class FanItem(val glyph: String, val title: String) {
    HALF("半", "半形符號"),
    FULL("全", "全形符號"),
    NUM("數", "數字"),
    KAOMOJI("顏", "顏文字"),
    CLIPBOARD("剪", "剪貼簿"),
}

object KeyboardLayouts {

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
                KeyDef("", KeyAction.Punct),
                KeyDef("", KeyAction.Space, weight = 1.7f),
                KeyDef("。", KeyAction.Commit("。")),
                KeyDef("ㄦ", KeyAction.Zhuyin('ㄦ')),
                KeyDef("", KeyAction.Backspace, style = KeyStyle.DARK),
                KeyDef("", KeyAction.Enter, style = KeyStyle.DARK),
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
            KeyDef(",", KeyAction.Commit(",")),
            KeyDef("", KeyAction.Space, weight = 3.6f),
            KeyDef(".", KeyAction.Commit(".")),
            KeyDef("", KeyAction.Enter, weight = 1.5f, style = KeyStyle.DARK),
        )
        return rows
    }

    private val HALF = (", . ; : ? ! ' \" ( ) [ ] { } < > - _ + = * / \\ | @ # $ % ^ & ~ `").split(' ')
    private val FULL = "，。、；：？！…「」『』（）【】《》〈〉—～＋－＊／＝％＠＃＆＿｜＼‧·".map { it.toString() }
    private val NUM = "1 2 3 4 5 6 7 8 9 0 . , + - * / = % ( ) $ # : ①".split(' ')
    private val KAOMOJI = listOf(
        "(*´∀`)~♥", "(｡◕∀◕｡)", "(´・ω・`)", "(╯°□°)╯︵ ┻━┻", "ヽ(✿ﾟ▽ﾟ)ノ", "(｡ŏ_ŏ)",
        "(/≧▽≦)/", "(눈‸눈)", "(・∀・)", "(>_<)", "m(_ _)m", "(T▽T)",
        "( ˘•ω•˘ )", "(ﾟ∀ﾟ)", "(≧∇≦)/", "┐(´д`)┌", "(๑•̀ㅂ•́)و✧", "(〃∀〃)",
    )

    /** 符號鍵盤：內容 + 控制列（回注音、圖釘、空白、刪除、換行）。 */
    fun symbols(item: FanItem, pinned: Boolean): List<List<KeyDef>> {
        val (items, perRow, size) = when (item) {
            FanItem.HALF -> Triple(HALF, 8, 22f)
            FanItem.FULL -> Triple(FULL, 8, 22f)
            FanItem.NUM -> Triple(NUM, 6, 24f)
            FanItem.KAOMOJI -> Triple(KAOMOJI, 3, 15f)
            FanItem.CLIPBOARD -> Triple(emptyList(), 1, 16f)
        }
        val rows = items.chunked(perRow).take(4).map { row ->
            row.map { KeyDef(it, KeyAction.Commit(it), textSizeSp = size) }
        }.toMutableList()
        rows += listOf(
            KeyDef("ㄅㄆㄇ", KeyAction.ToZhuyin, weight = 1.4f, style = KeyStyle.DARK, textSizeSp = 15f),
            KeyDef(if (pinned) "📌 鎖定" else "📌", KeyAction.Pin, weight = 1.2f, style = KeyStyle.DARK, textSizeSp = 15f),
            KeyDef("", KeyAction.Space, weight = 2.4f),
            KeyDef("", KeyAction.Backspace, weight = 1.2f, style = KeyStyle.DARK),
            KeyDef("", KeyAction.Enter, weight = 1.2f, style = KeyStyle.DARK),
        )
        return rows
    }
}
