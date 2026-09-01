package com.kazumaproject.core.domain.braille

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrailleInputProcessorTest {

    private lateinit var processor: BrailleInputProcessor

    @Before
    fun setUp() {
        processor = BrailleInputProcessor()
    }

    @Test
    fun testInputHiraganaA() {
        // 「あ」: ① (1打目: キー1, 2打目: ハンスペース)
        val result1 = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(result1 is BrailleInputResult.StrokeAdvance)
        assertEquals(BrailleStrokeStep.SECOND_STROKE, processor.currentStep)

        val result2 = processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        assertTrue(result2 is BrailleInputResult.Character)
        assertEquals("あ", (result2 as BrailleInputResult.Character).text)
        assertEquals(BrailleStrokeStep.FIRST_STROKE, processor.currentStep)
    }

    @Test
    fun testInputHiraganaI() {
        // 「い」: ①② (1打目: キー1 + キー2, 2打目: ハンスペース)
        val result1 = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1, 2))
        assertTrue(result1 is BrailleInputResult.StrokeAdvance)

        val result2 = processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        assertTrue(result2 is BrailleInputResult.Character)
        assertEquals("い", (result2 as BrailleInputResult.Character).text)
    }

    @Test
    fun testInputHiraganaU() {
        // 「う」: ①④ (1打目: キー1, 2打目: キー1)
        val result1 = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(result1 is BrailleInputResult.StrokeAdvance)

        val result2 = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(result2 is BrailleInputResult.Character)
        assertEquals("う", (result2 as BrailleInputResult.Character).text)
    }

    @Test
    fun testInputHiraganaKa() {
        // 「か」: ①⑥ (1打目: キー1, 2打目: キー3)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(3))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("か", (result as BrailleInputResult.Character).text)
    }

    @Test
    fun testInputDakutenGa() {
        // 濁点符: ⑤ (1打目: ハンスペース, 2打目: キー2)
        val prefix1 = processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        assertTrue(prefix1 is BrailleInputResult.StrokeAdvance)
        val prefix2 = processor.processStroke(isHalfSpace = false, keyIndices = setOf(2))
        assertTrue(prefix2 is BrailleInputResult.PrefixSet)
        assertEquals(BraillePrefixState.DAKUTEN, (prefix2 as BrailleInputResult.PrefixSet).prefix)

        // 次に「か」(①⑥)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(3))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("が", (result as BrailleInputResult.Character).text)
        assertEquals(BraillePrefixState.NONE, processor.currentPrefix)
    }

    @Test
    fun testInputHandakutenPa() {
        // 半濁点符: ⑥ (1打目: ハンスペース, 2打目: キー3)
        processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        val prefix = processor.processStroke(isHalfSpace = false, keyIndices = setOf(3))
        assertTrue(prefix is BrailleInputResult.PrefixSet)
        assertEquals(BraillePrefixState.HANDAKUTEN, (prefix as BrailleInputResult.PrefixSet).prefix)

        // 「は」(①③⑥: 1打目=1,3 / 2打目=3)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1, 3))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(3))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("ぱ", (result as BrailleInputResult.Character).text)
    }

    @Test
    fun testInputYouonKya() {
        // 拗音符: ④ (1打目: ハンスペース, 2打目: キー1)
        processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        val prefix = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(prefix is BrailleInputResult.PrefixSet)
        assertEquals(BraillePrefixState.YOUON, (prefix as BrailleInputResult.PrefixSet).prefix)

        // 「き」(①②⑥: 1打目=1,2 / 2打目=3)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1, 2))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(3))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("きゃ", (result as BrailleInputResult.Character).text)
    }

    @Test
    fun testDoubleHalfSpaceOutputsSpace() {
        // 1打目: ハンスペース, 2打目: ハンスペース -> スペース
        processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        val result = processor.processStroke(isHalfSpace = true, keyIndices = emptySet())
        assertTrue(result is BrailleInputResult.Space)
    }

    @Test
    fun testResetStroke() {
        // 1打目を入力
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertEquals(BrailleStrokeStep.SECOND_STROKE, processor.currentStep)

        // リセット実行
        val resetResult = processor.resetStroke()
        assertTrue(resetResult)
        assertEquals(BrailleStrokeStep.FIRST_STROKE, processor.currentStep)
        assertEquals(BrailleDot.DOT_NONE, processor.leftDots)
    }

    @Test
    fun testEnglishMode() {
        processor.switchMode() // JAPANESE -> ENGLISH
        assertEquals(BrailleInputMode.ENGLISH, processor.inputMode)

        // 'c': ①④ (1打目: 1, 2打目: 1)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("c", (result as BrailleInputResult.Character).text)
    }

    @Test
    fun testNumberMode() {
        processor.switchMode() // ENGLISH
        processor.switchMode() // NUMBER
        assertEquals(BrailleInputMode.NUMBER, processor.inputMode)

        // '3': ①④ (1打目: 1, 2打目: 1)
        processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        val result = processor.processStroke(isHalfSpace = false, keyIndices = setOf(1))
        assertTrue(result is BrailleInputResult.Character)
        assertEquals("3", (result as BrailleInputResult.Character).text)
    }
}