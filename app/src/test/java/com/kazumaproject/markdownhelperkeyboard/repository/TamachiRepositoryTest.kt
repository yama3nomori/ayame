package com.kazumaproject.markdownhelperkeyboard.repository

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.ByteArrayInputStream

class TamachiRepositoryTest {

    @Test
    fun testGetDetailedReading_katakanaWithLongVowel() = runTest {
        val repository = TamachiRepository()

        // Mock context and asset manager
        val context = mock(Context::class.java)
        val assetManager = mock(AssetManager::class.java)
        `when`(context.assets).thenReturn(assetManager)

        // Mock empty Tamachi.csv content for simple character type resolution
        val csvData = ""
        `when`(assetManager.open("Tamachi.csv")).thenReturn(ByteArrayInputStream(csvData.toByteArray()))

        repository.load(context)

        // "キーボード"
        // Before fix: "カタカナ キ ー カタカナ ボ ー カタカナ ド"
        // After fix: "カタカナ キーボード"
        val readingKatakana = repository.getDetailedReading("キーボード")
        assertEquals("カタカナ キーボード", readingKatakana)

        // "らーめん"
        // After fix: "ひらがな らーめん"
        val readingHiragana = repository.getDetailedReading("らーめん")
        assertEquals("ひらがな らーめん", readingHiragana)
        
        // Single character "ー" should fall back to default behavior (returning null, so caller uses original string)
        val readingSingleLongVowel = repository.getDetailedReading("ー")
        assertEquals(null, readingSingleLongVowel)

        // 半角大文字
        val readingHalfUpper = repository.getDetailedReading("ABC")
        assertEquals("半角大文字 ABC", readingHalfUpper)

        // 半角小文字
        val readingHalfLower = repository.getDetailedReading("abc")
        assertEquals("半角小文字 abc", readingHalfLower)

        // 半角混合 (Hello)
        val readingHalfMixed = repository.getDetailedReading("Hello")
        assertEquals("半角大文字 H 半角小文字 ello", readingHalfMixed)

        // 全角大文字
        val readingFullUpper = repository.getDetailedReading("ＡＢＣ")
        assertEquals("全角大文字 ＡＢＣ", readingFullUpper)

        // 全角小文字
        val readingFullLower = repository.getDetailedReading("ａｂｃ")
        assertEquals("全角小文字 ａｂｃ", readingFullLower)
    }
}
