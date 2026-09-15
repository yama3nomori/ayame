package com.kazumaproject.tenkey

import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import com.kazumaproject.tenkey.extensions.getNextInputChar
import com.kazumaproject.tenkey.extensions.getNextReturnInputChar
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isKana
import com.kazumaproject.tenkey.extensions.isKatakana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharExtensionTest {

    @Test
    fun testIsKatakanaAndIsKana() {
        assertTrue('ア'.isKatakana())
        assertTrue('ホ'.isKatakana())
        assertTrue('ボ'.isKatakana())
        assertTrue('ッ'.isKatakana())
        assertTrue('ヴ'.isKatakana())
        assertFalse('ー'.isKatakana())
        assertFalse('あ'.isKatakana())
        assertFalse('a'.isKatakana())

        assertTrue('あ'.isHiragana())
        assertTrue('ほ'.isHiragana())
        assertFalse('ア'.isHiragana())

        assertTrue('あ'.isKana())
        assertTrue('ア'.isKana())
        assertTrue('ホ'.isKana())
        assertTrue('ボ'.isKana())
        assertFalse('a'.isKana())
        assertFalse('1'.isKana())
    }

    @Test
    fun testGetDakutenSmallCharKatakana() {
        // 'ホ' -> 'ボ' -> 'ポ' -> 'ホ'
        assertEquals('ボ', 'ホ'.getDakutenSmallChar())
        assertEquals('ポ', 'ボ'.getDakutenSmallChar())
        assertEquals('ホ', 'ポ'.getDakutenSmallChar())

        // 'ト' -> 'ド' -> 'ト'
        assertEquals('ド', 'ト'.getDakutenSmallChar())
        assertEquals('ト', 'ド'.getDakutenSmallChar())

        // 'ツ' -> 'ッ' -> 'ヅ' -> 'ツ'
        assertEquals('ッ', 'ツ'.getDakutenSmallChar())
        assertEquals('ヅ', 'ッ'.getDakutenSmallChar())
        assertEquals('ツ', 'ヅ'.getDakutenSmallChar())

        // 'ア' -> 'ァ' -> 'ア'
        assertEquals('ァ', 'ア'.getDakutenSmallChar())
        assertEquals('ア', 'ァ'.getDakutenSmallChar())

        // 'ウ' -> 'ゥ' -> 'ヴ' -> 'ウ'
        assertEquals('ゥ', 'ウ'.getDakutenSmallChar())
        assertEquals('ヴ', 'ゥ'.getDakutenSmallChar())
        assertEquals('ウ', 'ヴ'.getDakutenSmallChar())

        // 'カ' -> 'ガ' -> 'カ'
        assertEquals('ガ', 'カ'.getDakutenSmallChar())
        assertEquals('カ', 'ガ'.getDakutenSmallChar())
    }

    @Test
    fun testGetDakutenFlickKatakana() {
        // Flick Left (濁点)
        assertEquals('ボ', 'ホ'.getDakutenFlickLeft())
        assertEquals('ガ', 'カ'.getDakutenFlickLeft())
        assertEquals('ヴ', 'ウ'.getDakutenFlickLeft())
        assertEquals('ヅ', 'ツ'.getDakutenFlickLeft())

        // Flick Right (半濁点)
        assertEquals('ポ', 'ホ'.getDakutenFlickRight())
        assertEquals('パ', 'ハ'.getDakutenFlickRight())

        // Flick Top (小文字)
        assertEquals('ッ', 'ツ'.getDakutenFlickTop())
        assertEquals('ァ', 'ア'.getDakutenFlickTop())
        assertEquals('ャ', 'ヤ'.getDakutenFlickTop())
        assertEquals('ュ', 'ユ'.getDakutenFlickTop())
        assertEquals('ョ', 'ヨ'.getDakutenFlickTop())
    }

    @Test
    fun testGetNextInputCharKatakana() {
        assertEquals('ヒ', 'ハ'.getNextInputChar('ハ'))
        assertEquals('フ', 'ヒ'.getNextInputChar('ハ'))
        assertEquals('ヘ', 'フ'.getNextInputChar('ハ'))
        assertEquals('ホ', 'ヘ'.getNextInputChar('ハ'))
        assertEquals('ハ', 'ホ'.getNextInputChar('ハ'))

        // With hiragana base
        assertEquals('ヒ', 'ハ'.getNextInputChar('は'))
        assertEquals('ホ', 'ヘ'.getNextInputChar('は'))
    }

    @Test
    fun testGetNextReturnInputCharKatakana() {
        assertEquals('ヘ', 'ホ'.getNextReturnInputChar())
        assertEquals('フ', 'ヘ'.getNextReturnInputChar())
        assertEquals('ヒ', 'フ'.getNextReturnInputChar())
        assertEquals('ハ', 'ヒ'.getNextReturnInputChar())
        assertEquals('ホ', 'ハ'.getNextReturnInputChar())
    }

    @Test
    fun testHiraganaRegression() {
        assertEquals('ぼ', 'ほ'.getDakutenSmallChar())
        assertEquals('ぽ', 'ぼ'.getDakutenSmallChar())
        assertEquals('ほ', 'ぽ'.getDakutenSmallChar())

        assertEquals('っ', 'つ'.getDakutenSmallChar())
        assertEquals('づ', 'っ'.getDakutenSmallChar())
        assertEquals('つ', 'づ'.getDakutenSmallChar())

        assertEquals('ぼ', 'ほ'.getDakutenFlickLeft())
        assertEquals('ぽ', 'ほ'.getDakutenFlickRight())
        assertEquals('っ', 'つ'.getDakutenFlickTop())
    }
}
