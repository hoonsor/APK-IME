package tw.hoonsor.ime.app.settings

import tw.hoonsor.ime.zhuyin.Syllable
import tw.hoonsor.ime.zhuyin.Zhuyin
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 字典的「電腦可編輯」格式（倬：匯出到電腦用 Excel／Google 試算表編輯，再匯入手機）。
 *
 * CSV（UTF-8 含 BOM、CRLF），欄位：類型, 內容, 注音, 使用次數
 *   中文詞   注音自建詞；注音可留空，匯入時自動判斷
 *   聯絡人   從聯絡人匯入的詞（格式同中文詞）
 *   英文詞   英文／數字／符號詞
 *   常用短語 可多行
 *   學習紀錄 選字學習次數（備份用，不需要編輯；刪掉也沒關係）
 *
 * 純 Kotlin，不依賴 Android，方便測試。
 */
object DictionaryCsv {

    const val TYPE_PHRASE = "中文詞"
    const val TYPE_CONTACT = "聯絡人"
    const val TYPE_WORD = "英文詞"
    const val TYPE_QUICK = "常用短語"
    const val TYPE_USAGE = "學習紀錄"
    private val HEADER = listOf("類型", "內容", "注音", "使用次數")

    data class Phrase(val text: String, val readings: List<String>, val contact: Boolean)
    data class Usage(val text: String, val readings: List<String>, val count: Int)

    data class Data(
        val phrases: List<Phrase>,
        val words: List<String>,
        val quick: List<String>,
        val usage: List<Usage>,
    )

    /** 一筆中文詞的注音留空、需要反查。 */
    data class Pending(val line: Int, val text: String, val contact: Boolean)

    data class ParseResult(
        val data: Data,
        /** 注音留空、要自動判斷的中文詞。 */
        val pending: List<Pending>,
        /** 「第 N 行：原因」 */
        val errors: List<String>,
    )

    // =====================================================================
    // 匯出
    // =====================================================================

    fun export(d: Data): ByteArray {
        val sb = StringBuilder()
        fun row(vararg f: String) { f.joinTo(sb, ",") { field(it) }; sb.append("\r\n") }
        row(*HEADER.toTypedArray())
        for (p in d.phrases) row(if (p.contact) TYPE_CONTACT else TYPE_PHRASE, p.text, p.readings.joinToString(" "), "")
        for (w in d.words) row(TYPE_WORD, excelSafe(w), "", "")
        for (q in d.quick) row(TYPE_QUICK, excelSafe(q), "", "")
        for (u in d.usage) row(TYPE_USAGE, u.text, u.readings.joinToString(" "), u.count.toString())
        val body = sb.toString().toByteArray(Charsets.UTF_8)
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body
    }

