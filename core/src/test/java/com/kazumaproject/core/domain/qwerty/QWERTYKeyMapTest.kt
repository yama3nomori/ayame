package com.kazumaproject.core.domain.qwerty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QWERTYKeyMapTest {

    @Test
    fun testJapaneseQwertyKeyMapping() {
        val keyMap = QWERTYKeyMap()

        // Verify default Japanese layout mapping (listDefaultJP)
        val infoLJP = keyMap.getKeyInfoDefaultJP(QWERTYKey.QWERTYKeyL)
        assertTrue("QWERTYKeyL in listDefaultJP should be a QWERTYVariation", infoLJP is QWERTYKeyInfo.QWERTYVariation)
        assertEquals('l', (infoLJP as QWERTYKeyInfo.QWERTYVariation).tap)

        val infoMinusJP = keyMap.getKeyInfoDefaultJP(QWERTYKey.QWERTYKeyAtMark)
        assertTrue("QWERTYKeyAtMark in listDefaultJP should be a QWERTYVariation", infoMinusJP is QWERTYKeyInfo.QWERTYVariation)
        assertEquals('ー', (infoMinusJP as QWERTYKeyInfo.QWERTYVariation).tap)

        // Verify default Japanese layout with number row mapping (listDefaultJPWithNumberRow)
        val infoLJPWithNum = keyMap.getKeyInfoDefaultJPWithNumberRow(QWERTYKey.QWERTYKeyL)
        assertTrue("QWERTYKeyL in listDefaultJPWithNumberRow should be a QWERTYVariation", infoLJPWithNum is QWERTYKeyInfo.QWERTYVariation)
        assertEquals('l', (infoLJPWithNum as QWERTYKeyInfo.QWERTYVariation).tap)

        val infoMinusJPWithNum = keyMap.getKeyInfoDefaultJPWithNumberRow(QWERTYKey.QWERTYKeyAtMark)
        assertTrue("QWERTYKeyAtMark in listDefaultJPWithNumberRow should be a QWERTYVariation", infoMinusJPWithNum is QWERTYKeyInfo.QWERTYVariation)
        assertEquals('ー', (infoMinusJPWithNum as QWERTYKeyInfo.QWERTYVariation).tap)
    }

    @Test
    fun testEnglishQwertyKeyMapping() {
        val keyMap = QWERTYKeyMap()

        // Verify default English layout mapping (listDefault)
        val infoL = keyMap.getKeyInfoDefault(QWERTYKey.QWERTYKeyL)
        assertTrue("QWERTYKeyL in listDefault should be a QWERTYVariation", infoL is QWERTYKeyInfo.QWERTYVariation)
        assertEquals('l', (infoL as QWERTYKeyInfo.QWERTYVariation).tap)
    }

    @Test
    fun testSymbolAndNumberMapping() {
        val keyMap = QWERTYKeyMap()

        val lists = listOf(
            Pair(keyMap.keysNumber, keyMap::getKeyInfoNumber),
            Pair(keyMap.keysSymbol, keyMap::getKeyInfoSymbol),
            Pair(keyMap.keysNumberJP, keyMap::getKeyInfoNumberJP),
            Pair(keyMap.keysSymbolJP, keyMap::getKeyInfoSymbolJP)
        )

        for ((keys, getKeyInfo) in lists) {
            // Verify QWERTYKeyV maps to KeyEqual ('=')
            assertTrue("QWERTYKeyV should be in key set", keys.contains(QWERTYKey.QWERTYKeyV))
            val infoV = getKeyInfo(QWERTYKey.QWERTYKeyV)
            assertTrue("QWERTYKeyV should map to a variation", infoV is QWERTYKeyInfo.QWERTYVariation)
            assertEquals('=', (infoV as QWERTYKeyInfo.QWERTYVariation).tap)

            // Verify QWERTYKeyB maps to KeyAsterisk ('*')
            assertTrue("QWERTYKeyB should be in key set", keys.contains(QWERTYKey.QWERTYKeyB))
            val infoB = getKeyInfo(QWERTYKey.QWERTYKeyB)
            assertTrue("QWERTYKeyB should map to a variation", infoB is QWERTYKeyInfo.QWERTYVariation)
            assertEquals('*', (infoB as QWERTYKeyInfo.QWERTYVariation).tap)
        }
    }
}
