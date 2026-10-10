package tw.hoonsor.ime.app

import android.content.Context
import android.util.Log
import tw.hoonsor.ime.zhuyin.Lexicon
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 系統詞庫只載入一次，輸入法與設定頁（反查注音）共用。
 * [get] 會阻塞，請在背景執行緒呼叫。
 */
object LexiconHolder {
    @Volatile
    private var lexicon: Lexicon? = null

    fun peek(): Lexicon? = lexicon

    @Synchronized
    fun get(context: Context): Lexicon {
        lexicon?.let { return it }
        val t0 = System.currentTimeMillis()
        val lex = BufferedReader(InputStreamReader(context.applicationContext.assets.open("zhuyin_dict.tsv"), Charsets.UTF_8))
            .use { Lexicon.loadTsv(it) }
        Log.i("LexiconHolder", "lexicon loaded: ${lex.size} entries in ${System.currentTimeMillis() - t0} ms")
        lexicon = lex
        return lex
    }
}
