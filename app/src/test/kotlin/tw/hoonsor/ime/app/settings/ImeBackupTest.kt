package tw.hoonsor.ime.app.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.backup.AutoBackup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 共用模組 android-auto-backup 的純邏輯（檔名、只刪自己的檔、只留 7 份） */
class AutoBackupTest {
    @Test fun fileName() {
        val c = Calendar.getInstance().apply { set(2026, 10, 10, 9, 5) }
        assertEquals("ZhuyinIME-20261110-0905.zip", AutoBackup.fileName("ZhuyinIME", "zip", c.time))
    }

    @Test fun onlyOwnFiles() {
        assertTrue(AutoBackup.isBackupName("ZhuyinIME-20261110-0905.zip", "ZhuyinIME", "zip"))
        assertTrue(AutoBackup.isBackupName("ZhuyinIME-20261110-0905 (1).zip", "ZhuyinIME", "zip"))
        assertFalse(AutoBackup.isBackupName("ZhuyinIME-LAB-20261110-0905.zip", "ZhuyinIME", "zip"))
        assertFalse(AutoBackup.isBackupName("ZhuyinIME-20261110-0905.json", "ZhuyinIME", "zip"))
        assertFalse(AutoBackup.isBackupName("倬注音字典_20261110_0905.csv", "ZhuyinIME", "zip"))
    }

    @Test fun keepNewest() {
        val names = (1..9).map { "ZhuyinIME-2026110$it-1200.zip" } + "notes.txt"
        val del = AutoBackup.toDelete(names, "ZhuyinIME", "zip", AutoBackup.KEEP)
        assertEquals(listOf("ZhuyinIME-20261102-1200.zip", "ZhuyinIME-20261101-1200.zip"), del)
        assertTrue(AutoBackup.toDelete(names.take(7), "ZhuyinIME", "zip", AutoBackup.KEEP).isEmpty())
    }

    @Test fun sourceName() {
        val s = ZhuyinBackupSource()
        assertEquals("ZhuyinIME", s.appName())
        assertEquals("zip", s.extension())
        assertEquals("Documents/HoonsorBackup/ZhuyinIME/", AutoBackup.folder(s))
    }
}

class ImeBackupTest {
    private val dict = "P\t你好\tㄋㄧˇ ㄏㄠˇ\tUSER\t3\nE\tabc@gmail.com\nQ\t謝謝您\\n敬上\nU\t你好\tㄋㄧˇ ㄏㄠˇ\t5\n"

    private val ime = mapOf<String, Any>(
        "panelHeightDp" to 230, "haptics" to true, "punct_ZHUYIN_SPACE" to " \t：\t；\t「",
        "clipMax" to 100, "lastSeen" to 12L, "scale" to 1.25f, "tags" to setOf("b", "a"),
    )
    private val ai = mapOf<String, Any>(
        "STT.provider" to "GROQ", "STT.model" to "whisper-large-v3", "base.GROQ" to "",
        "polishEnabled" to true, "language" to "zh",
    )

    private fun zipBytes(prefs: Map<String, Map<String, *>> = mapOf("ime" to ime, "ai_config" to ai)): ByteArray =
        ByteArrayOutputStream().also { ImeBackup.writeZip(it, dict, prefs, "0.9.0") }.toByteArray()

    private fun entries(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; names += e.name }
        }
        return names
    }

    @Test fun prefsRoundTrip() {
        assertEquals(ime, ImeBackup.decodePrefs("ime", ImeBackup.encodePrefs("ime", ime)))
        assertEquals(ai, ImeBackup.decodePrefs("ai_config", ImeBackup.encodePrefs("ai_config", ai)))
    }

    @Test fun zipRoundTrip() {
        val b = ImeBackup.readZip(ByteArrayInputStream(zipBytes()))
        assertEquals(dict, b.dict)
        assertEquals(4, b.dictLines)
        assertEquals(ime, b.prefs["ime"])
        assertEquals(ai, b.prefs["ai_config"])
        assertEquals("0.9.0", b.appVersion)
    }

    @Test fun zipContentsWhitelist() {
        // 就算呼叫端誤傳 secrets、app_update，zip 裡也只會有白名單的項目
        val bytes = zipBytes(mapOf(
            "ime" to ime, "ai_config" to ai,
            "secrets" to mapOf("GROQ" to "encrypted-value"),
            "app_update" to mapOf("skipped" to "v0.8.0"),
        ))
        val names = entries(bytes)
        assertEquals(listOf("manifest.json", "user_dict.tsv", "prefs/ai_config.json", "prefs/ime.json"), names)
        assertTrue(names.none { "secret" in it || "clip" in it || "app_update" in it })
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("encrypted-value"))
    }

    @Test fun sensitiveKeysExcluded() {
        val withKeys = ai + mapOf("GROQ.apiKey" to "gsk_xxx", "api_key" to "k", "authToken" to "t", "password" to "p")
        val encoded = ImeBackup.encodePrefs("ai_config", withKeys)
        assertEquals(ai, ImeBackup.decodePrefs("ai_config", encoded))
        assertFalse(encoded.toString().contains("gsk_xxx"))
        // 現有的鍵都不能被誤判成金鑰
        (ime.keys + ai.keys + listOf("autoStart", "holdToTalk", "translateTo", "STT_BACKUP.model", "punct_ENGLISH_RIGHT"))
            .forEach { assertFalse(it, ImeBackup.isSensitiveKey(it)) }
    }

    @Test fun sameContentSameBytes() {
        // AutoBackup 用 SHA-256 判斷「內容沒變不另存」，zip 位元組必須穩定（不含時間戳）
        assertArrayEquals(zipBytes(), zipBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherZip() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write("{\"format\":\"voicehub-settings\"}".toByteArray()); z.closeEntry()
        }
        ImeBackup.readZip(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZipWithoutManifest() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("user_dict.tsv")); z.write(dict.toByteArray()); z.closeEntry() }
        ImeBackup.readZip(ByteArrayInputStream(out.toByteArray()))
    }

    @Test fun ignoresUnknownEntries() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            ZipInputStream(ByteArrayInputStream(zipBytes())).use { src ->
                while (true) {
                    val e = src.nextEntry ?: break
                    z.putNextEntry(ZipEntry(e.name)); z.write(src.readBytes()); z.closeEntry()
                }
            }
            z.putNextEntry(ZipEntry("clip_history.tsv")); z.write("密碼123".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("prefs/secrets.json")); z.write("{}".toByteArray()); z.closeEntry()
        }
        val b = ImeBackup.readZip(ByteArrayInputStream(out.toByteArray()))
        assertEquals(setOf("ime", "ai_config"), b.prefs.keys)
        assertEquals(dict, b.dict)
    }

    @Test fun emptyDictStillValid() {
        val bytes = ByteArrayOutputStream().also { ImeBackup.writeZip(it, "", mapOf("ime" to ime), "") }.toByteArray()
        val b = ImeBackup.readZip(ByteArrayInputStream(bytes))
        assertEquals("", b.dict)
        assertEquals(0, b.dictLines)
        assertNull(b.prefs["ai_config"])
    }
}