    /** 需要時加上引號（逗號、引號、換行）。 */
    private fun field(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" "))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /**
     * Excel 會把 0912345678 變成 912345678、把很長的數字變成科學記號。
     * 這類內容寫成 ="0912345678"，Excel 會當文字顯示；匯入時再拆掉。
     */
    private val numberLike = Regex("^[+\\-]?[0-9][0-9\\-]*$")
    private fun excelSafe(s: String): String =
        if (numberLike.matches(s) && (s.startsWith("0") || s.startsWith("+") || s.length >= 12)) "=\"$s\"" else s

    private val excelText = Regex("^=\"(.*)\"$", RegexOption.DOT_MATCHES_ALL)
    private fun unwrapExcel(s: String): String = excelText.matchEntire(s)?.groupValues?.get(1) ?: s

    // =====================================================================
    // 匯入
    // =====================================================================

    /** 解碼：先試 UTF-8；失敗時當作 Big5（Excel「CSV（逗號分隔）」在繁中 Windows 會存成 Big5）。 */
    fun decode(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b = b.copyOfRange(3, b.size)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, runCatching { Charset.forName("Big5-HKSCS") }.getOrElse { Charset.forName("Big5") })
        }
    }

    /** 舊版「備份字典」產生的 TSV（P／E／Q／U 開頭）。 */
    fun isLegacyBackup(text: String): Boolean =
        text.lineSequence().take(5).any { it.length > 2 && it[1] == '\t' && it[0] in "PEQU" }

    fun parse(text: String): ParseResult {
        val rows = csvRows(text)
        val phrases = LinkedHashMap<String, Phrase>()
        val words = LinkedHashSet<String>()
        val quick = ArrayList<String>()
        val usage = LinkedHashMap<String, Usage>()
        val pending = ArrayList<Pending>()
        val errors = ArrayList<String>()

        for ((lineNo, f) in rows) {
            if (f.all { it.isBlank() }) continue
            val type = f.getOrElse(0) { "" }.trim()
            if (type == HEADER[0]) continue
            val content = unwrapExcel(f.getOrElse(1) { "" })
            val reading = f.getOrElse(2) { "" }.trim()
            when (type) {
                TYPE_PHRASE, TYPE_CONTACT -> {
                    val t = content.trim()
                    if (t.isEmpty()) { errors += "第 $lineNo 行：中文詞是空的"; continue }
                    if (reading.isEmpty()) { pending += Pending(lineNo, t, type == TYPE_CONTACT); continue }
                    val r = parseReadings(reading)
                    when {
                        r == null -> errors += "第 $lineNo 行：「$t」的注音格式不正確"
                        r.size != t.codePointCount(0, t.length) -> errors += "第 $lineNo 行：「$t」注音 ${r.size} 個音節，和字數不同"
                        else -> phrases[t + "|" + r.joinToString(" ")] = Phrase(t, r, type == TYPE_CONTACT)
                    }
                }
                TYPE_WORD -> {
                    val w = content.trim()
                    when {
                        w.isEmpty() -> errors += "第 $lineNo 行：英文詞是空的"
                        w.any { it.isWhitespace() } -> errors += "第 $lineNo 行：英文詞「$w」不能有空格"
                        else -> words += w
                    }
                }
                TYPE_QUICK -> {
                    val q = content.trimEnd().replace("\r\n", "\n")
                    if (q.isBlank()) errors += "第 $lineNo 行：常用短語是空的" else if (q !in quick) quick += q
                }
                TYPE_USAGE -> {
                    val r = parseReadings(reading)
                    val n = f.getOrElse(3) { "" }.trim().toIntOrNull()
                    if (content.isNotBlank() && r != null && n != null && n > 0) {
                        usage[content.trim() + "|" + r.joinToString(" ")] = Usage(content.trim(), r, n)
                    }
                }
                else -> errors += "第 $lineNo 行：看不懂的類型「$type」（應為 中文詞／英文詞／常用短語）"
            }
        }
        return ParseResult(Data(phrases.values.toList(), words.toList(), quick, usage.values.toList()), pending, errors)
    }

    /** 驗證並正規化注音：每個音節以注音符號開頭，聲調只能在最後；以空白分隔。 */
    fun parseReadings(s: String): List<String>? {
        val parts = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (p in parts) {
            if (!Zhuyin.isSymbol(p[0])) return null
            for ((i, c) in p.withIndex()) {
                if (Zhuyin.isSymbol(c)) continue
                if (Zhuyin.isTone(c) && i == p.length - 1) continue
                return null
            }
        }
        return parts.map { Syllable.parse(it).toString() }
    }

    /** RFC 4180 CSV（支援引號內換行、"" 跳脫）；回傳 (起始行號, 欄位)。 */
    private fun csvRows(text: String): List<Pair<Int, List<String>>> {
        val out = ArrayList<Pair<Int, List<String>>>()
        val row = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var line = 1
        var rowStart = 1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> { if (c == '\n') line++; cur.append(c) }
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row += cur.toString(); cur.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row += cur.toString(); cur.setLength(0)
                        out += rowStart to row.toList(); row.clear()
                        line++; rowStart = line
                    }
                    else -> cur.append(c)
                }
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row += cur.toString(); out += rowStart to row.toList() }
        return out
    }
}
