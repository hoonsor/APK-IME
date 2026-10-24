package tw.hoonsor.ime.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctKeyTest {

    private val slop = 12f

    @Test
    fun `點一下送左下，四個方向送對應的角`() {
        val k = KeyboardLayouts.ENGLISH_PUNCT_RIGHT
        assertEquals(".com", k.pick(0f, 0f, slop))
        assertEquals(".com", k.pick(5f, -5f, slop))
        assertEquals(".", k.pick(-30f, -30f, slop))
        assertEquals("~", k.pick(30f, -30f, slop))
        assertEquals(";", k.pick(30f, 30f, slop))
        assertEquals(".com", k.pick(-30f, 30f, slop))
    }

    @Test
    fun `四符號鍵的配置`() {
        assertEquals(KeyAction.Punct("，", "？", "、", "！"), KeyboardLayouts.ZHUYIN_PUNCT_LEFT)
        assertEquals(KeyAction.Punct("。", "&", "●", "（"), KeyboardLayouts.ZHUYIN_PUNCT_RIGHT)
        assertEquals(KeyAction.Punct("@", ",", "-", "_"), KeyboardLayouts.ENGLISH_PUNCT_LEFT)
        assertEquals(KeyAction.Punct(".com", ".", "~", ";"), KeyboardLayouts.ENGLISH_PUNCT_RIGHT)
    }

    @Test
    fun `注音與英文底列都放了兩個四符號鍵`() {
        val zhuyinBottom = KeyboardLayouts.ZHUYIN.last().map { it.action }
        assertTrue(KeyboardLayouts.ZHUYIN_PUNCT_LEFT in zhuyinBottom)
        assertTrue(KeyboardLayouts.ZHUYIN_PUNCT_RIGHT in zhuyinBottom)
        val englishBottom = KeyboardLayouts.english(upper = false, numberRow = false).last().map { it.action }
        assertTrue(KeyboardLayouts.ENGLISH_PUNCT_LEFT in englishBottom)
        assertTrue(KeyboardLayouts.ENGLISH_PUNCT_RIGHT in englishBottom)
    }
}
