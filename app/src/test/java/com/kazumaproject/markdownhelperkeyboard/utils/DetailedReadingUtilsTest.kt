package com.kazumaproject.markdownhelperkeyboard.utils

import com.kazumaproject.markdownhelperkeyboard.repository.TamachiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testJapaneseCharacterUtils_isKanji() {
        assertTrue(JapaneseCharacterUtils.isKanji('漢'))
        assertTrue(JapaneseCharacterUtils.isKanji('字'))
        assertTrue(JapaneseCharacterUtils.isKanji('東'))
        assertTrue(JapaneseCharacterUtils.isKanji('京'))
        assertTrue(JapaneseCharacterUtils.isKanji("𠮷")) // サロゲートペア漢字
        assertTrue(JapaneseCharacterUtils.isKanji('々'))

        assertFalse(JapaneseCharacterUtils.isKanji('あ'))
        assertFalse(JapaneseCharacterUtils.isKanji('ア'))
        assertFalse(JapaneseCharacterUtils.isKanji('A'))
        assertFalse(JapaneseCharacterUtils.isKanji('1'))
        assertFalse(JapaneseCharacterUtils.isKanji('、'))
        assertFalse(JapaneseCharacterUtils.isKanji(' '))
        assertFalse(JapaneseCharacterUtils.isKanji('\n'))
    }

    @Test
    fun testGetCursorDetailedReading_nonDTalker_kanjiDetailedReading() {
        val mockRepo = mock(TamachiRepository::class.java)
        `when`(mockRepo.getDetailedReading("東")).thenReturn("東のとう")

        // ドキュメントトーカ以外: 漢字は詳細読みされる
        val result = DetailedReadingUtils.getCursorDetailedReading("東", isDTalkerTTS = false, tamachiRepository = mockRepo)
        assertEquals("東のとう", result)
    }

    @Test
    fun testGetCursorDetailedReading_nonDTalker_nonKanjiReturnsCharOnly() {
        val mockRepo = mock(TamachiRepository::class.java)
        `when`(mockRepo.getDetailedReading("あ")).thenReturn("ひらがな あ")
        `when`(mockRepo.getDetailedReading("A")).thenReturn("半角大文字 A")

        // ドキュメントトーカ以外: ひらがな・カタカナ・アルファベット・数字は詳細読みされず文字のみ
        assertEquals("あ", DetailedReadingUtils.getCursorDetailedReading("あ", isDTalkerTTS = false, tamachiRepository = mockRepo))
        assertEquals("さ", DetailedReadingUtils.getCursorDetailedReading("さ", isDTalkerTTS = false, tamachiRepository = mockRepo))
        assertEquals("ア", DetailedReadingUtils.getCursorDetailedReading("ア", isDTalkerTTS = false, tamachiRepository = mockRepo))
        assertEquals("A", DetailedReadingUtils.getCursorDetailedReading("A", isDTalkerTTS = false, tamachiRepository = mockRepo))
        assertEquals("1", DetailedReadingUtils.getCursorDetailedReading("1", isDTalkerTTS = false, tamachiRepository = mockRepo))
    }

    @Test
    fun testGetCursorDetailedReading_nonDTalker_whitespaceAndSymbols() {
        // ドキュメントトーカ以外でも空白や記号はアクセシビリティ名
        assertEquals("改行", DetailedReadingUtils.getCursorDetailedReading("\n", isDTalkerTTS = false, tamachiRepository = null))
        assertEquals("スペース", DetailedReadingUtils.getCursorDetailedReading(" ", isDTalkerTTS = false, tamachiRepository = null))
        assertEquals("読点", DetailedReadingUtils.getCursorDetailedReading("、", isDTalkerTTS = false, tamachiRepository = null))
        assertEquals("長音", DetailedReadingUtils.getCursorDetailedReading("ー", isDTalkerTTS = false, tamachiRepository = null))
    }

    @Test
    fun testGetCursorDetailedReading_dtalker_consecutiveHiragana() {
        // 1文字目: 先頭なので詳細読み（SSML）
        val firstChar = DetailedReadingUtils.getCursorDetailedReading("あ", textBefore = null, isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(firstChar.contains("ひらがなのあ"))

        // 2文字目: 直前がひらがななので文字のみ
        val secondChar = DetailedReadingUtils.getCursorDetailedReading("さ", textBefore = "あ", isDTalkerTTS = true, tamachiRepository = null)
        assertEquals("さ", secondChar)
    }

    @Test
    fun testGetCursorDetailedReading_dtalker_consecutiveKatakanaWithLongVowel() {
        // 1文字目: カタカナ
        val first = DetailedReadingUtils.getCursorDetailedReading("キ", textBefore = null, isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(first.contains("カタカナのキ"))

        // 2文字目（長音）: 記号なので「長音」
        val second = DetailedReadingUtils.getCursorDetailedReading("ー", textBefore = "キ", isDTalkerTTS = true, tamachiRepository = null)
        assertEquals("長音", second)

        // 3文字目（長音の後、前がカタカナグループ）: 連続なので文字のみ
        val third = DetailedReadingUtils.getCursorDetailedReading("ボ", textBefore = "キー", isDTalkerTTS = true, tamachiRepository = null)
        assertEquals("ボ", third)
    }

    @Test
    fun testGetCursorDetailedReading_dtalker_consecutiveAlphabet() {
        // 'H' (大文字先頭): SSML
        val h = DetailedReadingUtils.getCursorDetailedReading("H", textBefore = null, isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(h.contains("H"))

        // 'e' (小文字切り替わり): SSML
        val e = DetailedReadingUtils.getCursorDetailedReading("e", textBefore = "H", isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(e.contains("e"))

        // 'l' (小文字連続): 直前が小文字なので文字のみ
        val l = DetailedReadingUtils.getCursorDetailedReading("l", textBefore = "He", isDTalkerTTS = true, tamachiRepository = null)
        assertEquals("l", l)
    }

    @Test
    fun testGetCursorDetailedReading_dtalker_consecutiveNumber() {
        // 1文字目: SSML
        val n1 = DetailedReadingUtils.getCursorDetailedReading("1", textBefore = null, isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(n1.contains("1"))

        // 2文字目: 直前が数字なので文字のみ
        val n2 = DetailedReadingUtils.getCursorDetailedReading("2", textBefore = "1", isDTalkerTTS = true, tamachiRepository = null)
        assertEquals("2", n2)
    }

    @Test
    fun testGetCursorDetailedReading_dtalker_isolatedSingleChar() {
        // 漢字の後の単独の「を」 -> 先頭なのでSSML詳細読み
        val wo = DetailedReadingUtils.getCursorDetailedReading("を", textBefore = "本", isDTalkerTTS = true, tamachiRepository = null)
        assertTrue(wo.contains("ひらがなのを"))
    }
}
