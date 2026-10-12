package com.kazumaproject.markdownhelperkeyboard.utils

import com.kazumaproject.markdownhelperkeyboard.repository.TamachiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DetailedReadingUtilsTest {

    @Test
    fun testSyosaiYomiSSML_pureHiragana() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("こんにちは")
        assertTrue(ssml.contains("ひらがなのこんにちは"))
        assertTrue(ssml.startsWith("<?xml version=\"1.0\"?><speak"))
        assertTrue(ssml.endsWith("</speak>"))
    }

    @Test
    fun testSyosaiYomiSSML_pureHiraganaWithLongVowel() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("らーめん")
        assertTrue(ssml.contains("ひらがなのらーめん"))
    }

    @Test
    fun testSyosaiYomiSSML_pureKatakana() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("テスト")
        assertTrue(ssml.contains("カタカナのテスト"))
    }

    @Test
    fun testSyosaiYomiSSML_pureKatakanaWithLongVowel() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("キーボード")
        assertTrue(ssml.contains("カタカナのキーボード"))
    }

    @Test
    fun testSyosaiYomiSSML_pureHalfWidthKatakana() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("ﾃｽﾄ")
        assertTrue(ssml.contains("半角カタカナのﾃｽﾄ"))
    }

    @Test
    fun testSyosaiYomiSSML_kanjiAndMixed() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("東京")
        assertTrue(ssml.contains("<say-as interpret-as=\"characters\" format=\"glyphs\">東京</say-as>"))
    }

    @Test
    fun testSyosaiYomiSSML_withPrefix() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("こんにちは", prefix = "1行目、")
        assertTrue(ssml.contains("1行目、ひらがなのこんにちは"))
    }

    @Test
    fun testSyosaiYomiSSML_kanjiWithPrefix() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("東京", prefix = "2行目、")
        assertTrue(ssml.contains("2行目、<say-as interpret-as=\"characters\" format=\"glyphs\">東京</say-as>"))
    }

    @Test
    fun testSyosaiYomiSSML_escaping() {
        val ssml = DetailedReadingUtils.getSyosaiYomiSSML("A&B<C>D")
        assertTrue(ssml.contains("A&amp;B&lt;C&gt;D"))
    }

    @Test
    fun testGetDetailedReading_dtalkerTrue() {
        val result = DetailedReadingUtils.getDetailedReading(
            text = "テスト",
            prefix = "",
            positionText = "",
            isDTalkerTTS = true,
            tamachiRepository = null
        )
        assertTrue(result.contains("カタカナのテスト"))
        assertTrue(result.startsWith("<?xml version=\"1.0\"?>"))
    }

    @Test
    fun testGetDetailedReading_dtalkerFalse_withTamachi() {
        val mockRepo = mock(TamachiRepository::class.java)
        `when`(mockRepo.getDetailedReading("東京")).thenReturn("東京のとう きょうのきょう")

        val result = DetailedReadingUtils.getDetailedReading(
            text = "東京",
            prefix = "1行目、",
            positionText = "",
            isDTalkerTTS = false,
            tamachiRepository = mockRepo
        )
        assertEquals("1行目、東京のとう きょうのきょう", result)
    }

    @Test
    fun testGetDetailedReading_emptyText() {
        val result = DetailedReadingUtils.getDetailedReading(
            text = "",
            prefix = "1行目、",
            positionText = "",
            isDTalkerTTS = true,
            tamachiRepository = null
        )
        assertEquals("1行目、", result)
    }

    @Test
    fun testGetCursorDetailedReading_whitespaceAndNewline() {
        assertEquals("改行", DetailedReadingUtils.getCursorDetailedReading("\n", false, null))
        assertEquals("スペース", DetailedReadingUtils.getCursorDetailedReading(" ", false, null))
        assertEquals("全角スペース", DetailedReadingUtils.getCursorDetailedReading("　", false, null))
        assertEquals("タブ", DetailedReadingUtils.getCursorDetailedReading("\t", false, null))
    }

    @Test
    fun testGetCursorDetailedReading_symbols() {
        assertEquals("読点", DetailedReadingUtils.getCursorDetailedReading("、", false, null))
        assertEquals("句点", DetailedReadingUtils.getCursorDetailedReading("。", false, null))
        assertEquals("長音", DetailedReadingUtils.getCursorDetailedReading("ー", false, null))
        assertEquals("アットマーク", DetailedReadingUtils.getCursorDetailedReading("@", false, null))
    }

    @Test
    fun testGetCursorDetailedReading_kanjiWithTamachi() {
        val mockRepo = mock(TamachiRepository::class.java)
        `when`(mockRepo.getDetailedReading("東")).thenReturn("東のとう")

        val result = DetailedReadingUtils.getCursorDetailedReading(
            text = "東",
            isDTalkerTTS = false,
            tamachiRepository = mockRepo
        )
        assertEquals("東のとう", result)
    }

    @Test
    fun testGetCursorDetailedReading_dtalkerSSML() {
        val result = DetailedReadingUtils.getCursorDetailedReading(
            text = "東",
            isDTalkerTTS = true,
            tamachiRepository = null
        )
        assertTrue(result.contains("<say-as interpret-as=\"characters\" format=\"glyphs\">東</say-as>"))

        val resultKana = DetailedReadingUtils.getCursorDetailedReading(
            text = "あ",
            isDTalkerTTS = true,
            tamachiRepository = null
        )
        assertTrue(resultKana.contains("ひらがなのあ"))
    }
}
