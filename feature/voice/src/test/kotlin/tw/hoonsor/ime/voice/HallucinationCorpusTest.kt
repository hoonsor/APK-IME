package tw.hoonsor.ime.voice

import org.junit.Test
import tw.hoonsor.ime.voice.text.Hallucination
import java.io.File

/**
 * 語料測試：用 hallucination-corpus.tsv（38 號專案 corpus_factory.py 以 MiniMax 生成＋盲驗）測幻覺過濾。
 * 兩種錯誤分開算：誤刪（正常內容被丟掉，最嚴重）與漏網（片尾套話被送出）。
 * 只產生報告、不讓建置失敗。
 * 執行：./gradlew :feature:voice:test --tests "*HallucinationCorpusTest"
 * 報告：feature/voice/build/reports/hallucination-corpus.md
 */
class HallucinationCorpusTest {

    @Test fun corpus() {
        val stream = javaClass.classLoader!!.getResourceAsStream("hallucination-corpus.tsv")
        if (stream == null) { println("hallucination-corpus.tsv 不存在，略過"); return }
        val rows = stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
            .map { it.split('\t', limit = 3) }.filter { it.size == 3 }
        val wrongDrop = rows.filter { it[1] == "keep" && Hallucination.isHallucination(it[2]) }
        val leaked = rows.filter { it[1] == "drop" && !Hallucination.isHallucination(it[2]) }
        val keeps = rows.count { it[1] == "keep" }
        val drops = rows.count { it[1] == "drop" }
        val report = buildString {
            appendLine("# 幻覺過濾語料測試報告")
            appendLine()
            appendLine("- 正常內容 $keeps 段：誤刪 ${wrongDrop.size}（${if (keeps > 0) wrongDrop.size * 100 / keeps else 0}%）← 越低越好，誤刪會讓使用者講的話消失")
            appendLine("- 片尾套話 $drops 段：漏網 ${leaked.size}（${if (drops > 0) leaked.size * 100 / drops else 0}%）")
            appendLine()
            appendLine("## 誤刪（正常內容被當成幻覺）")
            wrongDrop.forEach { appendLine("- ${it[0]}：${it[2]}") }
            appendLine()
            appendLine("## 漏網（套話沒有被擋）")
            leaked.forEach { appendLine("- ${it[0]}：${it[2]}") }
        }
        val out = File("build/reports/hallucination-corpus.md")
        out.parentFile.mkdirs()
        out.writeText(report, Charsets.UTF_8)
        println("幻覺語料：誤刪 ${wrongDrop.size}/$keeps，漏網 ${leaked.size}/$drops，報告 ${out.absolutePath}")
    }
}
