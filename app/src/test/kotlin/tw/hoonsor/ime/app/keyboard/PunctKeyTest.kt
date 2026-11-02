package tw.hoonsor.ime.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctKeyTest {

    private val slop = 12f

    @Test
    fun `點一下送左下，四個方向送對應的角`() {
        val k = PunctSlot.ENGLISH_RIGHT.default
        assertEquals(".com", k.pick(0f, 0f, slop))
        assertEquals(".com", k.pick(5f, -5f, slop))
        assertEquals(".", k.pick(-30f, -30f, slop))
        assertEquals("~", k.pick(30f, -30f, slop))
        assertEquals(";", k.pick(30f, 30f, slop))
        assertEquals(".com", k.pick(-30f, 30f, slop))
    }

    @Test
    fun `空白鍵點一下仍是空白`() {
        assertTrue(PunctSlot.ZHUYIN_SPACE.default.isSpace)
        assertTrue(PunctSlot.ENGLISH_SPACE.default.isSpace)
        assertEquals(" ", PunctSlot.ZHUYIN_SPACE.default.pick(0f, 0f, slop))
    }

    @Test
    fun `四符號鍵的預設配置`() {
        assertEquals(KeyAction.Punct("，", "？", "、", "！"), PunctSlot.ZHUYIN_LEFT.default)
        assertEquals(KeyAction.Punct("。", "&", "●", "（"), PunctSlot.ZHUYIN_RIGHT.default)
        assertEquals(KeyAction.Punct("@", ",", "-", "_"), PunctSlot.ENGLISH_LEFT.default)
        assertEquals(KeyAction.Punct(".com", ".", "~", ";"), PunctSlot.ENGLISH_RIGHT.default)
    }

    @Test
    fun `注音與英文底列都放了三個四符號鍵`() {
        val zhuyin = KeyboardLayouts.zhuyin().last().map { it.action }
        listOf(PunctSlot.ZHUYIN_LEFT, PunctSlot.ZHUYIN_SPACE, PunctSlot.ZHUYIN_RIGHT)
            .forEach { assertTrue(it.default in zhuyin) }
        val english = KeyboardLayouts.english(upper = false, numberRow = false).last().map { it.action }
        listOf(PunctSlot.ENGLISH_LEFT, PunctSlot.ENGLISH_SPACE, PunctSlot.ENGLISH_RIGHT)
            .forEach { assertTrue(it.default in english) }
    }

    @Test
    fun `版面使用自訂內容`() {
        val custom = KeyAction.Punct("a", "b", "c", "d")
        val row = KeyboardLayouts.zhuyin { if (it == PunctSlot.ZHUYIN_RIGHT) custom else it.default }.last()
        assertTrue(row.any { it.action == custom })
    }

    @Test
    fun `自訂內容存取來回一致`() {
        val p = KeyAction.Punct(".com.tw", "（", "😀", "abc")
        assertEquals(p, PunctSlot.ENGLISH_RIGHT.decode(PunctSlot.ENGLISH_RIGHT.encode(p)))
    }

    @Test
    fun `沒存過、壞資料、空白的角都用預設值`() {
        val s = PunctSlot.ZHUYIN_RIGHT
        assertEquals(s.default, s.decode(null))
        assertEquals(s.default, s.decode(""))
        assertEquals(s.default, s.decode("\t\t\t\t\t"))
        assertEquals(KeyAction.Punct("x", "&", "●", "（"), s.decode("x"))
        assertEquals(KeyAction.Punct("。", "&", "y", "（"), s.decode(" \t\ty\t"))
    }

    @Test
    fun `空白鍵的點一下不能被改掉`() {
        val s = PunctSlot.ZHUYIN_SPACE
        assertEquals(" ", s.decode("x\ta\tb\tc").downLeft)
        assertEquals("a", s.decode("x\ta\tb\tc").upLeft)
    }

    @Test
    fun `清理換行Tab與超長內容，不切壞emoji`() {
        assertEquals("ab", PunctSlot.clean(" a\tb\n"))
        assertEquals(PunctSlot.MAX_CHARS, PunctSlot.clean("x".repeat(30)).length)
        val cut = PunctSlot.clean("x".repeat(PunctSlot.MAX_CHARS - 1) + "😀")
        assertEquals(PunctSlot.MAX_CHARS - 1, cut.length)
    }
}
