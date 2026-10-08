package tw.hoonsor.ime.zhuyin

import java.io.BufferedReader

/** 詞庫中的一筆：詞、每字讀音、權重。 */
class LexEntry(
    val text: String,
    val syllables: List<Syllable>,
    val count: Int,
    val source: CandidateSource,
) {
    /** 索引鍵 = 每字讀音的第一個注音符號，例如 你好 → "ㄋㄏ"。 */
    val key: String = buildString { syllables.forEach { if (it.core.isNotEmpty()) append(it.core[0]) } }

    fun matches(fragments: List<Fragment>, from: Int = 0): Boolean {
        for (i in fragments.indices) {
            if (!fragments[i].matches(syllables[from + i])) return false
        }
        return true
    }
}

/**
 * 以「首符號鍵」為索引的詞庫。系統詞庫與使用者詞庫都用這個結構。
 */
open class Lexicon {
    protected val byKey = HashMap<String, MutableList<LexEntry>>()
    private var sortedKeys: Array<String>? = null
    private var sortedTexts: Array<LexEntry>? = null

    var size: Int = 0
        protected set

    open fun add(entry: LexEntry) {
        if (entry.syllables.isEmpty() || entry.text.length != entry.syllables.size) return
        if (entry.syllables.any { it.core.isEmpty() }) return
        byKey.getOrPut(entry.key) { ArrayList(2) }.add(entry)
        size++
        sortedKeys = null
        sortedTexts = null
    }

    fun remove(text: String, syllables: List<Syllable>): Boolean {
        val key = buildString { syllables.forEach { if (it.core.isNotEmpty()) append(it.core[0]) } }
        val list = byKey[key] ?: return false
        val removed = list.removeAll { it.text == text && it.syllables == syllables }
        if (removed) {
            size--
            if (list.isEmpty()) byKey.remove(key)
            sortedKeys = null
            sortedTexts = null
        }
        return removed
    }

    fun entries(): Sequence<LexEntry> = byKey.values.asSequence().flatten()

    fun exact(key: String): List<LexEntry> = byKey[key] ?: emptyList()

    /** 鍵以 prefix 開頭且比 prefix 長的所有詞（延長找詞用）。 */
    fun longerThan(prefix: String, limit: Int = 2000): List<LexEntry> {
        val keys = sortedKeys ?: byKey.keys.sorted().toTypedArray().also { sortedKeys = it }
        var i = lowerBound(keys, prefix)
        val out = ArrayList<LexEntry>()
        while (i < keys.size && keys[i].startsWith(prefix) && out.size < limit) {
            if (keys[i].length > prefix.length) out.addAll(byKey[keys[i]]!!)
            i++
        }
        return out
    }

    /** 以 text 開頭、比 text 長的詞（聯想詞用）。 */
    fun textStartingWith(text: String, limit: Int = 500): List<LexEntry> {
        val arr = sortedTexts ?: entries().filter { it.text.length >= 2 }
            .sortedBy { it.text }.toList().toTypedArray().also { sortedTexts = it }
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].text < text) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<LexEntry>()
        var i = lo
        while (i < arr.size && arr[i].text.startsWith(text) && out.size < limit) {
            if (arr[i].text.length > text.length) out.add(arr[i])
            i++
        }
        return out
    }

    private fun lowerBound(arr: Array<String>, target: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /**
         * 讀取 tools/build_dict.py 產生的 TSV：詞<TAB>讀音（空白分隔）<TAB>count。
         * 以 # 開頭的行為註解。
         */
        fun loadTsv(reader: BufferedReader): Lexicon {
            val lex = Lexicon()
            val syllableCache = HashMap<String, Syllable>(2048)
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == '#') continue
                    val t1 = line.indexOf('\t')
                    val t2 = line.indexOf('\t', t1 + 1)
                    if (t1 <= 0 || t2 <= t1) continue
                    val text = line.substring(0, t1)
                    val syls = line.substring(t1 + 1, t2).split(' ')
                        .map { s -> syllableCache.getOrPut(s) { Syllable.parse(s) } }
                    val count = line.substring(t2 + 1).trim().toIntOrNull() ?: 0
                    lex.add(LexEntry(text, syls, count, CandidateSource.SYSTEM))
                }
            }
            return lex
        }
    }
}
